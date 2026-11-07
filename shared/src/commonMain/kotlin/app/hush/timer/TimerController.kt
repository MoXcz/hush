// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer

import app.hush.activities.Activity
import app.hush.activities.ActivityId
import app.hush.activities.ActivityRepository
import app.hush.blocker.BlockResult
import app.hush.blocker.Blocker
import app.hush.blocker.ProfileId
import app.hush.blocker.SessionStatus
import app.hush.blocklists.BlockListRepository
import app.hush.blocklists.ProfileRole
import app.hush.blocklists.profileFor
import app.hush.history.SessionOutcome
import app.hush.history.SessionRepository
import app.hush.settings.SettingsRepository
import app.hush.tags.Tag
import app.hush.tags.TagId
import app.hush.tags.TagRepository
import app.hush.usage.WorkUsageRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** What a timer command did. Anything but [Ok] and [Unconfirmed] means the timer didn't change. */
enum class TimerCommandResult {
    Ok,

    /** The blocker denied the call: approval or setup is missing. */
    NeedsSetup,

    /** The blocker refused to start (e.g. its group is gone, or a session it can't end is live). */
    Refused,

    /** The blocker refused to stop: the profile can't stop early. */
    CantStopEarly,

    /** The blocker couldn't be reached, and it's known (or possible) that nothing changed. */
    Unreachable,

    /** The timer started, but the connection dropped and the blocker's state couldn't be read. */
    Unconfirmed,
}

/**
 * The one owner of the Pomodoro timer. UI, foreground service and phase-end wakeups only read
 * [state] or call these functions. Blocking goes through [Blocker] only: a work phase starts a
 * session, a work phase ending does nothing (the session expires by itself), pause stops it and
 * resume starts it again for the remaining minutes. No blocker call is ever retried automatically.
 * Durations come from the selected tag; every tag blocks with the same Deep work list. The selected
 * activity can change at any time and is written into a work phase when it's recorded.
 */
