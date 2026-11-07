// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer.ui.dimclock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hush.timer.ui.formatCountdown
import app.hush.ui.SharedTimerKeys
import app.hush.ui.sharedTimerElement
import app.hush.ui.theme.LocalAppFonts
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val DimText = Color(0xFFA2A2A2)
private val DimTrack = Color(0xFF1D1D1D)
private val ClockSize = 260.dp
private val DriftStep = 2.dp
private val DriftMargin = 16.dp

@Composable
fun DimClockRoute(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DimClockViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exit by rememberUpdatedState(onExit)
    LaunchedEffect(state.exit) { if (state.exit) exit() }
    DimClockWindowEffect(showWhenLocked = state.showWhenLocked)
    DimClockScreen(state = state, onTap = onExit, modifier = modifier)
}

@Composable
fun DimClockScreen(
    state: DimClockUiState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTap,
                ),
    ) {
        val drift = rememberBurnInDrift(maxWidth, maxHeight, minuteOf(state.now))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.offset(drift.x, drift.y).size(ClockSize),
        ) {
            CircularProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.sharedTimerElement(SharedTimerKeys.RING).size(ClockSize),
                color = DimText,
                trackColor = DimTrack,
                strokeWidth = 2.dp,
                gapSize = 0.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatCountdown(state.remaining),
                    modifier = Modifier.sharedTimerElement(SharedTimerKeys.COUNTDOWN),
                    color = DimText,
                    style =
                        TextStyle(
                            fontFamily = LocalAppFonts.current.clock,
                            fontSize = 56.sp,
                            letterSpacing = (-2).sp,
                            fontFeatureSettings = "tnum",
                        ),
                    maxLines = 1,
                )
                if (state.now != Instant.DISTANT_PAST) {
                    Text(
                        text = formatTimeOfDay(state.now),
                        color = DimText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private data class Drift(
    val x: Dp,
    val y: Dp,
)

private fun minuteOf(now: Instant): Long = now.toEpochMilliseconds() / 60_000

/** Moves the clock a little every minute, bouncing inside the margins (as Tomato does). */
@Composable
private fun rememberBurnInDrift(
    width: Dp,
    height: Dp,
    minute: Long,
): Drift {
    val maxX = (width - ClockSize - DriftMargin).coerceAtLeast(DriftMargin)
    val maxY = (height - ClockSize - DriftMargin).coerceAtLeast(DriftMargin)
    var x by rememberSaveable { mutableStateOf(Float.NaN) }
    var y by rememberSaveable { mutableStateOf(Float.NaN) }
    var stepX by rememberSaveable { mutableStateOf(DriftStep.value) }
    var stepY by rememberSaveable { mutableStateOf(DriftStep.value) }
    if (x.isNaN() || y.isNaN()) {
        x = ((width - ClockSize) / 2).value
        y = ((height - ClockSize) / 2).value
    }
    LaunchedEffect(minute) {
        if (x + stepX !in DriftMargin.value..maxX.value) stepX = -stepX
        if (y + stepY !in DriftMargin.value..maxY.value) stepY = -stepY
        x = (x + stepX).coerceIn(DriftMargin.value, maxX.value)
        y = (y + stepY).coerceIn(DriftMargin.value, maxY.value)
    }
    return Drift(x.dp, y.dp)
}

@Preview
@Composable
private fun DimClockScreenPreview() {
    DimClockScreen(state = DimClockUiState(remaining = 18.minutes, progress = 0.3f), onTap = {})
}
