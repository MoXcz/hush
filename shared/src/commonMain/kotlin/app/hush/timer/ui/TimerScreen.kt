// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupMenuState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hush.resources.Res
import app.hush.resources.ic_pause_large
import app.hush.resources.ic_play_large
import app.hush.resources.ic_restart_large
import app.hush.resources.ic_skip_next_large
import app.hush.resources.phase_long_break
import app.hush.resources.phase_short_break
import app.hush.resources.phase_work
import app.hush.resources.timer_blocking_off
import app.hush.resources.timer_blocking_phone_only
import app.hush.resources.timer_dim_clock_hint
import app.hush.resources.timer_notice_cant_stop_early
import app.hush.resources.timer_notice_dismiss
import app.hush.resources.timer_notice_refused
import app.hush.resources.timer_notice_unconfirmed
import app.hush.resources.timer_notice_unreachable
import app.hush.resources.timer_pause
import app.hush.resources.timer_reset
import app.hush.resources.timer_resume
import app.hush.resources.timer_set_up
import app.hush.resources.timer_skip
import app.hush.resources.timer_start
import app.hush.resources.timer_up_next
import app.hush.resources.timer_work_count
import app.hush.tags.Tag
import app.hush.tags.TagColor
import app.hush.tags.TagId
import app.hush.timer.Phase
import app.hush.timer.TimerDurations
import app.hush.ui.AnimatedNotice
import app.hush.ui.ProgressRing
import app.hush.ui.ScreenTitle
import app.hush.ui.ScreenTopBar
import app.hush.ui.SharedTimerKeys
import app.hush.ui.sharedTimerElement
import app.hush.ui.theme.HushTheme
import app.hush.ui.theme.LocalAppFonts
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
fun TimerRoute(
    onOpenDimClock: () -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.notice) {
        if (state.notice == TimerNotice.NeedsSetup) {
            viewModel.onAction(TimerAction.DismissNotice)
            onOpenSetup()
        }
    }
    TimerScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenDimClock = onOpenDimClock,
        onOpenSetup = onOpenSetup,
        modifier = modifier,
    )
}

