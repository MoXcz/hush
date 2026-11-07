// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.settings.ui.onboarding

import androidx.compose.runtime.Immutable
import app.hush.blocklists.BlockList

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Checking,
    val lists: List<BlockList> = emptyList(),
    val problem: OnboardingProblem? = null,
)

/** Explain and turn the blocker on, then pick apps for each list. Decided from the blocker's state. */
enum class OnboardingStep { Checking, TurnOn, PickApps }

enum class OnboardingProblem { CouldNotOpen }

sealed interface OnboardingAction {
    /** Re-reads the blocker state. Sent on every resume, since the user turns it on in system settings. */
    data object Refresh : OnboardingAction

    data object OpenSystemSettings : OnboardingAction

    data object DismissProblem : OnboardingAction
}