class TimerController(
    private val blocker: Blocker,
    private val settingsRepository: SettingsRepository,
    private val blockLists: BlockListRepository,
    tagRepository: TagRepository,
    activityRepository: ActivityRepository,
    private val store: TimerStateStore,
    private val wakeups: WakeupScheduler,
    private val alerts: PhaseAlerts,
    private val sessions: SessionRepository,
    private val usage: WorkUsageRecorder,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val restored = CompletableDeferred<Unit>()
    private val mutableState = MutableStateFlow(TimerState())
    private var durations = TimerDurations()
    private var tag: Tag? = null
    private var activity: Activity? = null

    // A deleted or unknown selection falls back to the first remaining tag.
    private val selectedTag =
        combine(settingsRepository.settings.map { it.selectedTagId }.distinctUntilChanged(), tagRepository.tags) { id, tags ->
            tags.firstOrNull { it.id == id } ?: tags.firstOrNull()
        }.distinctUntilChanged()

    // A deleted or unknown selection means no activity.
    private val selectedActivity =
        combine(
            settingsRepository.settings.map { it.selectedActivityId }.distinctUntilChanged(),
            activityRepository.activities,
        ) { id, all ->
            all.firstOrNull { it.id == id }
        }.distinctUntilChanged()
    private var ticker: Job? = null

    val state: StateFlow<TimerState> = mutableState.asStateFlow()

    init {
        scope.launch {
            // A failed restore must neither hang waiters nor kill the tag collector: start fresh.
            try {
                mutex.withLock { restore() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                restored.complete(Unit)
            }
            launch {
                selectedActivity.collect { newActivity ->
                    mutex.withLock {
                        activity = newActivity
                        mutableState.update { it.copy(activity = newActivity) }
                    }
                }
            }
            selectedTag.collect { newTag ->
                mutex.withLock {
                    apply(newTag)
                    publish(PomodoroEngine.withDurations(current(), durations))
                }
            }
        }
    }

    /** Suspends until the stored timer has been read back after process start. */
    suspend fun awaitRestore() = restored.await()

    /** Selects the tag for the next work phase. Refused (false) while a work phase is in progress. */
    suspend fun selectTag(id: TagId): Boolean {
        restored.await()
        return mutex.withLock {
            if (current().workInProgress) return@withLock false
            settingsRepository.setSelectedTag(id)
            true
        }
    }

    /** Selects what the work phases are spent doing (null: nothing). Allowed at any time, also mid-phase. */
    suspend fun selectActivity(id: ActivityId?) {
        settingsRepository.setSelectedActivity(id)
    }

    suspend fun start(): TimerCommandResult =
        command { snapshot ->
            when {
                snapshot.run is RunState.Running -> TimerCommandResult.Ok
                snapshot.phase != Phase.Work -> run(snapshot)
                else -> startWork(snapshot)
            }
        }

    suspend fun pause(): TimerCommandResult =
        command { snapshot ->
            if (snapshot.run !is RunState.Running) return@command TimerCommandResult.Ok
            val stopped = stopBlocking(snapshot)
            if (stopped == TimerCommandResult.Ok) {
                publish(PomodoroEngine.pause(snapshot.copy(blocking = false), clock.now()))
            }
            stopped
        }

    suspend fun skip(): TimerCommandResult =
        command { snapshot ->
            val stopped = stopBlocking(snapshot)
            if (stopped == TimerCommandResult.Ok) {
                alerts.clear()
                recordStoppedWork(snapshot)
                publish(PomodoroEngine.advance(snapshot, durations))
            }
            stopped
        }

    suspend fun reset(): TimerCommandResult =
        command { snapshot ->
            val stopped = stopBlocking(snapshot)
            if (stopped == TimerCommandResult.Ok) {
                alerts.clear()
                recordStoppedWork(snapshot)
                publish(PomodoroEngine.initial(durations))
            }
            stopped
        }

    /**
     * Ends the phase if its time is up and stages the next one. Idempotent: the ticker, the wakeup
     * and restore can all call it, and only the first call after the end time does anything.
     */
    suspend fun expireIfDue(): Boolean {
        restored.await()
        return mutex.withLock { expireLocked() }
    }

    private suspend fun expireLocked(): Boolean {
        val snapshot = current()
        if (!PomodoroEngine.isDue(snapshot, clock.now())) return false
        val next = PomodoroEngine.advance(snapshot, durations)
        val endsAt = (snapshot.run as RunState.Running).endsAt
        if (snapshot.phase == Phase.Work) {
            recordWork(snapshot, endedAt = endsAt, outcome = SessionOutcome.Completed)
        }
        publish(next)
        alerts.phaseEnded(snapshot.phase, next.phase)
        return true
    }

    // Only a work phase that was actually started counts; a staged one is just skipped.
    private suspend fun recordStoppedWork(snapshot: TimerSnapshot) {
        if (snapshot.phase != Phase.Work || snapshot.startedAt == null) return
        recordWork(snapshot, endedAt = clock.now(), outcome = SessionOutcome.Stopped)
    }

    // Called with the lock held, so a phase can be written at most once. The apps are read after
    // the lock is released (the recorder launches its own job).
    private suspend fun recordWork(
        snapshot: TimerSnapshot,
        endedAt: Instant,
        outcome: SessionOutcome,
    ) {
        val startedAt = snapshot.startedAt ?: (endedAt - snapshot.duration)
        val uid =
            sessions.recordWork(
                profile = blockLists.profileFor(ProfileRole.DeepWork),
                tagId = snapshot.tagId ?: tag?.id,
                plannedMinutes = snapshot.duration.wholeMinutesUp(),
                startedAt = startedAt,
                endedAt = endedAt,
                outcome = outcome,
                activityId = activity?.id,
            )
        usage.record(uid, startedAt, endedAt)
    }

    private suspend fun restore() {
        apply(selectedTag.first())
        activity = selectedActivity.first()
        val stored = store.load() ?: PomodoroEngine.initial(durations)
        publish(PomodoroEngine.withDurations(stored, durations))
        expireLocked()
    }

    private suspend fun startWork(snapshot: TimerSnapshot): TimerCommandResult {
        val profile = blockLists.profileFor(ProfileRole.DeepWork) ?: return run(snapshot.copy(blocking = false))
        val remaining = PomodoroEngine.remaining(snapshot, clock.now())
        return when (blocker.start(profile, remaining.wholeMinutesUp())) {
            BlockResult.Ok -> {
                runBlocked(snapshot, profile)
            }

            BlockResult.Denied -> {
                TimerCommandResult.NeedsSetup
            }

            BlockResult.Refused -> {
                TimerCommandResult.Refused
            }

            // The start may or may not have run, so look before doing anything; never call start again.
            BlockResult.ConnectionLost -> {
                when (blocker.activeSession()) {
                    is SessionStatus.Active -> {
                        runBlocked(snapshot, profile)
                    }

                    SessionStatus.None -> {
                        TimerCommandResult.Unreachable
                    }

                    // Run anyway, so the user isn't invited to press Start again and stack sessions.
                    is SessionStatus.Unknown -> {
                        runBlocked(snapshot, profile)
                        TimerCommandResult.Unconfirmed
                    }
                }
            }
        }
    }

    private suspend fun runBlocked(
        snapshot: TimerSnapshot,
        profile: ProfileId,
    ): TimerCommandResult {
        // Not listed (e.g. unreachable) means unknown: show pause, the blocker still enforces its rule.
        val canStopEarly = blocker.profiles().firstOrNull { it.id == profile }?.canStopEarly ?: true
        return run(snapshot.copy(blocking = true, canStopEarly = canStopEarly))
    }

    private suspend fun run(snapshot: TimerSnapshot): TimerCommandResult {
        alerts.clear()
        val tagged = if (snapshot.phase == Phase.Work && snapshot.run == RunState.Ready) snapshot.copy(tagId = tag?.id) else snapshot
        publish(PomodoroEngine.start(tagged, clock.now()))
        return TimerCommandResult.Ok
    }

    // Ok only when no session of ours can still be running, so pause never leaves apps blocked.
    private suspend fun stopBlocking(snapshot: TimerSnapshot): TimerCommandResult {
        if (!snapshot.blocking || snapshot.run !is RunState.Running) return TimerCommandResult.Ok
        return when (blocker.stop()) {
            BlockResult.Ok -> {
                TimerCommandResult.Ok
            }

            BlockResult.Denied -> {
                TimerCommandResult.NeedsSetup
            }

            BlockResult.Refused -> {
                publish(snapshot.copy(canStopEarly = false))
                TimerCommandResult.CantStopEarly
            }

            BlockResult.ConnectionLost -> {
                when (blocker.activeSession()) {
                    SessionStatus.None -> TimerCommandResult.Ok
                    is SessionStatus.Active, is SessionStatus.Unknown -> TimerCommandResult.Unreachable
                }
            }
        }
    }

    private suspend fun command(block: suspend (TimerSnapshot) -> TimerCommandResult): TimerCommandResult {
        restored.await()
        return mutex.withLock { block(current()) }
    }

    private fun current(): TimerSnapshot = mutableState.value.snapshot

    private fun apply(newTag: Tag?) {
        tag = newTag
        durations = newTag?.durations ?: TimerDurations()
    }

    private suspend fun publish(snapshot: TimerSnapshot) {
        mutableState.value =
            TimerState(
                snapshot = snapshot,
                remaining = PomodoroEngine.remaining(snapshot, clock.now()),
                workPhasesPerSet = durations.longBreakAfter,
                tag = tag,
                activity = activity,
            )
        when (val run = snapshot.run) {
            is RunState.Running -> {
                wakeups.schedulePhaseEnd(run.endsAt)
                ensureTicking()
            }

            RunState.Ready, is RunState.Paused -> {
                wakeups.cancelPhaseEnd()
            }
        }
        store.save(snapshot)
    }

    // The loop only republishes the remaining time; it stops itself once the timer isn't running.
    private fun ensureTicking() {
        if (ticker?.isActive == true) return
        ticker =
            scope.launch {
                while (isActive) {
                    val snapshot = current()
                    val run = snapshot.run as? RunState.Running ?: break
                    val left = run.endsAt - clock.now()
                    if (left <= Duration.ZERO) {
                        expireIfDue()
                        continue
                    }
                    mutableState.update {
                        if (it.snapshot == snapshot) it.copy(remaining = left.coerceAtMost(snapshot.duration)) else it
                    }
                    // Wake on the next whole second of the countdown.
                    val toNextSecond = left.inWholeMilliseconds % MILLIS_PER_SECOND
                    delay(if (toNextSecond == 0L) MILLIS_PER_SECOND else toNextSecond)
                }
            }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}

/** Blocker sessions are whole minutes; round up so blocking covers the whole phase. At least 1. */
internal fun Duration.wholeMinutesUp(): Int {
    val millis = inWholeMilliseconds.coerceAtLeast(0)
    return ((millis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt().coerceAtLeast(1)
}

private const val MILLIS_PER_MINUTE = 60_000L
