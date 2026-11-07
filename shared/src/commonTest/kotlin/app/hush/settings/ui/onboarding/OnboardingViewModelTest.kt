// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.settings.ui.onboarding

import app.hush.blocker.Availability
import app.hush.blocker.fake.FakeBlocker
import app.hush.blocklists.FakeBlockListRepository
import app.hush.blocklists.ProfileRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val blocker = FakeBlocker(availability = Availability.NeedsApproval)
    private val blockLists = FakeBlockListRepository(deepWork = emptySet(), detox = emptySet())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): OnboardingViewModel =
        OnboardingViewModel(blocker, blocker, blockLists).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }

    @Test
    fun `starts checking until the first refresh`() =
        runTest {
            assertEquals(OnboardingStep.Checking, viewModel().uiState.value.step)
        }

    @Test
    fun `a blocker that is off asks to turn it on`() =
        runTest {
            val vm = viewModel()
            vm.onAction(OnboardingAction.Refresh)
            assertEquals(OnboardingStep.TurnOn, vm.uiState.value.step)
        }

    @Test
    fun `turning it on in system settings moves on to the lists on the next refresh`() =
        runTest {
            val vm = viewModel()
            vm.onAction(OnboardingAction.Refresh)
            vm.onAction(OnboardingAction.OpenSystemSettings)
            assertEquals(OnboardingStep.TurnOn, vm.uiState.value.step)
            blocker.availability = Availability.Ready
            vm.onAction(OnboardingAction.Refresh)
            assertEquals(OnboardingStep.PickApps, vm.uiState.value.step)
            assertEquals(
                ProfileRole.entries.map { it.profileId },
                vm.uiState.value.lists
                    .map { it.id },
            )
        }

    @Test
    fun `the lists follow app picks made elsewhere`() =
        runTest {
            blocker.availability = Availability.Ready
            val vm = viewModel()
            vm.onAction(OnboardingAction.Refresh)
            blockLists.setApps(ProfileRole.Detox.profileId, setOf("com.example.video"))
            assertEquals(
                setOf("com.example.video"),
                vm.uiState.value.lists[1]
                    .packages,
            )
        }

    @Test
    fun `settings that can't open are reported until dismissed`() =
        runTest {
            blocker.settingsOpen = false
            val vm = viewModel()
            vm.onAction(OnboardingAction.OpenSystemSettings)
            assertEquals(OnboardingProblem.CouldNotOpen, vm.uiState.value.problem)
            vm.onAction(OnboardingAction.DismissProblem)
            assertNull(vm.uiState.value.problem)
        }
}