@Composable
fun TimerScreen(
    state: TimerUiState,
    onAction: (TimerAction) -> Unit,
    onOpenDimClock: () -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = phaseColors(state.phase)
    val motionScheme = MaterialTheme.motionScheme
    var showTags by rememberSaveable { mutableStateOf(false) }
    var showActivities by rememberSaveable { mutableStateOf(false) }
    if (showActivities) {
        ActivitySheet(
            activities = state.activities,
            selected = state.activity?.id,
            onSelect = { onAction(TimerAction.SelectActivity(it)) },
            onAdd = { onAction(TimerAction.AddActivity(it)) },
            onDismiss = { showActivities = false },
        )
    }
    if (showTags) {
        TagSheet(
            tags = state.tags,
            selected = state.tag?.id,
            canSwitch = state.canSwitchTag,
            onSelect = { onAction(TimerAction.SelectTag(it)) },
            onDismiss = { showTags = false },
        )
    }
    Column(modifier = modifier.fillMaxSize()) {
        ScreenTopBar(
            title = {
                AnimatedContent(state.phase, transitionSpec = { verticalSwap(motionScheme.defaultSpatialSpec()) }) { phase ->
                    ScreenTitle(stringResource(phase.label()), color = colors.accent)
                }
            },
        )
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tag?.let { tag -> TagChip(tag = tag, onClick = { showTags = true }) }
                ActivityChip(activity = state.activity, onClick = { showActivities = true })
            }
            TimerDial(state = state, colors = colors, onClick = onOpenDimClock)
            TimerControls(state = state, colors = colors, onAction = onAction)
            Spacer(Modifier.height(24.dp))
            UpNext(phase = state.nextPhase, duration = state.nextDuration)
            Spacer(Modifier.height(16.dp))
            AnimatedNotice(
                notice = state.notice?.takeIf { it != TimerNotice.NeedsSetup },
                text = { stringResource(it.message() ?: return@AnimatedNotice null) },
                actionLabel = stringResource(Res.string.timer_notice_dismiss),
                onAction = { onAction(TimerAction.DismissNotice) },
            )
            AnimatedVisibility(
                visible = state.blockingOff,
                enter = fadeIn(motionScheme.defaultEffectsSpec()) + expandVertically(motionScheme.defaultSpatialSpec()),
                exit = fadeOut(motionScheme.fastEffectsSpec()) + shrinkVertically(motionScheme.defaultSpatialSpec()),
            ) {
                BlockingOffLine(onOpenSetup = onOpenSetup)
            }
            if (state.blockingPhoneOnly) {
                Text(
                    stringResource(Res.string.timer_blocking_phone_only),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Focus uses primary, breaks use tertiary, cross-faded on phase change (as Tomato does). */
private data class PhaseColors(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
)

@Composable
private fun phaseColors(phase: Phase): PhaseColors {
    val scheme = MaterialTheme.colorScheme
    val focus by animateFloatAsState(
        targetValue = if (phase == Phase.Work) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
    )
    return PhaseColors(
        accent = lerp(scheme.tertiary, scheme.primary, focus),
        onAccent = lerp(scheme.onTertiary, scheme.onPrimary, focus),
        container = lerp(scheme.tertiaryContainer, scheme.secondaryContainer, focus),
        onContainer = lerp(scheme.onTertiaryContainer, scheme.onSecondaryContainer, focus),
    )
}

private fun <S> AnimatedContentTransitionScope<S>.verticalSwap(spec: FiniteAnimationSpec<IntOffset>): ContentTransform =
    slideInVertically(spec) { -it * 5 / 4 } togetherWith slideOutVertically(spec) { it * 5 / 4 }

@Composable
private fun TimerDial(
    state: TimerUiState,
    colors: PhaseColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hint = stringResource(Res.string.timer_dim_clock_hint)
    val countdown = formatCountdown(state.remaining)
    val fontSize by animateFloatAsState(
        targetValue = if (countdown.length < 6) 72f else 64f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .widthIn(max = 350.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = hint,
                    onClick = onClick,
                ),
    ) {
        ProgressRing(
            progress = { state.progress },
            wavy = state.phase != Phase.Work,
            color = colors.accent,
            trackColor = colors.container,
            modifier = Modifier.sharedTimerElement(SharedTimerKeys.RING).fillMaxWidth(0.9f).aspectRatio(1f),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = countdown,
                modifier = Modifier.sharedTimerElement(SharedTimerKeys.COUNTDOWN),
                style =
                    TextStyle(
                        fontFamily = LocalAppFonts.current.clock,
                        fontSize = fontSize.sp,
                        letterSpacing = (-2.6).sp,
                        fontFeatureSettings = "tnum",
                    ),
                maxLines = 1,
            )
            Text(
                stringResource(Res.string.timer_work_count, state.workPhase, state.workPhasesPerSet),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = LocalAppFonts.current.clock),
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** Tomato's connected group: a wide play/pause toggle, a round reset, a narrow skip. Buttons squeeze their neighbours when pressed. */
@Composable
private fun TimerControls(
    state: TimerUiState,
    colors: PhaseColors,
    onAction: (TimerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !state.busy
    val running = state.status == TimerStatus.Running
    val haptic = LocalHapticFeedback.current
    val showToggle = !running || state.canStopEarly
    val showReset = state.canReset && state.canStopEarly
    val showSkip = state.canStopEarly
    val toggleLabel =
        stringResource(
            when (state.status) {
                TimerStatus.Ready -> Res.string.timer_start
                TimerStatus.Paused -> Res.string.timer_resume
                TimerStatus.Running -> Res.string.timer_pause
            },
        )
    val resetLabel = stringResource(Res.string.timer_reset)
    val skipLabel = stringResource(Res.string.timer_skip)
    val tonal = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.container, contentColor = colors.onContainer)
    val sources = remember { List(3) { MutableInteractionSource() } }
    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState, colors = tonal, modifier = Modifier.size(64.dp, 96.dp))
        },
        modifier = modifier.padding(16.dp),
    ) {
        if (showToggle) {
            customItem(
                buttonGroupContent = {
                    FilledIconToggleButton(
                        checked = running,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                            onAction(if (running) TimerAction.Pause else TimerAction.Start)
                        },
                        enabled = enabled,
                        colors =
                            IconButtonDefaults.filledIconToggleButtonColors(
                                checkedContainerColor = colors.accent,
                                checkedContentColor = colors.onAccent,
                            ),
                        shapes = IconButtonDefaults.toggleableShapes(checkedShape = MaterialTheme.shapes.extraLarge),
                        interactionSource = sources[0],
                        modifier = Modifier.size(width = 128.dp, height = 96.dp).animateWidth(sources[0]),
                    ) {
                        Icon(
                            painterResource(if (running) Res.drawable.ic_pause_large else Res.drawable.ic_play_large),
                            contentDescription = toggleLabel,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                },
                menuContent = { menuState ->
                    MenuEntry(toggleLabel, menuState) { onAction(if (running) TimerAction.Pause else TimerAction.Start) }
                },
            )
        }
        if (showReset) {
            customItem(
                buttonGroupContent = {
                    FilledTonalIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onAction(TimerAction.Reset)
                        },
                        enabled = enabled,
                        colors = tonal,
                        shapes = IconButtonDefaults.shapes(),
                        interactionSource = sources[1],
                        modifier = Modifier.size(96.dp).animateWidth(sources[1]),
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_restart_large),
                            contentDescription = resetLabel,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                },
                menuContent = { menuState -> MenuEntry(resetLabel, menuState) { onAction(TimerAction.Reset) } },
            )
        }
        if (showSkip) {
            customItem(
                buttonGroupContent = {
                    FilledTonalIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onAction(TimerAction.Skip)
                        },
                        enabled = enabled,
                        colors = tonal,
                        shapes = IconButtonDefaults.shapes(),
                        interactionSource = sources[2],
                        modifier = Modifier.size(64.dp, 96.dp).animateWidth(sources[2]),
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_skip_next_large),
                            contentDescription = skipLabel,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                },
                menuContent = { menuState -> MenuEntry(skipLabel, menuState) { onAction(TimerAction.Skip) } },
            )
        }
    }
}

