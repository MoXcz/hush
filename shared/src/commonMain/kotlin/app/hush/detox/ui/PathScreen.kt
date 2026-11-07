// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.detox.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hush.detox.LevelOutcome
import app.hush.resources.Res
import app.hush.resources.ic_check
import app.hush.resources.path_active
import app.hush.resources.path_complete
import app.hush.resources.path_earlier_level
import app.hush.resources.path_level
import app.hush.resources.path_level_current
import app.hush.resources.path_level_passed
import app.hush.resources.path_no_profile
import app.hush.resources.path_notice_refused
import app.hush.resources.path_notice_unconfirmed
import app.hush.resources.path_notice_unreachable
import app.hush.resources.path_ok
import app.hush.resources.path_outcome_ended
import app.hush.resources.path_outcome_passed
import app.hush.resources.path_outcome_passed_again
import app.hush.resources.path_outcome_stopped_in_blocker
import app.hush.resources.path_panic
import app.hush.resources.path_phone_only
import app.hush.resources.path_set_up
import app.hush.resources.path_start
import app.hush.resources.path_stats
import app.hush.resources.path_title
import app.hush.timer.ui.formatCountdown
import app.hush.ui.AnimatedNotice
import app.hush.ui.ProgressRing
import app.hush.ui.ScreenTitle
import app.hush.ui.ScreenTopBar
import app.hush.ui.theme.HushTheme
import app.hush.ui.theme.LocalAppFonts
import app.hush.ui.theme.segmentShape
import app.hush.ui.theme.segmentedShapes
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.minutes

@Composable
fun PathRoute(
    onOpenPanic: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PathViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onAction(PathAction.Check)
        onPauseOrDispose {}
    }
    LaunchedEffect(state.notice) {
        if (state.notice == PathNotice.NeedsSetup) {
            viewModel.onAction(PathAction.DismissNotice)
            onOpenSetup()
        }
    }
    PathScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenPanic = onOpenPanic,
        onOpenStats = onOpenStats,
        onOpenSetup = onOpenSetup,
        modifier = modifier,
    )
}

