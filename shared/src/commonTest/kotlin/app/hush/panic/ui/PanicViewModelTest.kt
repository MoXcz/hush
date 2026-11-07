// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.panic.ui

import app.hush.blocker.BlockProfile
import app.hush.blocker.BlockResult
import app.hush.blocker.ProfileId
import app.hush.blocker.fake.FakeBlocker
import app.hush.blocklists.FakeBlockListRepository
import app.hush.detox.DetoxController
import app.hush.detox.FakeLevelAlerts
import app.hush.detox.LevelOutcome
import app.hush.history.FakeSessionRepository
import app.hush.settings.FakeSettingsRepository
import app.hush.timer.FakeWakeupScheduler
import app.hush.timer.TestClock
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PanicViewModelTest {
    private val detox = ProfileId("detox")
    private val blocker = FakeBlocker(profiles = listOf(BlockProfile(detox, "Detox", true)))
    private val settings = FakeSettingsRepository()
    private val blockLists = FakeBlockListRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.controller(): DetoxController {
        val clock = TestClock(testScheduler)
        return DetoxController(
            blocker,
            settings,
            blockLists,
            FakeSessionRepository(),
            FakeWakeupScheduler(),
            FakeLevelAlerts(),
            clock,
            backgroundScope,
        ).also { runCurrent() }
    }

    private fun TestScope.viewModel(controller: DetoxController): PanicViewModel =
        PanicViewModel(controller, TestClock(testScheduler)).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }

    private fun TestScope.advance(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    @Test
    fun `the exercise lasts 60 seconds and walks through the five senses`() =
        runTest {
            val controller = controller().also { it.startAttempt() }
            val vm = viewModel(controller)
            assertEquals(60.seconds, vm.uiState.value.remaining)
            assertEquals(GroundingStep.See, vm.uiState.value.step)
            val steps = mutableListOf(vm.uiState.value.step)
            repeat(60) {
                advance(1.seconds)
                if (steps.last() != vm.uiState.value.step) steps += vm.uiState.value.step
            }
            assertEquals(GroundingStep.entries.toList(), steps.toList())
            assertTrue(vm.uiState.value.done)
            assertEquals(Duration.ZERO, vm.uiState.value.remaining)
        }

    @Test
    fun `end session is ignored until the exercise is over`() =
        runTest {
            val controller = controller().also { it.startAttempt() }
            val vm = viewModel(controller)
            advance(30.seconds)
            vm.onAction(PanicAction.EndSession)
            runCurrent()
            assertFalse("stop" in blocker.calls)
            assertNotNull(controller.state.value.attempt)
        }

    @Test
    fun `end session after the exercise stops the attempt and closes`() =
        runTest {
            val controller = controller().also { it.startAttempt() }
            val vm = viewModel(controller)
            advance(60.seconds)
            vm.onAction(PanicAction.EndSession)
            runCurrent()
            assertTrue("stop" in blocker.calls)
            assertEquals(LevelOutcome.EndedEarly, controller.state.value.lastOutcome)
            assertTrue(vm.uiState.value.closed)
        }

    @Test
    fun `a profile that can't stop early gets an explanation instead`() =
        runTest {
            blocker.profiles = listOf(BlockProfile(detox, "Detox", canStopEarly = false))
            val controller = controller().also { it.startAttempt() }
            val vm = viewModel(controller)
            assertFalse(vm.uiState.value.canStopEarly)
        }

    @Test
    fun `a refused stop keeps the attempt and switches to the explanation`() =
        runTest {
            val controller = controller().also { it.startAttempt() }
            val vm = viewModel(controller)
            advance(60.seconds)
            blocker.nextResult = BlockResult.Refused
            vm.onAction(PanicAction.EndSession)
            runCurrent()
            assertFalse(vm.uiState.value.canStopEarly)
            assertFalse(vm.uiState.value.closed)
            assertEquals(PanicNotice.CantStopEarly, vm.uiState.value.notice)
        }

    @Test
    fun `an unreachable blocker is reported and nothing ends`() =
        runTest {
            val controller = controller().also { it.startAttempt() }
            val vm = viewModel(controller)
            advance(60.seconds)
            blocker.nextResult = BlockResult.ConnectionLost
            vm.onAction(PanicAction.EndSession)
            runCurrent()
            assertEquals(PanicNotice.Unreachable, vm.uiState.value.notice)
            assertNotNull(controller.state.value.attempt)
            vm.onAction(PanicAction.DismissNotice)
            assertNull(vm.uiState.value.notice)
        }

    @Test
    fun `the screen closes when the attempt passes meanwhile`() =
        runTest {
            val controller = controller()
            val vm = viewModel(controller)
            assertTrue(vm.uiState.value.closed)
        }
}
