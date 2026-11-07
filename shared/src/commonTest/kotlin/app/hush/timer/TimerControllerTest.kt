// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer

import app.hush.activities.FakeActivityRepository
import app.hush.blocker.BlockProfile
import app.hush.blocker.BlockResult
import app.hush.blocker.Blocker
import app.hush.blocker.ProfileId
import app.hush.blocker.SessionStatus
import app.hush.blocker.UnknownReason
import app.hush.blocker.fake.FakeBlocker
import app.hush.blocklists.FakeBlockListRepository
import app.hush.history.FakeSessionRepository
import app.hush.history.SessionOutcome
import app.hush.history.SessionType
import app.hush.settings.FakeSettingsRepository
import app.hush.tags.FakeTagRepository
import app.hush.tags.TagId
import app.hush.timer.TestClock.Companion.START
import app.hush.usage.AppTime
import app.hush.usage.FakeAppUsageReader
import app.hush.usage.FakeAppUsageRepository
import app.hush.usage.WorkUsageRecorder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TimerControllerTest {
    private val deep = ProfileId("deep")
    private val blocker = FakeBlocker()
    private val settings = FakeSettingsRepository()
    private val blockLists = FakeBlockListRepository()
    private val store = FakeTimerStateStore()
    private val wakeups = FakeWakeupScheduler()
    private val alerts = FakePhaseAlerts()
    private val sessions = FakeSessionRepository()
    private val tags = FakeTagRepository()
    private val activities = FakeActivityRepository()
    private val usageReader = FakeAppUsageReader()
    private val appUsage = FakeAppUsageRepository()

    private fun TestScope.controller(): TimerController =
        TimerController(
            blocker,
            settings,
            blockLists,
            tags,
            activities,
            store,
            wakeups,
            alerts,
            sessions,
            WorkUsageRecorder(usageReader, appUsage, backgroundScope),
            TestClock(testScheduler),
            backgroundScope,
        ).also {
            runCurrent()
        }

    private fun TestScope.advance(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    private val TimerController.snapshot get() = state.value.snapshot

    private fun blockerCommands() = blocker.calls.filter { it.startsWith("start") || it == "stop" }

    @Test
    fun `a failing restore starts fresh and keeps following the tag`() =
        runTest {
            val failing =
                object : TimerStateStore by store {
                    override suspend fun load(): TimerSnapshot? = throw IllegalStateException("corrupt")
                }
            val timer =
                TimerController(
                    blocker,
                    settings,
                    blockLists,
                    tags,
                    activities,
                    failing,
                    wakeups,
                    alerts,
                    sessions,
                    WorkUsageRecorder(usageReader, appUsage, backgroundScope),
                    TestClock(testScheduler),
                    backgroundScope,
                )
            runCurrent()
            timer.awaitRestore()
            assertEquals(TimerSnapshot(), timer.snapshot)
            assertTrue(timer.selectTag(TagId(3)))
            runCurrent()
            assertEquals(50.minutes, timer.state.value.remaining)
        }

    @Test
    fun `starts ready on the first work phase`() =
        runTest {
            val timer = controller()
            assertEquals(TimerSnapshot(), timer.snapshot)
            assertEquals(25.minutes, timer.state.value.remaining)
            assertEquals(4, timer.state.value.workPhasesPerSet)
        }

    @Test
    fun `work phase starts blocking for the work minutes and ends on time`() =
        runTest {
            val timer = controller()
            assertEquals(TimerCommandResult.Ok, timer.start())
            assertEquals(listOf("start:deep:25"), blockerCommands())
            assertEquals(RunState.Running(START + 25.minutes), timer.snapshot.run)
            assertTrue(timer.snapshot.blocking)
            assertEquals(START + 25.minutes, wakeups.scheduled)

            advance(25.minutes - 1.seconds)
            assertEquals(Phase.Work, timer.snapshot.phase)
            assertEquals(1.seconds, timer.state.value.remaining)

            advance(1.seconds)
            assertEquals(Phase.ShortBreak, timer.snapshot.phase)
            assertEquals(RunState.Ready, timer.snapshot.run)
            assertEquals(listOf(Phase.Work to Phase.ShortBreak), alerts.ended)
            assertNull(wakeups.scheduled)
        }

    @Test
    fun `work phase end does nothing in the blocker`() =
        runTest {
            val timer = controller()
            timer.start()
            advance(25.minutes)
            assertEquals(listOf("start:deep:25"), blockerCommands())
        }

    @Test
    fun `remaining time is derived from the clock while running`() =
        runTest {
            val timer = controller()
            timer.start()
            advance(10.minutes)
            assertEquals(15.minutes, timer.state.value.remaining)
            // Republished once per second of the countdown, not in between.
            advance(500.milliseconds)
            assertEquals(15.minutes, timer.state.value.remaining)
            advance(500.milliseconds)
            assertEquals(14.minutes + 59.seconds, timer.state.value.remaining)
        }

    @Test
    fun `breaks are not blocked`() =
        runTest {
            val timer = controller()
            timer.skip()
            assertEquals(Phase.ShortBreak, timer.snapshot.phase)
            timer.start()
            assertTrue(timer.state.value.isRunning)
            assertEquals(emptyList(), blockerCommands())
            assertFalse(timer.snapshot.blocking)
        }

    @Test
    fun `four work phases lead to a long break, then the set starts again`() =
        runTest {
            val timer = controller()
            val seen = mutableListOf<Phase>()
            repeat(8) {
                seen += timer.snapshot.phase
                timer.start()
                advance(timer.snapshot.duration)
            }
            seen += timer.snapshot.phase
            assertEquals(
                listOf(
                    Phase.Work,
                    Phase.ShortBreak,
                    Phase.Work,
                    Phase.ShortBreak,
                    Phase.Work,
                    Phase.ShortBreak,
                    Phase.Work,
                    Phase.LongBreak,
                    Phase.Work,
                ),
                seen,
            )
            assertEquals(0, timer.snapshot.completedWorkPhases)
            assertEquals(4, blockerCommands().count { it == "start:deep:25" })
        }

    @Test
    fun `pause stops blocking and resume starts it again for the remaining minutes`() =
        runTest {
            val timer = controller()
            timer.start()
            advance(10.minutes + 30.seconds)
            assertEquals(TimerCommandResult.Ok, timer.pause())
            assertEquals(RunState.Paused(14.minutes + 30.seconds), timer.snapshot.run)
            assertFalse(timer.snapshot.blocking)
            assertNull(wakeups.scheduled)

            advance(1.hours)
            assertEquals(Phase.Work, timer.snapshot.phase)
            assertEquals(14.minutes + 30.seconds, timer.state.value.remaining)

            timer.start()
            assertEquals(listOf("start:deep:25", "stop", "start:deep:15"), blockerCommands())
            val resumedAt = START + 1.hours + 10.minutes + 30.seconds
            assertEquals(RunState.Running(resumedAt + 14.minutes + 30.seconds), timer.snapshot.run)
        }

    @Test
    fun `a profile that can't stop early is recorded so pause, skip and reset can be hidden`() =
        runTest {
            blocker.profiles = listOf(BlockProfile(deep, "Deep work", canStopEarly = false))
            val timer = controller()
            timer.start()
            assertTrue(timer.snapshot.blocking)
            assertFalse(timer.snapshot.canStopEarly)
        }

    @Test
    fun `a profile the blocker can't list is treated as stoppable`() =
        runTest {
            val unlisted =
                object : Blocker by blocker {
                    override suspend fun profiles(): List<BlockProfile> = emptyList()
                }
            val timer =
                TimerController(
                    unlisted,
                    settings,
                    blockLists,
                    tags,
                    activities,
                    store,
                    wakeups,
                    alerts,
                    sessions,
                    WorkUsageRecorder(usageReader, appUsage, backgroundScope),
                    TestClock(testScheduler),
                    backgroundScope,
                )
            timer.start()
            assertTrue(timer.snapshot.blocking)
            assertTrue(timer.snapshot.canStopEarly)
        }

    @Test
    fun `refused stop keeps the timer running and marks it as not stoppable`() =
        runTest {
            val timer = controller()
            timer.start()
            blocker.nextResult = BlockResult.Refused
            assertEquals(TimerCommandResult.CantStopEarly, timer.pause())
            assertTrue(timer.state.value.isRunning)
            assertFalse(timer.snapshot.canStopEarly)
            assertEquals(TimerCommandResult.CantStopEarly, timer.skip())
            assertEquals(Phase.Work, timer.snapshot.phase)
        }

    @Test
    fun `skip in a blocked work phase stops blocking and stages the break`() =
        runTest {
            val timer = controller()
            timer.start()
            advance(3.minutes)
            assertEquals(TimerCommandResult.Ok, timer.skip())
            assertEquals(listOf("start:deep:25", "stop"), blockerCommands())
            assertEquals(TimerSnapshot(Phase.ShortBreak, 1, 5.minutes, RunState.Ready), timer.snapshot)
            assertEquals(emptyList(), alerts.ended)
            assertNull(wakeups.scheduled)
        }

    @Test
    fun `reset stops blocking and returns to the first work phase`() =
        runTest {
            val timer = controller()
            timer.skip()
            timer.skip()
            timer.start()
            assertEquals(1, timer.snapshot.completedWorkPhases)
            assertEquals(TimerCommandResult.Ok, timer.reset())
            assertEquals(TimerSnapshot(), timer.snapshot)
            assertEquals(listOf("start:deep:25", "stop"), blockerCommands())
        }

    @Test
    fun `denied start asks for setup and leaves the timer ready`() =
        runTest {
            val timer = controller()
            blocker.nextResult = BlockResult.Denied
            assertEquals(TimerCommandResult.NeedsSetup, timer.start())
            assertEquals(RunState.Ready, timer.snapshot.run)
            assertNull(wakeups.scheduled)
        }

    @Test
    fun `refused start leaves the timer ready`() =
        runTest {
            val timer = controller()
            blocker.nextResult = BlockResult.Refused
            assertEquals(TimerCommandResult.Refused, timer.start())
            assertEquals(RunState.Ready, timer.snapshot.run)
        }

    @Test
    fun `connection lost on start with a live session runs the timer without retrying`() =
        runTest {
            val timer = controller()
            blocker.nextResult = BlockResult.ConnectionLost
            blocker.session = SessionStatus.Active("Deep work", 25)
            assertEquals(TimerCommandResult.Ok, timer.start())
            assertTrue(timer.state.value.isRunning)
            assertTrue(timer.snapshot.blocking)
            assertEquals(listOf("start:deep:25"), blockerCommands())
            assertTrue("activeSession" in blocker.calls)
        }

    @Test
    fun `connection lost on start with no session reports it and doesn't retry`() =
        runTest {
            val timer = controller()
            blocker.nextResult = BlockResult.ConnectionLost
            blocker.session = SessionStatus.None
            assertEquals(TimerCommandResult.Unreachable, timer.start())
            assertEquals(RunState.Ready, timer.snapshot.run)
            advance(1.minutes)
            assertEquals(listOf("start:deep:25"), blockerCommands())
        }

    @Test
    fun `connection lost on start with an unknown state runs unconfirmed and never retries`() =
        runTest {
            val timer = controller()
            blocker.nextResult = BlockResult.ConnectionLost
            blocker.session = SessionStatus.Unknown(UnknownReason.Unreachable)
            assertEquals(TimerCommandResult.Unconfirmed, timer.start())
            assertTrue(timer.state.value.isRunning)
            assertTrue(timer.snapshot.blocking)
            advance(5.minutes)
            assertEquals(TimerCommandResult.Ok, timer.start())
            assertEquals(listOf("start:deep:25"), blockerCommands())
        }

    @Test
    fun `connection lost on stop pauses only when the session is known to be gone`() =
        runTest {
            val timer = controller()
            timer.start()
            blocker.nextResult = BlockResult.ConnectionLost

            blocker.session = SessionStatus.Unknown(UnknownReason.Unreadable)
            assertEquals(TimerCommandResult.Unreachable, timer.pause())
            assertTrue(timer.state.value.isRunning)

            blocker.session = SessionStatus.Active("Deep work", 20)
            assertEquals(TimerCommandResult.Unreachable, timer.pause())
            assertTrue(timer.state.value.isRunning)

            blocker.session = SessionStatus.None
            assertEquals(TimerCommandResult.Ok, timer.pause())
            assertTrue(timer.snapshot.run is RunState.Paused)
            assertEquals(3, blocker.calls.count { it == "stop" })
        }

    @Test
    fun `denied stop keeps the timer running and asks for setup`() =
        runTest {
            val timer = controller()
            timer.start()
            blocker.nextResult = BlockResult.Denied
            assertEquals(TimerCommandResult.NeedsSetup, timer.pause())
            assertTrue(timer.state.value.isRunning)
        }

    @Test
    fun `without a deep work profile the timer runs and nothing is blocked`() =
        runTest {
            blockLists.setApps(deep, emptySet())
            val timer = controller()
            assertEquals(TimerCommandResult.Ok, timer.start())
            assertTrue(timer.state.value.isRunning)
            assertFalse(timer.snapshot.blocking)
            timer.pause()
            assertEquals(emptyList(), blockerCommands())
        }

    @Test
    fun `restores a running phase from stored timestamps and ends it on time`() =
        runTest {
            store.stored =
                TimerSnapshot(Phase.Work, 1, 25.minutes, RunState.Running(START + 10.minutes), blocking = true)
            val timer = controller()
            assertEquals(10.minutes, timer.state.value.remaining)
            assertTrue(timer.snapshot.blocking)
            assertEquals(START + 10.minutes, wakeups.scheduled)
            advance(10.minutes)
            assertEquals(TimerSnapshot(Phase.ShortBreak, 2, 5.minutes), timer.snapshot)
            assertEquals(emptyList(), blockerCommands())
        }

    @Test
    fun `restore ends a phase that ran out while the process was dead`() =
        runTest {
            store.stored = TimerSnapshot(Phase.Work, 3, 25.minutes, RunState.Running(START - 1.minutes))
            val timer = controller()
            assertEquals(TimerSnapshot(Phase.LongBreak, 4, 15.minutes), timer.snapshot)
            assertEquals(listOf(Phase.Work to Phase.LongBreak), alerts.ended)
            assertEquals(timer.snapshot, store.stored)
        }

    @Test
    fun `restores a paused phase without running it`() =
        runTest {
            store.stored = TimerSnapshot(Phase.ShortBreak, 2, 5.minutes, RunState.Paused(2.minutes))
            val timer = controller()
            advance(1.hours)
            assertEquals(RunState.Paused(2.minutes), timer.snapshot.run)
            assertEquals(2.minutes, timer.state.value.remaining)
        }

    @Test
    fun `commands wait for the stored timer before acting`() =
        runTest {
            store.stored = TimerSnapshot(Phase.ShortBreak, 2, 5.minutes, RunState.Paused(2.minutes))
            val timer =
                TimerController(
                    blocker,
                    settings,
                    blockLists,
                    tags,
                    activities,
                    store,
                    wakeups,
                    alerts,
                    sessions,
                    WorkUsageRecorder(usageReader, appUsage, backgroundScope),
                    TestClock(testScheduler),
                    backgroundScope,
                )
            timer.start()
            assertEquals(RunState.Running(START + 2.minutes), timer.snapshot.run)
        }

    @Test
    fun `expiry is idempotent across the ticker, the wakeup and repeated calls`() =
        runTest {
            val timer = controller()
            timer.start()
            assertFalse(timer.expireIfDue())
            advance(25.minutes)
            assertFalse(timer.expireIfDue())
            assertFalse(timer.expireIfDue())
            assertEquals(1, alerts.ended.size)
            assertEquals(Phase.ShortBreak, timer.snapshot.phase)
        }

    @Test
    fun `new durations apply to a phase that hasn't started, not to a running one`() =
        runTest {
            val timer = controller()
            tags.setDurations(TagId(1), TimerDurations(work = 50.minutes, longBreakAfter = 3))
            runCurrent()
            assertEquals(50.minutes, timer.state.value.remaining)
            assertEquals(3, timer.state.value.workPhasesPerSet)

            timer.start()
            tags.setDurations(TagId(1), TimerDurations(work = 10.minutes))
            runCurrent()
            assertEquals(50.minutes, timer.snapshot.duration)
            assertEquals(listOf("start:deep:50"), blockerCommands())
        }

    @Test
    fun `every change is persisted`() =
        runTest {
            val timer = controller()
            timer.start()
            assertEquals(timer.snapshot, store.stored)
            timer.pause()
            assertEquals(timer.snapshot, store.stored)
            timer.skip()
            assertEquals(timer.snapshot, store.stored)
        }

    @Test
    fun `a phase-end alert is cleared when the next phase starts`() =
        runTest {
            val timer = controller()
            timer.start()
            advance(25.minutes)
            val before = alerts.clears
            timer.start()
            assertEquals(before + 1, alerts.clears)
        }

    @Test
    fun `a finished work phase is written to history once`() =
        runTest {
            val timer = controller()
            timer.start()
            advance(10.minutes)
            timer.pause()
            advance(1.hours)
            timer.start()
            advance(15.minutes)
            assertFalse(timer.expireIfDue())
            val row = sessions.rows.value.single()
            assertEquals(SessionType.Work, row.type)
            assertEquals(deep, row.profile)
            assertEquals(25, row.plannedMinutes)
            assertEquals(START, row.startedAt)
            assertEquals(START + 1.hours + 25.minutes, row.endedAt)
            assertEquals(SessionOutcome.Completed, row.outcome)
        }

    @Test
    fun `durations come from the selected tag, and the first tag when none is selected`() =
        runTest {
            val timer = controller()
            assertEquals(
                "Focus",
                timer.state.value.tag
                    ?.name,
            )
            assertEquals(25.minutes, timer.state.value.remaining)
            assertTrue(timer.selectTag(TagId(3)))
            runCurrent()
            assertEquals(TagId(3), settings.settings.value.selectedTagId)
            assertEquals(50.minutes, timer.state.value.remaining)
            timer.start()
            assertEquals(listOf("start:deep:50"), blockerCommands())
        }

    @Test
    fun `a selected tag deleted by a sync falls back to the first remaining tag`() =
        runTest {
            val timer = controller()
            assertTrue(timer.selectTag(TagId(3)))
            runCurrent()
            // A sync changes rows in place: the selection keeps its local id, now pointing at a deleted tag.
            tags.rows.value = tags.rows.value.map { if (it.id == TagId(3)) it.copy(deleted = true) else it }
            runCurrent()
            assertEquals(TagId(3), settings.settings.value.selectedTagId)
            assertEquals(
                "Focus",
                timer.state.value.tag
                    ?.name,
            )
            assertEquals(25.minutes, timer.state.value.remaining)
        }

    @Test
    fun `a tag can't be switched while a work phase is in progress, only before it or in a break`() =
        runTest {
            val timer = controller()
            timer.start()
            assertFalse(timer.selectTag(TagId(2)))
            timer.pause()
            assertFalse(timer.selectTag(TagId(2)))
            timer.start()
            advance(25.minutes)
            assertEquals(Phase.ShortBreak, timer.snapshot.phase)
            assertTrue(timer.selectTag(TagId(2)))
            runCurrent()
            // The staged break takes the new tag's length.
            assertEquals(10.minutes, timer.snapshot.duration)
        }

    @Test
    fun `a work phase is recorded with the tag it started with`() =
        runTest {
            settings.setSelectedTag(TagId(2))
            val timer = controller()
            timer.start()
            // Deleting the tag mid-phase falls back to another one, but the phase keeps its tag.
            tags.delete(TagId(2))
            runCurrent()
            assertEquals(
                "Focus",
                timer.state.value.tag
                    ?.name,
            )
            advance(20.minutes)
            assertEquals(
                TagId(2),
                sessions.rows.value
                    .single()
                    .tagId,
            )
            assertEquals(
                20,
                sessions.rows.value
                    .single()
                    .plannedMinutes,
            )
        }

    @Test
    fun `the started work phase's tag survives a restore`() =
        runTest {
            controller().apply {
                selectTag(TagId(3))
                runCurrent()
                start()
            }
            assertEquals(TagId(3), store.stored?.tagId)
        }

    @Test
    fun `breaks are not written to history`() =
        runTest {
            val timer = controller()
            timer.start()
            advance(25.minutes)
            timer.start()
            advance(5.minutes)
            assertEquals(Phase.Work, timer.snapshot.phase)
            assertEquals(listOf(SessionType.Work), sessions.rows.value.map { it.type })
        }

    @Test
    fun `skipping or resetting a started work phase records it as stopped`() =
        runTest {
            val timer = controller()
            timer.start()
            advance(5.minutes)
            timer.skip()
            timer.skip() // short break, not recorded
            timer.start()
            advance(3.minutes)
            timer.reset()
            assertEquals(listOf(SessionOutcome.Stopped, SessionOutcome.Stopped), sessions.rows.value.map { it.outcome })
            assertEquals(listOf(START + 5.minutes, START + 8.minutes), sessions.rows.value.map { it.endedAt })
        }

    @Test
    fun `skipping a work phase that never started records nothing`() =
        runTest {
            val timer = controller()
            timer.skip()
            assertTrue(sessions.rows.value.isEmpty())
        }

    @Test
    fun `a failed stop records nothing`() =
        runTest {
            val timer = controller()
            timer.start()
            blocker.nextResult = BlockResult.Refused
            timer.skip()
            assertTrue(sessions.rows.value.isEmpty())
        }

    @Test
    fun `a work phase that ended while the process was dead is recorded on restore`() =
        runTest {
            store.stored =
                TimerSnapshot(Phase.Work, 0, 25.minutes, RunState.Running(START - 5.minutes), startedAt = START - 30.minutes)
            controller()
            val row = sessions.rows.value.single()
            assertEquals(START - 30.minutes, row.startedAt)
            assertEquals(START - 5.minutes, row.endedAt)
        }

    @Test
    fun `a finished work phase records the apps used during it`() =
        runTest {
            usageReader.apps = listOf(AppTime("org.notes", "Notes", 10.minutes))
            val timer = controller()
            timer.start()
            advance(25.minutes)
            assertEquals(listOf(START to START + 25.minutes), usageReader.reads)
            val row = appUsage.usage.value.single()
            assertEquals(
                sessions.uidOf(
                    sessions.rows.value
                        .single()
                        .id,
                ),
                row.sessionUid,
            )
            assertEquals("org.notes", row.packageName)
        }

    @Test
    fun `a stopped work phase records its apps up to the stop`() =
        runTest {
            val timer = controller()
            timer.start()
            advance(10.minutes)
            timer.skip()
            runCurrent()
            assertEquals(listOf(START to START + 10.minutes), usageReader.reads)
        }

    @Test
    fun `apps are read only for work phases, not breaks`() =
        runTest {
            val timer = controller()
            timer.start()
            advance(25.minutes)
            timer.start()
            advance(5.minutes)
            timer.skip()
            runCurrent()
            assertEquals(1, usageReader.reads.size)
        }

    @Test
    fun `no apps are recorded without usage access`() =
        runTest {
            usageReader.permitted = false
            usageReader.apps = listOf(AppTime("org.notes", "Notes", 10.minutes))
            val timer = controller()
            timer.start()
            advance(25.minutes)
            assertTrue(appUsage.usage.value.isEmpty())
            assertEquals(1, sessions.rows.value.size)
        }

    @Test
    fun `a failing usage read loses the apps but not the work phase`() =
        runTest {
            usageReader.failure = IllegalStateException("usage service gone")
            val timer = controller()
            timer.start()
            advance(25.minutes)
            assertEquals(Phase.ShortBreak, timer.snapshot.phase)
            assertEquals(
                SessionOutcome.Completed,
                sessions.rows.value
                    .single()
                    .outcome,
            )
            assertTrue(appUsage.usage.value.isEmpty())
        }

    @Test
    fun `the selected activity is written into the recorded work phase`() =
        runTest {
            val kobo = activities.add("Reading on Kobo")
            val timer = controller()
            timer.selectActivity(kobo)
            runCurrent()
            assertEquals(
                kobo,
                timer.state.value.activity
                    ?.id,
            )
            timer.start()
            advance(25.minutes)
            assertEquals(
                kobo,
                sessions.rows.value
                    .single()
                    .activityId,
            )
        }

    @Test
    fun `the activity stays selected for the next work phases until cleared`() =
        runTest {
            val kobo = activities.add("Reading on Kobo")
            val timer = controller()
            timer.selectActivity(kobo)
            runCurrent()
            timer.start()
            advance(25.minutes)
            timer.skip()
            timer.start()
            advance(25.minutes)
            timer.selectActivity(null)
            runCurrent()
            timer.skip()
            timer.start()
            advance(25.minutes)
            assertEquals(listOf(kobo, kobo, null), sessions.rows.value.map { it.activityId })
        }

    @Test
    fun `the activity can change during a work phase and the latest one is recorded`() =
        runTest {
            val kobo = activities.add("Reading on Kobo")
            val paper = activities.add("Paper notes")
            val timer = controller()
            timer.selectActivity(kobo)
            timer.start()
            advance(10.minutes)
            timer.selectActivity(paper)
            runCurrent()
            assertEquals(Phase.Work, timer.snapshot.phase)
            advance(15.minutes)
            assertEquals(
                paper,
                sessions.rows.value
                    .single()
                    .activityId,
            )
        }

    @Test
    fun `a deleted activity is no longer selected`() =
        runTest {
            val kobo = activities.add("Reading on Kobo")
            val timer = controller()
            timer.selectActivity(kobo)
            runCurrent()
            activities.delete(kobo)
            runCurrent()
            assertNull(timer.state.value.activity)
            timer.start()
            advance(25.minutes)
            assertNull(
                sessions.rows.value
                    .single()
                    .activityId,
            )
        }
}
