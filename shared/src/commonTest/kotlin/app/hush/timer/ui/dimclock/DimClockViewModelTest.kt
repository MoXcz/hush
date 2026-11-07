// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer.ui.dimclock

import app.hush.activities.FakeActivityRepository
import app.hush.blocker.fake.FakeBlocker
import app.hush.blocklists.FakeBlockListRepository
import app.hush.history.FakeSessionRepository
import app.hush.settings.FakeSettingsRepository
import app.hush.settings.Settings
import app.hush.tags.FakeTagRepository
import app.hush.timer.FakePhaseAlerts
import app.hush.timer.FakeTimerStateStore
import app.hush.timer.FakeWakeupScheduler
import app.hush.timer.TestClock
import app.hush.timer.TestClock.Companion.START
import app.hush.timer.TimerController
import app.hush.usage.FakeAppUsageReader
import app.hush.usage.FakeAppUsageRepository
import app.hush.usage.WorkUsageRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DimClockViewModelTest {
    private val settings = FakeSettingsRepository(Settings())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.controller(): TimerController =
        TimerController(
            FakeBlocker(),
            settings,
            FakeBlockListRepository(),
            FakeTagRepository(),
            FakeActivityRepository(),
            FakeTimerStateStore(),
            FakeWakeupScheduler(),
            FakePhaseAlerts(),
            FakeSessionRepository(),
            WorkUsageRecorder(FakeAppUsageReader(), FakeAppUsageRepository(), backgroundScope),
            TestClock(testScheduler),
            backgroundScope,
        ).also { runCurrent() }

    private fun TestScope.viewModel(controller: TimerController): DimClockViewModel =
        DimClockViewModel(controller, settings, TestClock(testScheduler)).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }

    @Test
    fun `shows the countdown and the time of day`() =
        runTest {
            val timer = controller()
            timer.start()
            val vm = viewModel(timer)
            advanceTimeBy(5.minutes)
            runCurrent()
            assertEquals(20.minutes, vm.uiState.value.remaining)
            assertEquals(0.2f, vm.uiState.value.progress, 0.001f)
            assertEquals(START + 5.minutes, vm.uiState.value.now)
            assertFalse(vm.uiState.value.exit)
        }

    @Test
    fun `exits by itself when the phase ends`() =
        runTest {
            val timer = controller()
            timer.start()
            val vm = viewModel(timer)
            advanceTimeBy(25.minutes - 1.seconds)
            runCurrent()
            assertFalse(vm.uiState.value.exit)
            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(vm.uiState.value.exit)
        }

    @Test
    fun `follows the lock screen setting`() =
        runTest {
            val vm = viewModel(controller())
            assertFalse(vm.uiState.value.showWhenLocked)
            settings.setDimClockShowWhenLocked(true)
            runCurrent()
            assertTrue(vm.uiState.value.showWhenLocked)
        }

    @Test
    fun `minute ticks land on wall-clock minute boundaries`() =
        runTest {
            advanceTimeBy(20.seconds)
            val ticks = minuteTicks(TestClock(testScheduler)).take(3).toList()
            assertEquals(listOf(START + 20.seconds, START + 1.minutes, START + 2.minutes), ticks)
        }
}
