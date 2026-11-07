// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer.ui

import app.hush.activities.FakeActivityRepository
import app.hush.blocker.BlockProfile
import app.hush.blocker.BlockResult
import app.hush.blocker.ProfileId
import app.hush.blocker.SessionStatus
import app.hush.blocker.fake.FakeBlocker
import app.hush.blocklists.FakeBlockListRepository
import app.hush.core.DeviceFeatures
import app.hush.history.FakeSessionRepository
import app.hush.settings.FakeSettingsRepository
import app.hush.tags.FakeTagRepository
import app.hush.tags.TagId
import app.hush.timer.FakePhaseAlerts
import app.hush.timer.FakeTimerStateStore
import app.hush.timer.FakeWakeupScheduler
import app.hush.timer.Phase
import app.hush.timer.TestClock
import app.hush.timer.TimerController
import app.hush.timer.TimerDurations
import app.hush.usage.FakeAppUsageReader
import app.hush.usage.FakeAppUsageRepository
import app.hush.usage.WorkUsageRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {
    private val deep = ProfileId("deep")
    private val blocker = FakeBlocker()
    private val settings = FakeSettingsRepository()
    private val blockLists = FakeBlockListRepository()
    private val tags = FakeTagRepository()
    private val activities = FakeActivityRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(features: DeviceFeatures = DeviceFeatures()): TimerViewModel {
        val controller =
            TimerController(
                blocker,
                settings,
                blockLists,
                tags,
                activities,
                FakeTimerStateStore(),
                FakeWakeupScheduler(),
                FakePhaseAlerts(),
                FakeSessionRepository(),
                WorkUsageRecorder(FakeAppUsageReader(), FakeAppUsageRepository(), backgroundScope),
                TestClock(testScheduler),
                backgroundScope,
            )
        runCurrent()
        return TimerViewModel(controller, blockLists, tags, activities, features).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }
    }

    private fun TestScope.act(
        vm: TimerViewModel,
        action: TimerAction,
    ) {
        vm.onAction(action)
        runCurrent()
    }

    @Test
    fun `shows the first work phase ready to start`() =
        runTest {
            val state = viewModel().uiState.value
            assertEquals(Phase.Work, state.phase)
            assertEquals(TimerStatus.Ready, state.status)
            assertEquals(25.minutes, state.remaining)
            assertEquals(1, state.workPhase)
            assertEquals(4, state.workPhasesPerSet)
            assertFalse(state.canReset)
            assertFalse(state.blockingOff)
        }

    @Test
    fun `start, pause and resume show the matching status and progress`() =
        runTest {
            val vm = viewModel()
            act(vm, TimerAction.Start)
            assertEquals(TimerStatus.Running, vm.uiState.value.status)
            advanceTimeBy(5.minutes)
            runCurrent()
            assertEquals(0.2f, vm.uiState.value.progress, 0.001f)
            assertTrue(vm.uiState.value.canReset)
            act(vm, TimerAction.Pause)
            assertEquals(TimerStatus.Paused, vm.uiState.value.status)
        }

    @Test
    fun `pause, skip and reset are hidden when the profile can't stop early`() =
        runTest {
            blocker.profiles = listOf(BlockProfile(deep, "Deep work", canStopEarly = false))
            val vm = viewModel()
            assertTrue(vm.uiState.value.canStopEarly)
            act(vm, TimerAction.Start)
            assertFalse(vm.uiState.value.canStopEarly)
            advanceTimeBy(25.minutes)
            runCurrent()
            // The break isn't blocked, so it can be paused and skipped again.
            assertEquals(Phase.ShortBreak, vm.uiState.value.phase)
            assertTrue(vm.uiState.value.canStopEarly)
        }

    @Test
    fun `without a deep work profile it says blocking is off and still runs`() =
        runTest {
            blockLists.setApps(deep, emptySet())
            val vm = viewModel()
            assertTrue(vm.uiState.value.blockingOff)
            act(vm, TimerAction.Start)
            assertEquals(TimerStatus.Running, vm.uiState.value.status)
            assertNull(vm.uiState.value.notice)
        }

    @Test
    fun `on a device that can't block it says blocking is phone-only, not off, and runs unblocked`() =
        runTest {
            blockLists.setApps(deep, emptySet())
            val vm = viewModel(DeviceFeatures(blocking = false, lockScreen = false))
            assertTrue(vm.uiState.value.blockingPhoneOnly)
            assertFalse(vm.uiState.value.blockingOff)
            act(vm, TimerAction.Start)
            assertEquals(TimerStatus.Running, vm.uiState.value.status)
            assertNull(vm.uiState.value.notice)
            assertTrue(blocker.calls.none { it.startsWith("start") })
        }

    @Test
    fun `denied start becomes a setup notice that the UI acknowledges`() =
        runTest {
            val vm = viewModel()
            blocker.nextResult = BlockResult.Denied
            act(vm, TimerAction.Start)
            assertEquals(TimerNotice.NeedsSetup, vm.uiState.value.notice)
            assertEquals(TimerStatus.Ready, vm.uiState.value.status)
            act(vm, TimerAction.DismissNotice)
            assertNull(vm.uiState.value.notice)
        }

    @Test
    fun `refused start explains and the timer stays ready`() =
        runTest {
            val vm = viewModel()
            blocker.nextResult = BlockResult.Refused
            act(vm, TimerAction.Start)
            assertEquals(TimerNotice.Refused, vm.uiState.value.notice)
            assertEquals(TimerStatus.Ready, vm.uiState.value.status)
        }

    @Test
    fun `connection lost with no session explains and doesn't retry`() =
        runTest {
            val vm = viewModel()
            blocker.nextResult = BlockResult.ConnectionLost
            blocker.session = SessionStatus.None
            act(vm, TimerAction.Start)
            assertEquals(TimerNotice.Unreachable, vm.uiState.value.notice)
            assertEquals(1, blocker.calls.count { it.startsWith("start") })
        }

    @Test
    fun `the next command clears an old notice`() =
        runTest {
            val vm = viewModel()
            blocker.nextResult = BlockResult.Refused
            act(vm, TimerAction.Start)
            blocker.nextResult = BlockResult.Ok
            act(vm, TimerAction.Start)
            assertNull(vm.uiState.value.notice)
            assertEquals(TimerStatus.Running, vm.uiState.value.status)
        }

    @Test
    fun `a break shows the work phase it follows`() =
        runTest {
            val vm = viewModel()
            act(vm, TimerAction.Skip)
            assertEquals(Phase.ShortBreak, vm.uiState.value.phase)
            assertEquals(1, vm.uiState.value.workPhase)
            act(vm, TimerAction.Skip)
            assertEquals(2, vm.uiState.value.workPhase)
            act(vm, TimerAction.Reset)
            assertEquals(1, vm.uiState.value.workPhase)
            assertFalse(vm.uiState.value.canReset)
        }

    @Test
    fun `the tag chip shows the selected tag and switching is locked during a work phase`() =
        runTest {
            val vm = viewModel()
            assertEquals(
                "Focus",
                vm.uiState.value.tag
                    ?.name,
            )
            assertEquals(
                listOf("Focus", "Light", "Deep"),
                vm.uiState.value.tags
                    .map { it.name },
            )
            assertTrue(vm.uiState.value.canSwitchTag)
            act(vm, TimerAction.SelectTag(TagId(2)))
            assertEquals(
                "Light",
                vm.uiState.value.tag
                    ?.name,
            )
            assertEquals(20.minutes, vm.uiState.value.remaining)
            act(vm, TimerAction.Start)
            assertFalse(vm.uiState.value.canSwitchTag)
            act(vm, TimerAction.SelectTag(TagId(3)))
            assertEquals(
                "Light",
                vm.uiState.value.tag
                    ?.name,
            )
        }

    @Test
    fun `up next shows the phase staged after this one with its length`() =
        runTest {
            tags.setDurations(TagId(1), TimerDurations(shortBreak = 7.minutes, longBreakAfter = 2))
            val vm = viewModel()
            assertEquals(Phase.ShortBreak, vm.uiState.value.nextPhase)
            assertEquals(7.minutes, vm.uiState.value.nextDuration)
            act(vm, TimerAction.Skip)
            assertEquals(Phase.Work, vm.uiState.value.nextPhase)
            assertEquals(25.minutes, vm.uiState.value.nextDuration)
            act(vm, TimerAction.Skip)
            // The second work phase of a two-phase set ends in the long break.
            assertEquals(Phase.LongBreak, vm.uiState.value.nextPhase)
            assertEquals(15.minutes, vm.uiState.value.nextDuration)
        }

    @Test
    fun `the doing chip is empty by default and lists the activities to pick`() =
        runTest {
            activities.add("Reading on Kobo")
            val vm = viewModel()
            assertNull(vm.uiState.value.activity)
            assertEquals(
                listOf("Reading on Kobo"),
                vm.uiState.value.activities
                    .map { it.name },
            )
        }

    @Test
    fun `adding an activity on the timer picks it, and none clears it`() =
        runTest {
            val vm = viewModel()
            act(vm, TimerAction.AddActivity("  Reading on Kobo "))
            assertEquals(
                "Reading on Kobo",
                vm.uiState.value.activity
                    ?.name,
            )
            act(vm, TimerAction.SelectActivity(null))
            assertNull(vm.uiState.value.activity)
            assertNull(settings.settings.value.selectedActivityId)
        }

    @Test
    fun `an activity can be picked while a work phase runs`() =
        runTest {
            val kobo = activities.add("Reading on Kobo")
            val vm = viewModel()
            act(vm, TimerAction.Start)
            act(vm, TimerAction.SelectActivity(kobo))
            assertEquals(TimerStatus.Running, vm.uiState.value.status)
            assertEquals(
                kobo,
                vm.uiState.value.activity
                    ?.id,
            )
        }

    @Test
    fun `a blank activity name adds nothing`() =
        runTest {
            val vm = viewModel()
            act(vm, TimerAction.AddActivity("   "))
            assertTrue(activities.rows.value.isEmpty())
        }
}