@Composable
private fun MenuEntry(
    label: String,
    menuState: ButtonGroupMenuState,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = {
            onClick()
            menuState.dismiss()
        },
    )
}

@Composable
private fun UpNext(
    phase: Phase,
    duration: Duration,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(Res.string.timer_up_next), style = MaterialTheme.typography.titleSmall)
        AnimatedContent(duration, transitionSpec = { verticalSwap(motionScheme.defaultSpatialSpec()) }) {
            Text(
                formatCountdown(it),
                style =
                    TextStyle(
                        fontFamily = LocalAppFonts.current.clock,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        color = if (phase == Phase.Work) scheme.primary else scheme.tertiary,
                        fontFeatureSettings = "tnum",
                    ),
            )
        }
        AnimatedContent(phase, transitionSpec = { verticalSwap(motionScheme.defaultSpatialSpec()) }) {
            Text(
                stringResource(it.label()),
                style = MaterialTheme.typography.titleMediumEmphasized.copy(fontFamily = LocalAppFonts.current.clock),
            )
        }
    }
}

private fun TimerNotice.message(): StringResource? =
    when (this) {
        TimerNotice.Refused -> Res.string.timer_notice_refused
        TimerNotice.CantStopEarly -> Res.string.timer_notice_cant_stop_early
        TimerNotice.Unreachable -> Res.string.timer_notice_unreachable
        TimerNotice.Unconfirmed -> Res.string.timer_notice_unconfirmed
        TimerNotice.NeedsSetup -> null
    }

@Composable
private fun BlockingOffLine(
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.timer_blocking_off),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenSetup, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.timer_set_up)) }
    }
}

internal fun Phase.label(): StringResource =
    when (this) {
        Phase.Work -> Res.string.phase_work
        Phase.ShortBreak -> Res.string.phase_short_break
        Phase.LongBreak -> Res.string.phase_long_break
    }

private val previewTag = Tag(TagId(1), "Focus", TagColor.Indigo, TimerDurations())

@Preview
@Composable
private fun TimerScreenRunningPreview() {
    HushTheme {
        TimerScreen(
            state =
                TimerUiState(
                    tag = previewTag,
                    remaining = 12.minutes + 34.seconds,
                    progress = 0.5f,
                    status = TimerStatus.Running,
                    workPhase = 2,
                ),
            onAction = {},
            onOpenDimClock = {},
            onOpenSetup = {},
        )
    }
}

@Preview
@Composable
private fun TimerScreenBlockingOffPreview() {
    HushTheme {
        TimerScreen(state = TimerUiState(blockingOff = true), onAction = {}, onOpenDimClock = {}, onOpenSetup = {})
    }
}
