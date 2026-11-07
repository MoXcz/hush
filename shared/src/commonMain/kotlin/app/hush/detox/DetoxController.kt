// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.detox

import app.hush.blocker.BlockResult
import app.hush.blocker.Blocker
import app.hush.blocker.ProfileId
import app.hush.blocker.SessionStatus
import app.hush.blocklists.BlockListRepository
import app.hush.blocklists.ProfileRole
import app.hush.blocklists.profileFor
import app.hush.history.Session
import app.hush.history.SessionId
import app.hush.history.SessionOutcome
import app.hush.history.SessionRepository
import app.hush.settings.SettingsRepository
import app.hush.timer.WakeupScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What a detox command did. Anything but [Ok] and [Unconfirmed] means nothing changed. */
enum class DetoxCommandResult {
    Ok,

    /** No detox profile is chosen yet. */
    NoProfile,

    /** The blocker denied the call: approval or setup is missing. */
    NeedsSetup,

    /** The chosen level is neither passed nor the next one. */
    LevelLocked,

    /** The blocker refused to start (e.g. the group is gone, or another session is live). */
    Refused,

    /** The blocker refused to stop: the profile can't stop early. */
    CantStopEarly,

    /** The blocker couldn't be reached, and it's known (or possible) that nothing changed. */
    Unreachable,

    /** The attempt started, but the connection dropped and the blocker's state couldn't be read. */
    Unconfirmed,
}

data class DetoxAttempt(
    val id: SessionId,
    val levelMinutes: Int,
    val startedAt: Instant,
    val canStopEarly: Boolean,
) {
    val endsAt: Instant get() = startedAt + levelMinutes.minutes
}

data class DetoxState(
    val attempt: DetoxAttempt? = null,
    val remaining: Duration = Duration.ZERO,
    /** How the last attempt ended, until the UI acknowledges it. */
    val lastOutcome: LevelOutcome? = null,
)

/**
 * The one owner of detox attempts. An attempt is a blocker session of the level's length plus an
 * open `Session` row (wall-clock start and planned minutes), so it survives process death and
 * reboots. Pass = the end time is reached with no early stop recorded. Fail = ended through the
 * panic flow, or the blocker positively reports no session ([SessionStatus.None]) before the end.
 * [SessionStatus.Unknown] never fails a level.
 */
