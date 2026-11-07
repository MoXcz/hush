// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.settings.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hush.blocker.Availability
import app.hush.blocker.Blocker
import app.hush.blocker.BlockerSetup
import app.hush.blocklists.BlockListRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val blocker: Blocker,
    private val setup: BlockerSetup,
    blockLists: BlockListRepository,
) : ViewModel() {
    private data class Progress(
        val step: OnboardingStep = OnboardingStep.Checking,
        val problem: OnboardingProblem? = null,
    )

    private val progress = MutableStateFlow(Progress())
    private var refreshJob: Job? = null

    val uiState: StateFlow<OnboardingUiState> =
        combine(progress, blockLists.lists) { progress, lists ->
            OnboardingUiState(step = progress.step, lists = lists, problem = progress.problem)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    fun onAction(action: OnboardingAction) {
        when (action) {
            OnboardingAction.Refresh -> refresh()
            OnboardingAction.OpenSystemSettings -> if (!setup.openSystemSettings()) report(OnboardingProblem.CouldNotOpen)
            OnboardingAction.DismissProblem -> progress.update { it.copy(problem = null) }
        }
    }

    private fun report(problem: OnboardingProblem) {
        progress.update { it.copy(problem = problem) }
    }

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                val step =
                    when (blocker.availability()) {
                        Availability.Ready -> OnboardingStep.PickApps
                        Availability.NeedsApproval, Availability.Unsupported -> OnboardingStep.TurnOn
                    }
                progress.update { it.copy(step = step) }
            }
    }
}
