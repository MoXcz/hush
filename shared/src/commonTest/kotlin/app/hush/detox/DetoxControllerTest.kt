// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.detox

import app.hush.blocker.BlockProfile
import app.hush.blocker.BlockResult
import app.hush.blocker.ProfileId
import app.hush.blocker.SessionStatus
import app.hush.blocker.UnknownReason
import app.hush.blocker.fake.FakeBlocker
import app.hush.blocklists.FakeBlockListRepository
import app.hush.history.FakeSessionRepository
import app.hush.history.Session
import app.hush.history.SessionId
import app.hush.history.SessionOutcome
import app.hush.history.SessionRepository
import app.hush.history.SessionType
import app.hush.settings.FakeSettingsRepository
import app.hush.settings.Settings
import app.hush.timer.FakeWakeupScheduler
import app.hush.timer.TestClock
import app.hush.timer.TestClock.Companion.START
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DetoxControllerTest {
    private val detox = ProfileId("detox")
    private val blocker =
        FakeBlocker(
            profiles =
                listOf(
                    BlockProfile(ProfileId("deep"), "Deep work", true),
                    BlockProfile(detox, "Detox", true),
                ),
        )
    private val settings = FakeSettingsRepository(Settings(detoxGoal = 2.hours))
    private val blockLists = FakeBlockListRepository()
    private val sessions = FakeSessionRepository()
    private val wakeups = FakeWakeupScheduler()
    private val alerts = FakeLevelAlerts()

    private fun TestScope.controller(): DetoxController =
        DetoxController(blocker, settings, blockLists, sessions, wakeups, alerts, TestClock(testScheduler), backgroundScope).also {
            runCurrent()
        }

    private fun TestScope.advance(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    private fun blockerCommands() = blocker.calls.filter { it.startsWith("start") || it == "stop" }

    private fun detoxRows() = sessions.rows.value.filter { it.type == SessionType.Detox }

    @Test
    fun `an attempt starts blocking for the current level and opens a row`() =
        runTest {
            val controller = controller()
            assertEquals(DetoxCommandResult.Ok, controller.startAttempt())
            assertEquals(listOf("start:detox:60"), blockerCommands())
            val attempt = assertNotNull(controller.state.value.attempt)
            assertEquals(60, attempt.levelMinutes)
            assertEquals(START + 1.hours, attempt.endsAt)
            assertEquals(1.hours, controller.state.value.remaining)
            assertEquals(listOf(Session(attempt.id, SessionType.Detox, detox, 60, START)), detoxRows())
        }

    @Test
    fun `the level check is scheduled a minute before the end`() =
        runTest {
            val controller = controller()
            controller.startAttempt()
            assertEquals(START + 59.minutes, wakeups.levelCheck)
        }

    @Test
    fun `level passes when end time reached without stop`() =
        runTest {
            val controller = controller()
            controller.startAttempt()
            advance(1.hours - 1.seconds)
            assertNotNull(controller.state.value.attempt)
            assertEquals(1.seconds, controller.state.value.remaining)

            advance(1.seconds)
            assertNull(controller.state.value.attempt)
            assertEquals(LevelOutcome.Passed, controller.state.value.lastOutcome)
            val row = detoxRows().single()
            assertEquals(SessionOutcome.Completed, row.outcome)
            assertEquals(START + 1.hours, row.endedAt)
            assertEquals(listOf(60), alerts.passed)
            assertEquals(listOf(true), alerts.advanced)
            assertNull(wakeups.levelCheck)
        }

    @Test
    fun `passing a level makes the next attempt the next level`() =
        runTest {
            val controller = controller()
            controller.startAttempt()
            advance(1.hours)
            controller.startAttempt()
            assertEquals(listOf("start:detox:60", "start:detox:90"), blockerCommands())
            assertNull(controller.state.value.lastOutcome)
        }

    @Test
    fun `ending through the panic flow stops blocking and fails the level`() =
        runTest {
            val controller = controller()
            controller.startAttempt()
            advance(10.minutes)
            assertEquals(DetoxCommandResult.Ok, controller.endEarly())
            assertEquals(listOf("start:detox:60", "stop"), blockerCommands())
            assertNull(controller.state.value.attempt)
            assertEquals(LevelOutcome.EndedEarly, controller.state.value.lastOutcome)
            val row = detoxRows().single()
            assertEquals(SessionOutcome.Stopped, row.outcome)
            assertEquals(START + 10.minutes, row.endedAt)
            assertNull(wakeups.levelCheck)
            assertTrue(alerts.passed.isEmpty())
        }

    @Test
    fun `a failed level is retried at the same length with no penalty`() =
        runTest {
            val controller = controller()
            controller.startAttempt()
            advance(1.hours)
            controller.startAttempt()
            controller.endEarly()
            controller.startAttempt()
            assertEquals(listOf("start:detox:60", "start:detox:90", "stop", "start:detox:90"), blockerCommands())
        }

    @Test
    fun `level fails when the blocker reports no session before the end`() =
        runTest {
            val controller = controller()
            controller.startAttempt()
            advance(20.minutes)
            blocker.session = SessionStatus.None
            controller.check()
            assertNull(controller.state.value.attempt)
            assertEquals(LevelOutcome.StoppedInBlocker, controller.state.value.lastOutcome)
            assertEquals(SessionOutcome.Abandoned, detoxRows().single().outcome)
            assertEquals(START + 20.minutes, detoxRows().single().endedAt)
        }

    @Test
    fun `an unknown blocker state never fails the level`() =
        runTest {
            val controller = controller()
            controller.startAttempt()
            advance(20.minutes)
            UnknownReason.entries.forEach { reason ->
                blocker.session = SessionStatus.Unknown(reason)
                controller.check()
                assertNotNull(controller.state.value.attempt)
            }
            assertNull(detoxRows().single().outcome)
            advance(40.minutes)
            assertEquals(LevelOutcome.Passed, controller.state.value.lastOutcome)
        }

    @Test
    fun `the check before the end re-arms the wakeup for the end`() =
        runTest {
            val controller = controller()
            controller.startAttempt()
            advance(59.minutes)
            controller.check()
            assertNotNull(controller.state.value.attempt)
            assertEquals(START + 1.hours, wakeups.levelCheck)
        }

    @Test
    fun `a check after the end passes without asking the blocker`() =
        runTest {
            val controller = controller()
            controller.startAttempt()
            blocker.calls.clear()
            blocker.session = SessionStatus.None
            advanceTimeBy(2.hours) // ticker and wakeup both late; the check decides from timestamps
            controller.check()
            assertEquals(LevelOutcome.Passed, controller.state.value.lastOutcome)
            assertFalse("activeSession" in blocker.calls)
        }

    @Test
    fun `expiry is idempotent across the ticker, the wakeup and resume`() =
        runTest {
            val controller = controller()
            controller.startAttempt()
            advance(1.hours)
            assertFalse(controller.expireIfDue())
            controller.check()
            assertEquals(listOf(60), alerts.passed)
            assertEquals(1, detoxRows().size)
        }

    @Test
    fun `a failing restore refuses to start an attempt instead of risking a duplicate`() =
        runTest {
            val failing =
                object : SessionRepository by sessions {
                    override suspend fun openDetoxAttempt(): Session? = throw IllegalStateException("corrupt")
                }
            val controller =
                DetoxController(blocker, settings, blockLists, failing, wakeups, alerts, TestClock(testScheduler), backgroundScope)
            runCurrent()
            controller.awaitRestore()
            assertEquals(DetoxCommandResult.Unconfirmed, controller.startAttempt())
            assertTrue(blocker.calls.none { it.startsWith("start") })
        }

    @Test
    fun `an open attempt is restored after process death`() =
        runTest {
            sessions.rows.value = listOf(Session(SessionId(7), SessionType.Detox, detox, 90, START - 30.minutes))
            val controller = controller()
            val attempt = assertNotNull(controller.state.value.attempt)
            assertEquals(SessionId(7), attempt.id)
            assertEquals(1.hours, controller.state.value.remaining)
            assertEquals(START + 59.minutes, wakeups.levelCheck)
            assertTrue(blockerCommands().isEmpty())
            advance(1.hours)
            assertEquals(LevelOutcome.Passed, controller.state.value.lastOutcome)
        }

    @Test
    fun `an attempt whose end passed while the process was dead passes on restore`() =
        runTest {
            sessions.rows.value = listOf(Session(SessionId(7), SessionType.Detox, detox, 60, START - 3.hours))
            val controller = controller()
            assertNull(controller.state.value.attempt)
            assertEquals(LevelOutcome.Passed, controller.state.value.lastOutcome)
            assertEquals(START - 2.hours, detoxRows().single().endedAt)
        }

    @Test
    fun `an empty detox list means no attempt and no blocker call`() =
        runTest {
            blockLists.setApps(detox, emptySet())
            val controller = controller()
            assertEquals(DetoxCommandResult.NoProfile, controller.startAttempt())
            assertTrue(blockerCommands().isEmpty())
            assertTrue(detoxRows().isEmpty())
        }

    @Test
    fun `denied or refused start changes nothing`() =
        runTest {
            val controller = controller()
            blocker.nextResult = BlockResult.Denied
            assertEquals(DetoxCommandResult.NeedsSetup, controller.startAttempt())
            blocker.nextResult = BlockResult.Refused
            assertEquals(DetoxCommandResult.Refused, controller.startAttempt())
            assertNull(controller.state.value.attempt)
            assertTrue(detoxRows().isEmpty())
            assertNull(wakeups.levelCheck)
        }

    @Test
    fun `lost connection on start looks at the session and never retries`() =
        runTest {
            val controller = controller()
            blocker.nextResult = BlockResult.ConnectionLost

            blocker.session = SessionStatus.None
            assertEquals(DetoxCommandResult.Unreachable, controller.startAttempt())
            assertNull(controller.state.value.attempt)

            blocker.session = SessionStatus.Unknown(UnknownReason.Unreachable)
            assertEquals(DetoxCommandResult.Unconfirmed, controller.startAttempt())
            assertNotNull(controller.state.value.attempt)
            assertEquals(listOf("start:detox:60", "start:detox:60"), blockerCommands())
        }

    @Test
    fun `lost connection on start with an active session counts as started`() =
        runTest {
            val controller = controller()
            blocker.nextResult = BlockResult.ConnectionLost
            blocker.session = SessionStatus.Active("Detox", 60)
            assertEquals(DetoxCommandResult.Ok, controller.startAttempt())
            assertNotNull(controller.state.value.attempt)
            assertEquals(1, blockerCommands().size)
        }

    @Test
    fun `starting while an attempt runs does nothing`() =
        runTest {
            val controller = controller()
            controller.startAttempt()
            controller.startAttempt()
            assertEquals(1, blockerCommands().size)
            assertEquals(1, detoxRows().size)
        }

    @Test
    fun `a profile that can't stop early is known from the start`() =
        runTest {
            blocker.profiles = listOf(BlockProfile(detox, "Detox", canStopEarly = false))
            val controller = controller()
            controller.startAttempt()
            assertFalse(assertNotNull(controller.state.value.attempt).canStopEarly)
        }

    @Test
    fun `refused stop keeps the attempt and marks it as not stoppable`() =
        runTest {
            val controller = controller()
            controller.startAttempt()
            blocker.nextResult = BlockResult.Refused
            assertEquals(DetoxCommandResult.CantStopEarly, controller.endEarly())
            val attempt = assertNotNull(controller.state.value.attempt)
            assertFalse(attempt.canStopEarly)
            assertNull(detoxRows().single().outcome)
        }

    @Test
    fun `lost connection on stop ends only when the session is known to be gone`() =
        runTest {
            val controller = controller()
            controller.startAttempt()
            blocker.nextResult = BlockResult.ConnectionLost
            blocker.session = SessionStatus.Unknown(UnknownReason.Unreachable)
            assertEquals(DetoxCommandResult.Unreachable, controller.endEarly())
            assertNotNull(controller.state.value.attempt)

            blocker.session = SessionStatus.None
            assertEquals(DetoxCommandResult.Ok, controller.endEarly())
            assertEquals(LevelOutcome.EndedEarly, controller.state.value.lastOutcome)
            assertEquals(listOf("start:detox:60", "stop", "stop"), blockerCommands())
        }

    @Test
    fun `the outcome stays until acknowledged`() =
        runTest {
            val controller = controller()
            controller.startAttempt()
            controller.endEarly()
            assertEquals(LevelOutcome.EndedEarly, controller.state.value.lastOutcome)
            controller.acknowledgeOutcome()
            assertNull(controller.state.value.lastOutcome)
        }

    @Test
    fun `after the goal is passed attempts repeat the goal level`() =
        runTest {
            settings.settings.value = settings.settings.value.copy(detoxGoal = 1.hours)
            val controller = controller()
            controller.startAttempt()
            advance(1.hours)
            controller.startAttempt()
            assertEquals(listOf("start:detox:60", "start:detox:60"), blockerCommands())
        }

    private fun passed(vararg minutes: Int) {
        sessions.rows.value =
            minutes.mapIndexed { i, m ->
                Session(
                    SessionId(100L + i),
                    SessionType.Detox,
                    detox,
                    m,
                    START - 1.days,
                    START - 1.days + m.minutes,
                    SessionOutcome.Completed,
                )
            }
    }

    @Test
    fun `start without a choice still attempts the next level`() =
        runTest {
            passed(60, 90)
            val controller = controller()
            assertEquals(DetoxCommandResult.Ok, controller.startAttempt())
            assertEquals(listOf("start:detox:120"), blockerCommands())
        }

    @Test
    fun `a passed level can be attempted instead of the next one`() =
        runTest {
            passed(60, 90)
            val controller = controller()
            assertEquals(DetoxCommandResult.Ok, controller.startAttempt(1.hours))
            assertEquals(listOf("start:detox:60"), blockerCommands())
            assertEquals(
                60,
                controller.state.value.attempt
                    ?.levelMinutes,
            )
            assertEquals(60, detoxRows().single { it.outcome == null }.plannedMinutes)
        }

    @Test
    fun `the controller refuses a locked level`() =
        runTest {
            passed(60)
            val controller = controller()
            assertEquals(DetoxCommandResult.LevelLocked, controller.startAttempt(2.hours))
            assertEquals(DetoxCommandResult.LevelLocked, controller.startAttempt(75.minutes))
            assertTrue(blockerCommands().isEmpty())
            assertNull(controller.state.value.attempt)
            assertEquals(1, detoxRows().size)
        }

    @Test
    fun `progress is unchanged after passing a shorter level`() =
        runTest {
            passed(60, 90)
            val controller = controller()
            controller.startAttempt(1.hours)
            advance(1.hours)
            assertEquals(LevelOutcome.PassedAgain, controller.state.value.lastOutcome)
            assertEquals(listOf(false), alerts.advanced)
            controller.startAttempt()
            assertEquals(listOf("start:detox:60", "start:detox:120"), blockerCommands())
        }

    @Test
    fun `progress is unchanged after failing a shorter level`() =
        runTest {
            passed(60, 90)
            val controller = controller()
            controller.startAttempt(1.hours)
            advance(10.minutes)
            controller.endEarly()
            assertEquals(LevelOutcome.EndedEarly, controller.state.value.lastOutcome)
            controller.startAttempt()
            assertEquals(listOf("start:detox:60", "stop", "start:detox:120"), blockerCommands())
        }

    @Test
    fun `a shorter attempt is restored after process death with its own length`() =
        runTest {
            passed(60, 90)
            sessions.rows.value += Session(SessionId(7), SessionType.Detox, detox, 60, START - 30.minutes)
            val controller = controller()
            assertEquals(
                60,
                controller.state.value.attempt
                    ?.levelMinutes,
            )
            assertEquals(30.minutes, controller.state.value.remaining)
            advance(30.minutes)
            assertEquals(LevelOutcome.PassedAgain, controller.state.value.lastOutcome)
            assertEquals(listOf(60), alerts.passed)
        }
}
