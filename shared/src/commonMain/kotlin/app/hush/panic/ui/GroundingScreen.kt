// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.panic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hush.resources.Res
import app.hush.resources.panic_back
import app.hush.resources.panic_cant_stop
import app.hush.resources.panic_done
import app.hush.resources.panic_end
import app.hush.resources.panic_intro
import app.hush.resources.panic_notice_needs_setup
import app.hush.resources.panic_notice_unreachable
import app.hush.resources.panic_ok
import app.hush.resources.panic_step_hear
import app.hush.resources.panic_step_see
import app.hush.resources.panic_step_smell
import app.hush.resources.panic_step_taste
import app.hush.resources.panic_step_touch
import app.hush.resources.panic_title
import app.hush.ui.AnimatedNotice
import app.hush.ui.ProgressRing
import app.hush.ui.ScreenTitle
import app.hush.ui.ScreenTopBar
import app.hush.ui.theme.HushTheme
import app.hush.ui.theme.LocalAppFonts
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
fun GroundingRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PanicViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) {
        if (state.closed) onClose()
    }
    GroundingScreen(state = state, onAction = viewModel::onAction, onBackToFocus = onClose, modifier = modifier)
}

@Composable
fun GroundingScreen(
    state: PanicUiState,
    onAction: (PanicAction) -> Unit,
    onBackToFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    Column(modifier = modifier.fillMaxSize()) {
        ScreenTopBar(title = { ScreenTitle(stringResource(Res.string.panic_title), color = scheme.tertiary) })
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(Res.string.panic_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                ProgressRing(
                    progress = { (1.0 - state.remaining / PanicViewModel.EXERCISE).toFloat().coerceIn(0f, 1f) },
                    wavy = true,
                    color = scheme.tertiary,
                    trackColor = scheme.tertiaryContainer,
                    strokeWidth = 12.dp,
                    modifier = Modifier.size(200.dp),
                )
                Text(
                    state.remaining.inWholeSeconds.toString(),
                    style = TextStyle(fontFamily = LocalAppFonts.current.clock, fontSize = 56.sp, fontFeatureSettings = "tnum"),
                )
            }
            Spacer(Modifier.height(32.dp))
            AnimatedContent(
                targetState = if (state.done) null else state.step,
                transitionSpec = {
                    (
                        fadeIn(
                            motionScheme.defaultEffectsSpec(),
                        ) + slideInVertically(motionScheme.defaultSpatialSpec()) { it / 2 }
                    ) togetherWith
                        (fadeOut(motionScheme.fastEffectsSpec()) + slideOutVertically(motionScheme.defaultSpatialSpec()) { -it / 2 })
                },
            ) { step ->
                Text(
                    stringResource(step?.prompt() ?: Res.string.panic_done),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(32.dp))
            // Going back to focus is always allowed; only ending waits for the exercise.
            Button(
                onClick = onBackToFocus,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                Text(stringResource(Res.string.panic_back), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
            }
            AnimatedVisibility(
                visible = state.done,
                enter = fadeIn(motionScheme.defaultEffectsSpec()) + expandVertically(motionScheme.defaultSpatialSpec()),
                exit = fadeOut(motionScheme.fastEffectsSpec()) + shrinkVertically(motionScheme.defaultSpatialSpec()),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(12.dp))
                    if (state.canStopEarly) {
                        FilledTonalButton(
                            onClick = { onAction(PanicAction.EndSession) },
                            enabled = !state.busy,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(Res.string.panic_end))
                        }
                    } else {
                        Text(
                            stringResource(Res.string.panic_cant_stop),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedNotice(
                notice = state.notice,
                text = { notice ->
                    when (notice) {
                        PanicNotice.Unreachable -> stringResource(Res.string.panic_notice_unreachable)

                        PanicNotice.NeedsSetup -> stringResource(Res.string.panic_notice_needs_setup)

                        // Shown by the canStopEarly explanation above.
                        PanicNotice.CantStopEarly -> null
                    }
                },
                actionLabel = stringResource(Res.string.panic_ok),
                onAction = { onAction(PanicAction.DismissNotice) },
            )
        }
    }
}

private fun GroundingStep.prompt(): StringResource =
    when (this) {
        GroundingStep.See -> Res.string.panic_step_see
        GroundingStep.Touch -> Res.string.panic_step_touch
        GroundingStep.Hear -> Res.string.panic_step_hear
        GroundingStep.Smell -> Res.string.panic_step_smell
        GroundingStep.Taste -> Res.string.panic_step_taste
    }

@Preview
@Composable
private fun GroundingScreenPreview() {
    HushTheme {
        GroundingScreen(state = PanicUiState(remaining = 37.seconds, step = GroundingStep.Touch), onAction = {}, onBackToFocus = {})
    }
}

@Preview
@Composable
private fun GroundingScreenDonePreview() {
    HushTheme {
        GroundingScreen(
            state = PanicUiState(remaining = Duration.ZERO, step = GroundingStep.Taste, done = true, canStopEarly = false),
            onAction = {},
            onBackToFocus = {},
        )
    }
}