@Composable
fun PathScreen(
    state: PathUiState,
    onAction: (PathAction) -> Unit,
    onOpenPanic: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    // Background first, so it also fills the space the caller pads out for the bottom bar.
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).then(modifier)) {
        ScreenTopBar(
            title = { ScreenTitle(stringResource(Res.string.path_title)) },
            actions = {
                FilledTonalButton(onClick = onOpenStats, shapes = ButtonDefaults.shapes(), modifier = Modifier.padding(end = 8.dp)) {
                    Text(stringResource(Res.string.path_stats))
                }
            },
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = state.active,
                contentKey = { it != null },
                transitionSpec = {
                    (fadeIn(motionScheme.defaultEffectsSpec()) + scaleIn(motionScheme.defaultSpatialSpec(), 0.9f)) togetherWith
                        fadeOut(motionScheme.fastEffectsSpec())
                },
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxWidth(),
            ) { active ->
                if (active != null) {
                    ActiveAttempt(active = active, onOpenPanic = onOpenPanic, modifier = Modifier.fillMaxWidth())
                } else {
                    StartArea(state = state, onAction = onAction, onOpenSetup = onOpenSetup, modifier = Modifier.fillMaxWidth())
                }
            }
            val ok = stringResource(Res.string.path_ok)
            AnimatedNotice(
                notice = state.outcome,
                text = { stringResource(it.message()) },
                actionLabel = ok,
                onAction = { onAction(PathAction.DismissOutcome) },
            )
            AnimatedNotice(
                notice = state.notice,
                text = { notice -> notice.message()?.let { stringResource(it) } },
                actionLabel = ok,
                onAction = { onAction(PathAction.DismissNotice) },
            )
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier.widthIn(max = 600.dp).padding(horizontal = 16.dp).animateContentSize(motionScheme.defaultSpatialSpec()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val count = state.levels.size
                state.levels.asReversed().forEachIndexed { index, level ->
                    LevelRow(
                        number = count - index,
                        level = level,
                        index = index,
                        count = count,
                        onSelect = { onAction(PathAction.SelectLevel(level.minutes)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveAttempt(
    active: ActiveAttemptUi,
    onOpenPanic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(Res.string.path_active, levelLabel(active.levelMinutes)),
            style = MaterialTheme.typography.titleLarge,
            color = scheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f)) {
            ProgressRing(
                progress = { active.progress },
                wavy = false,
                color = scheme.primary,
                trackColor = scheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(0.9f).aspectRatio(1f),
            )
            Text(
                formatCountdown(active.remaining),
                style =
                    TextStyle(
                        fontFamily = LocalAppFonts.current.clock,
                        fontSize = 56.sp,
                        letterSpacing = (-2).sp,
                        fontFeatureSettings = "tnum",
                    ),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(
            onClick = onOpenPanic,
            shapes = ButtonDefaults.shapes(),
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.tertiaryContainer,
                    contentColor = scheme.onTertiaryContainer,
                ),
            modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) {
            Text(stringResource(Res.string.path_panic), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
        }
    }
}

@Composable
private fun StartArea(
    state: PathUiState,
    onAction: (PathAction) -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 24.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            levelLabel(state.currentLevelMinutes),
            style = TextStyle(fontFamily = LocalAppFonts.current.clock, fontSize = 56.sp, letterSpacing = (-2).sp),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        AnimatedVisibility(visible = state.chosenLevelMinutes != null) {
            Text(
                stringResource(Res.string.path_earlier_level),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        if (state.complete) {
            Text(
                stringResource(Res.string.path_complete),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }
        if (state.phoneOnly) {
            Text(
                stringResource(Res.string.path_phone_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else if (state.noProfile) {
            Text(
                stringResource(Res.string.path_no_profile),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onOpenSetup, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.path_set_up)) }
        } else {
            Button(
                onClick = { onAction(PathAction.StartAttempt) },
                enabled = !state.busy,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                Text(
                    stringResource(Res.string.path_start, levelLabel(state.currentLevelMinutes)),
                    style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                )
            }
        }
    }
}

/** Selectable rows (passed or next, before Start) are segmented rows; the rest are static. */
@Composable
private fun LevelRow(
    number: Int,
    level: LevelItem,
    index: Int,
    count: Int,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val content =
        when {
            level.selected -> scheme.onPrimaryContainer
            level.status == LevelStatus.Locked -> scheme.onSurfaceVariant
            else -> scheme.onSurface
        }
    val headline: @Composable () -> Unit = {
        Text(
            levelLabel(level.minutes),
            color = content,
            fontWeight = if (level.selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
    val overline: @Composable () -> Unit = { Text(stringResource(Res.string.path_level, number), color = content) }
    val trailing: @Composable () -> Unit = {
        when (level.status) {
            LevelStatus.Passed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(Res.drawable.ic_check), contentDescription = null, tint = scheme.primary)
                    Text(
                        stringResource(Res.string.path_level_passed),
                        color = scheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            LevelStatus.Current -> {
                Text(stringResource(Res.string.path_level_current), color = content)
            }

            LevelStatus.Locked -> {}
        }
    }
    if (level.selectable) {
        SegmentedListItem(
            selected = level.selected,
            onClick = onSelect,
            shapes = segmentedShapes(index, count),
            colors =
                ListItemDefaults.segmentedColors(
                    containerColor = scheme.surfaceBright,
                    selectedContainerColor = scheme.primaryContainer,
                ),
            modifier = modifier,
            overlineContent = overline,
            trailingContent = trailing,
            content = headline,
        )
    } else {
        val container by animateColorAsState(if (level.selected) scheme.primaryContainer else scheme.surfaceBright)
        ListItem(
            modifier = modifier.clip(segmentShape(index, count)),
            colors = ListItemDefaults.colors(containerColor = container),
            headlineContent = headline,
            overlineContent = overline,
            trailingContent = trailing,
        )
    }
}

private fun LevelOutcome.message(): StringResource =
    when (this) {
        LevelOutcome.Passed -> Res.string.path_outcome_passed
        LevelOutcome.PassedAgain -> Res.string.path_outcome_passed_again
        LevelOutcome.EndedEarly -> Res.string.path_outcome_ended
        LevelOutcome.StoppedInBlocker -> Res.string.path_outcome_stopped_in_blocker
    }

private fun PathNotice.message(): StringResource? =
    when (this) {
        PathNotice.NeedsSetup -> null
        PathNotice.Refused -> Res.string.path_notice_refused
        PathNotice.Unreachable -> Res.string.path_notice_unreachable
        PathNotice.Unconfirmed -> Res.string.path_notice_unconfirmed
    }

private val previewLevels =
    listOf(
        LevelItem(60, LevelStatus.Passed, selectable = true),
        LevelItem(90, LevelStatus.Current, selected = true, selectable = true),
        LevelItem(120, LevelStatus.Locked),
    )

@Preview
@Composable
private fun PathScreenPreview() {
    HushTheme {
        PathScreen(
            state = PathUiState(levels = previewLevels, currentLevelMinutes = 90),
            onAction = {},
            onOpenPanic = {},
            onOpenStats = {},
            onOpenSetup = {},
        )
    }
}

@Preview
@Composable
private fun PathScreenActivePreview() {
    HushTheme {
        PathScreen(
            state =
                PathUiState(
                    levels = previewLevels,
                    currentLevelMinutes = 90,
                    active = ActiveAttemptUi(90, 41.minutes, 0.55f, canStopEarly = true),
                ),
            onAction = {},
            onOpenPanic = {},
            onOpenStats = {},
            onOpenSetup = {},
        )
    }
}
