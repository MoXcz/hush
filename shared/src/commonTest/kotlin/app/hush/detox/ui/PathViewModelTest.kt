// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.detox.ui

import app.hush.blocker.BlockProfile
import app.hush.blocker.BlockResult
import app.hush.blocker.ProfileId
import app.hush.blocker.SessionStatus
import app.hush.blocker.fake.FakeBlocker
import app.hush.blocklists.FakeBlockListRepository
import app.hush.core.DeviceFeatures
import app.hush.detox.DetoxAttempt
import app.hush.detox.DetoxController
import app.hush.detox.DetoxState
import app.hush.detox.FakeLevelAlerts
import app.hush.detox.LevelOutcome
import app.hush.history.FakeSessionRepository
import app.hush.history.Session
import app.hush.history.SessionId
import app.hush.history.SessionOutcome
import app.hush.history.SessionType
import app.hush.settings.FakeSettingsRepository
import app.hush.settings.Settings
import app.hush.timer.FakeWakeupScheduler
import app.hush.timer.TestClock
import app.hush.timer.TestClock.Companion.START
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class PathViewModelTest {
    private val detox = ProfileId("detox")
    private val blocker = FakeBlocker(profiles = listOf(BlockProfile(detox, "Detox", true)))
    private val settings = FakeSettingsRepository(Settings(detoxGoal = 2.hours))
    private val blockLists = FakeBlockListRepository()
    private val sessions = FakeSessionRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(features: DeviceFeatures = DeviceFeatures()): PathViewModel {
        val controller =
            DetoxController(
                blocker,
                settings,
                blockLists,
                sessions,
                FakeWakeupScheduler(),
                FakeLevelAlerts(),
                TestClock(testScheduler),
                backgroundScope,
            )
        runCurrent()
        return PathViewModel(controller, settings, blockLists, sessions, features).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }
    }

    private fun TestScope.act(
        vm: PathViewModel,
        action: PathAction,
    ) {
        vm.onAction(action)
        runCurrent()
    }

    @Test
    fun `shows the ladder with the first level current`() =
        runTest {
            val state = viewModel().uiState.value
            assertEquals(
                listOf(
                    LevelItem(60, LevelStatus.Current, selected = true, selectable = true),
                    LevelItem(90, LevelStatus.Locked),
                    LevelItem(120, LevelStatus.Locked),
                ),
                state.levels,
            )
            assertEquals(60, state.currentLevelMinutes)
            assertNull(state.active)
            assertFalse(state.noProfile)
        }

    @Test
    fun `starting shows the running attempt with its countdown`() =
        runTest {
            val vm = viewModel()
            act(vm, PathAction.StartAttempt)
            val active = assertNotNull(vm.uiState.value.active)
            assertEquals(60, active.levelMinutes)
            assertEquals(1.hours, active.remaining)
            advanceTimeBy(15.minutes)
            runCurrent()
            assertEquals(
                45.minutes,
                vm.uiState.value.active
                    ?.remaining,
            )
            assertEquals(
                0.25f,
                vm.uiState.value.active
                    ?.progress,
            )
        }

    @Test
    fun `a passed level is marked and the next one becomes current`() =
        runTest {
            val vm = viewModel()
            act(vm, PathAction.StartAttempt)
            advanceTimeBy(1.hours)
            runCurrent()
            val state = vm.uiState.value
            assertEquals(LevelOutcome.Passed, state.outcome)
            assertEquals(listOf(LevelStatus.Passed, LevelStatus.Current, LevelStatus.Locked), state.levels.map { it.status })
            act(vm, PathAction.DismissOutcome)
            assertNull(vm.uiState.value.outcome)
        }

    @Test
    fun `resume check fails a level stopped inside the blocker`() =
        runTest {
            val vm = viewModel()
            act(vm, PathAction.StartAttempt)
            blocker.session = SessionStatus.None
            act(vm, PathAction.Check)
            assertEquals(LevelOutcome.StoppedInBlocker, vm.uiState.value.outcome)
            assertNull(vm.uiState.value.active)
        }

    @Test
    fun `an empty detox list asks for setup`() =
        runTest {
            blockLists.setApps(detox, emptySet())
            assertTrue(viewModel().uiState.value.noProfile)
        }

    @Test
    fun `on a device that can't block the path is read-only`() =
        runTest {
            settings.settings.value = Settings(detoxGoal = 2.hours)
            assertFalse(viewModel().uiState.value.phoneOnly)
            val state = viewModel(DeviceFeatures(blocking = false)).uiState.value
            assertTrue(state.phoneOnly)
            assertEquals(listOf(60, 90, 120), state.levels.map { it.minutes })
        }

    @Test
    fun `blocker problems become notices`() =
        runTest {
            val vm = viewModel()
            blocker.nextResult = BlockResult.Denied
            act(vm, PathAction.StartAttempt)
            assertEquals(PathNotice.NeedsSetup, vm.uiState.value.notice)
            blocker.nextResult = BlockResult.Refused
            act(vm, PathAction.StartAttempt)
            assertEquals(PathNotice.Refused, vm.uiState.value.notice)
            act(vm, PathAction.DismissNotice)
            assertNull(vm.uiState.value.notice)
        }

    @Test
    fun `a complete path keeps the goal level to repeat`() {
        val state = toUiState(DetoxState(), Settings(detoxGoal = 90.minutes), longestPassedMinutes = 90)
        assertTrue(state.complete)
        assertEquals(90, state.currentLevelMinutes)
        assertEquals(listOf(LevelStatus.Passed, LevelStatus.Passed), state.levels.map { it.status })
    }

    @Test
    fun `a running attempt keeps its level after the goal changes`() {
        val attempt = DetoxAttempt(SessionId(1), 120, START, canStopEarly = false)
        val state = toUiState(DetoxState(attempt, remaining = 1.hours), Settings(detoxGoal = 60.minutes), longestPassedMinutes = 90)
        assertEquals(120, state.currentLevelMinutes)
        assertEquals(0.5f, state.active?.progress)
        assertEquals(false, state.active?.canStopEarly)
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
    fun `only passed levels and the next one are selectable`() =
        runTest {
            passed(60)
            val state = viewModel().uiState.value
            assertEquals(listOf(true, true, false), state.levels.map { it.selectable })
            assertEquals(listOf(false, true, false), state.levels.map { it.selected })
        }

    @Test
    fun `choosing a passed level makes Start attempt it`() =
        runTest {
            passed(60)
            val vm = viewModel()
            act(vm, PathAction.SelectLevel(60))
            val state = vm.uiState.value
            assertEquals(60, state.currentLevelMinutes)
            assertEquals(60, state.chosenLevelMinutes)
            assertEquals(listOf(true, false, false), state.levels.map { it.selected })
            assertEquals(LevelStatus.Current, state.levels[1].status)
            act(vm, PathAction.StartAttempt)
            assertEquals(listOf("start:detox:60"), blocker.calls.filter { it.startsWith("start") })
            assertEquals(
                60,
                vm.uiState.value.active
                    ?.levelMinutes,
            )
        }

    @Test
    fun `tapping the next level goes back to the default`() =
        runTest {
            passed(60)
            val vm = viewModel()
            act(vm, PathAction.SelectLevel(60))
            act(vm, PathAction.SelectLevel(90))
            assertNull(vm.uiState.value.chosenLevelMinutes)
            assertEquals(90, vm.uiState.value.currentLevelMinutes)
        }

    @Test
    fun `a locked level can't be chosen`() =
        runTest {
            passed(60)
            val vm = viewModel()
            act(vm, PathAction.SelectLevel(120))
            assertNull(vm.uiState.value.chosenLevelMinutes)
            assertEquals(90, vm.uiState.value.currentLevelMinutes)
        }

    @Test
    fun `the choice resets once the attempt ends`() =
        runTest {
            passed(60)
            val vm = viewModel()
            act(vm, PathAction.SelectLevel(60))
            act(vm, PathAction.StartAttempt)
            advanceTimeBy(1.hours)
            runCurrent()
            val state = vm.uiState.value
            assertEquals(LevelOutcome.PassedAgain, state.outcome)
            assertNull(state.chosenLevelMinutes)
            assertEquals(90, state.currentLevelMinutes)
            assertEquals(listOf(LevelStatus.Passed, LevelStatus.Current, LevelStatus.Locked), state.levels.map { it.status })
        }

    @Test
    fun `a failed start keeps the choice for the retry`() =
        runTest {
            passed(60)
            val vm = viewModel()
            act(vm, PathAction.SelectLevel(60))
            blocker.nextResult = BlockResult.Refused
            act(vm, PathAction.StartAttempt)
            assertEquals(60, vm.uiState.value.chosenLevelMinutes)
        }

    @Test
    fun `the ladder isn't selectable while an attempt runs`() =
        runTest {
            passed(60)
            val vm = viewModel()
            act(vm, PathAction.StartAttempt)
            act(vm, PathAction.SelectLevel(60))
            val state = vm.uiState.value
            assertTrue(state.levels.none { it.selectable })
            assertEquals(90, state.currentLevelMinutes)
            advanceTimeBy(90.minutes)
            runCurrent()
            assertNull(vm.uiState.value.chosenLevelMinutes)
            assertEquals(120, vm.uiState.value.currentLevelMinutes)
        }

    @Test
    fun `the ladder isn't selectable on a device that can't block`() =
        runTest {
            passed(60)
            val vm = viewModel(DeviceFeatures(blocking = false))
            act(vm, PathAction.SelectLevel(60))
            val state = vm.uiState.value
            assertTrue(state.levels.none { it.selectable })
            assertNull(state.chosenLevelMinutes)
            assertEquals(90, state.currentLevelMinutes)
        }

    @Test
    fun `after the goal is reached every level is selectable`() {
        val state = toUiState(DetoxState(), Settings(detoxGoal = 2.hours), longestPassedMinutes = 120, selectedMinutes = 60)
        assertTrue(state.levels.all { it.selectable })
        assertEquals(60, state.chosenLevelMinutes)
    }

    @Test
    fun `a running shorter attempt shows its own length and the next level stays next`() {
        val attempt = DetoxAttempt(SessionId(1), 60, START, canStopEarly = true)
        val state = toUiState(DetoxState(attempt, remaining = 30.minutes), Settings(detoxGoal = 2.hours), longestPassedMinutes = 90)
        assertEquals(60, state.currentLevelMinutes)
        assertEquals(60, state.active?.levelMinutes)
        assertEquals(listOf(true, false, false), state.levels.map { it.selected })
        assertEquals(listOf(LevelStatus.Passed, LevelStatus.Passed, LevelStatus.Current), state.levels.map { it.status })
    }
}
