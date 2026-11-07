// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.settings.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hush.blocker.ProfileId
import app.hush.blocklists.BlockList
import app.hush.blocklists.ui.BlockListRows
import app.hush.resources.Res
import app.hush.resources.onboarding_done
import app.hush.resources.onboarding_explain
import app.hush.resources.onboarding_how_to
import app.hush.resources.onboarding_open_settings
import app.hush.resources.onboarding_pick_apps
import app.hush.resources.onboarding_problem_could_not_open
import app.hush.resources.onboarding_problem_dismiss
import app.hush.resources.onboarding_title
import app.hush.ui.AnimatedNotice
import app.hush.ui.ScreenTitle
import app.hush.ui.ScreenTopBar
import app.hush.ui.theme.HushTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingRoute(
    onOpenList: (ProfileId) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(OnboardingAction.Refresh)
        onPauseOrDispose {}
    }
    OnboardingScreen(state = state, onAction = viewModel::onAction, onOpenList = onOpenList, onDone = onDone, modifier = modifier)
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    onOpenList: (ProfileId) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    // Background first, so it also fills the space the caller pads out for the bottom bar.
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).then(modifier)) {
        ScreenTopBar(title = { ScreenTitle(stringResource(Res.string.onboarding_title)) })
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    (
                        fadeIn(
                            motionScheme.defaultEffectsSpec(),
                        ) + slideInHorizontally(motionScheme.defaultSpatialSpec()) { it / 4 }
                    ) togetherWith
                        fadeOut(motionScheme.fastEffectsSpec())
                },
                modifier = Modifier.fillMaxWidth(),
            ) { step ->
                StepContent(step = step, lists = state.lists, onAction = onAction, onOpenList = onOpenList, onDone = onDone)
            }
            AnimatedNotice(
                notice = state.problem,
                text = { problem ->
                    when (problem) {
                        OnboardingProblem.CouldNotOpen -> stringResource(Res.string.onboarding_problem_could_not_open)
                    }
                },
                actionLabel = stringResource(Res.string.onboarding_problem_dismiss),
                onAction = { onAction(OnboardingAction.DismissProblem) },
                error = true,
            )
        }
    }
}

@Composable
private fun StepContent(
    step: OnboardingStep,
    lists: List<BlockList>,
    onAction: (OnboardingAction) -> Unit,
    onOpenList: (ProfileId) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (step) {
            OnboardingStep.Checking -> {
                LoadingIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 32.dp))
            }

            OnboardingStep.TurnOn -> {
                Body(stringResource(Res.string.onboarding_explain))
                Body(stringResource(Res.string.onboarding_how_to))
                PrimaryButton(stringResource(Res.string.onboarding_open_settings)) { onAction(OnboardingAction.OpenSystemSettings) }
            }

            OnboardingStep.PickApps -> {
                Body(stringResource(Res.string.onboarding_pick_apps))
                BlockListRows(lists = lists, onOpen = onOpenList)
                PrimaryButton(stringResource(Res.string.onboarding_done), onClick = onDone)
            }
        }
    }
}

/** Explanations sit in one rounded card, like Tomato's settings rows. */
@Composable
private fun Body(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceBright, modifier = modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(20.dp))
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    ) { Text(text, style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)) }
}

@Preview
@Composable
private fun OnboardingPickPreview() {
    HushTheme {
        OnboardingScreen(
            state =
                OnboardingUiState(
                    step = OnboardingStep.PickApps,
                    lists =
                        listOf(
                            BlockList(ProfileId("deep"), "Deep work", setOf("a", "b", "c")),
                            BlockList(ProfileId("detox"), "Detox", emptySet()),
                        ),
                ),
            onAction = {},
            onOpenList = {},
            onDone = {},
        )
    }
}

@Preview
@Composable
private fun OnboardingTurnOnPreview() {
    HushTheme {
        OnboardingScreen(state = OnboardingUiState(step = OnboardingStep.TurnOn), onAction = {}, onOpenList = {}, onDone = {})
    }
}