class DetoxController(
    private val blocker: Blocker,
    private val settingsRepository: SettingsRepository,
    private val blockLists: BlockListRepository,
    private val sessions: SessionRepository,
    private val wakeups: WakeupScheduler,
    private val alerts: LevelAlerts,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val restored = CompletableDeferred<Unit>()
    private val mutableState = MutableStateFlow(DetoxState())
    private var ticker: Job? = null
    private var restoreFailed = true

    val state: StateFlow<DetoxState> = mutableState.asStateFlow()

    init {
        scope.launch {
            // A failed restore must not hang waiters. An open attempt may then be invisible, so
            // starting stays refused (unconfirmed) rather than risking a second session.
            try {
                mutex.withLock { restore() }
                restoreFailed = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                restored.complete(Unit)
            }
        }
    }

    suspend fun awaitRestore() = restored.await()

    /**
     * Starts [level], or the next level when it's null. Only a passed level or the next one may be
     * attempted ([PathProgress.choosable]); anything else is [DetoxCommandResult.LevelLocked].
     * Never retries the blocker call.
     */
    suspend fun startAttempt(level: Duration? = null): DetoxCommandResult =
        command {
            if (restoreFailed) return@command DetoxCommandResult.Unconfirmed
            if (current() != null) return@command DetoxCommandResult.Ok
            val settings = settingsRepository.settings.first()
            val profile = blockLists.profileFor(ProfileRole.Detox) ?: return@command DetoxCommandResult.NoProfile
            val longest = sessions.longestPassedLevelMinutes.first()?.minutes
            val progress = DetoxPath.progress(settings.detoxGoal, longest)
            if (level != null && level !in progress.choosable) return@command DetoxCommandResult.LevelLocked
            val minutes = (level ?: progress.currentLevel).inWholeMinutes.toInt()
            when (blocker.start(profile, minutes)) {
                BlockResult.Ok -> {
                    open(profile, minutes)
                }

                BlockResult.Denied -> {
                    DetoxCommandResult.NeedsSetup
                }

                BlockResult.Refused -> {
                    DetoxCommandResult.Refused
                }

                BlockResult.ConnectionLost -> {
                    when (blocker.activeSession()) {
                        is SessionStatus.Active -> {
                            open(profile, minutes)
                        }

                        SessionStatus.None -> {
                            DetoxCommandResult.Unreachable
                        }

                        // Treat as started, so the user isn't invited to start again and stack sessions.
                        is SessionStatus.Unknown -> {
                            open(profile, minutes)
                            DetoxCommandResult.Unconfirmed
                        }
                    }
                }
            }
        }

    /** The panic flow's "End session": stops blocking and records the attempt as ended early. */
    suspend fun endEarly(): DetoxCommandResult =
        command {
            val attempt = current() ?: return@command DetoxCommandResult.Ok
            when (blocker.stop()) {
                BlockResult.Ok -> {
                    finish(attempt, SessionOutcome.Stopped, LevelOutcome.EndedEarly)
                    DetoxCommandResult.Ok
                }

                BlockResult.Denied -> {
                    DetoxCommandResult.NeedsSetup
                }

                BlockResult.Refused -> {
                    publish(attempt.copy(canStopEarly = false))
                    DetoxCommandResult.CantStopEarly
                }

                BlockResult.ConnectionLost -> {
                    when (blocker.activeSession()) {
                        SessionStatus.None -> {
                            finish(attempt, SessionOutcome.Stopped, LevelOutcome.EndedEarly)
                            DetoxCommandResult.Ok
                        }

                        is SessionStatus.Active, is SessionStatus.Unknown -> {
                            DetoxCommandResult.Unreachable
                        }
                    }
                }
            }
        }

    /**
     * Passes the attempt if its end time is reached. Idempotent: the ticker, the wakeup and app
     * resume can all call it, and only the first call after the end does anything.
     */
    suspend fun expireIfDue(): Boolean {
        restored.await()
        return mutex.withLock { expireLocked() }
    }

    /**
     * App resume and the level-check wakeup: pass if the end is reached, otherwise ask the blocker.
     * Only a positive "no session" before the end fails the level.
     */
    suspend fun check() {
        restored.await()
        mutex.withLock { checkLocked() }
    }

    fun acknowledgeOutcome() {
        mutableState.update { it.copy(lastOutcome = null) }
    }

    private suspend fun checkLocked() {
        if (expireLocked()) return
        val attempt = current() ?: return
        when (blocker.activeSession()) {
            SessionStatus.None -> finish(attempt, SessionOutcome.Abandoned, LevelOutcome.StoppedInBlocker)
            is SessionStatus.Active, is SessionStatus.Unknown -> publish(attempt)
        }
    }

    private suspend fun expireLocked(): Boolean {
        val attempt = current() ?: return false
        if (clock.now() < attempt.endsAt) return false
        val longest = sessions.longestPassedLevelMinutes.first()
        val advanced = longest == null || attempt.levelMinutes > longest
        val outcome = if (advanced) LevelOutcome.Passed else LevelOutcome.PassedAgain
        finish(attempt, SessionOutcome.Completed, outcome, endedAt = attempt.endsAt)
        alerts.levelPassed(attempt.levelMinutes, advanced)
        return true
    }

    private suspend fun restore() {
        val open = sessions.openDetoxAttempt() ?: return
        publish(open.toAttempt(canStopEarly(open.profile)))
        expireLocked()
    }

    private suspend fun open(
        profile: ProfileId,
        minutes: Int,
    ): DetoxCommandResult {
        alerts.clear()
        val session = sessions.startDetoxAttempt(profile, minutes, clock.now())
        mutableState.update { it.copy(lastOutcome = null) }
        publish(session.toAttempt(canStopEarly(profile)))
        return DetoxCommandResult.Ok
    }

    // Not listed (e.g. unreachable) counts as stoppable; a Refused stop then turns it off.
    private suspend fun canStopEarly(profile: ProfileId?): Boolean =
        blocker.profiles().firstOrNull { it.id == profile }?.canStopEarly ?: true

    private suspend fun finish(
        attempt: DetoxAttempt,
        outcome: SessionOutcome,
        levelOutcome: LevelOutcome,
        endedAt: Instant = clock.now(),
    ) {
        sessions.finish(attempt.id, endedAt, outcome)
        wakeups.cancelLevelCheck()
        mutableState.value = DetoxState(lastOutcome = levelOutcome)
    }

    private suspend fun command(block: suspend () -> DetoxCommandResult): DetoxCommandResult {
        restored.await()
        return mutex.withLock { block() }
    }

    private fun current(): DetoxAttempt? = mutableState.value.attempt

    private fun publish(attempt: DetoxAttempt) {
        val now = clock.now()
        mutableState.update { it.copy(attempt = attempt, remaining = remaining(attempt, now)) }
        // Check shortly before the end, so a session stopped inside the blocker is caught while it
        // can still be told apart from one that simply expired; then wake again at the end.
        val checkAt = attempt.endsAt - CHECK_LEAD
        wakeups.scheduleLevelCheck(if (now < checkAt) checkAt else attempt.endsAt)
        ensureTicking()
    }

    private fun remaining(
        attempt: DetoxAttempt,
        now: Instant,
    ): Duration = (attempt.endsAt - now).coerceIn(Duration.ZERO, attempt.levelMinutes.minutes)

    private fun ensureTicking() {
        if (ticker?.isActive == true) return
        ticker =
            scope.launch {
                while (isActive) {
                    val attempt = current() ?: break
                    val left = attempt.endsAt - clock.now()
                    if (left <= Duration.ZERO) {
                        expireIfDue()
                        continue
                    }
                    mutableState.update {
                        if (it.attempt == attempt) it.copy(remaining = remaining(attempt, clock.now())) else it
                    }
                    val toNextSecond = left.inWholeMilliseconds % MILLIS_PER_SECOND
                    delay(if (toNextSecond == 0L) MILLIS_PER_SECOND else toNextSecond)
                }
            }
    }

    private companion object {
        val CHECK_LEAD = 1.minutes
        const val MILLIS_PER_SECOND = 1_000L
    }
}

private fun Session.toAttempt(canStopEarly: Boolean) =
    DetoxAttempt(id = id, levelMinutes = plannedMinutes, startedAt = startedAt, canStopEarly = canStopEarly)
