// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A message the user acknowledges, in a tonal card that grows in and out of place. */
@Composable
internal fun <T : Any> AnimatedNotice(
    notice: T?,
    text: @Composable (T) -> String?,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = notice,
        transitionSpec = { fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec()) },
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) { current ->
        val message = current?.let { text(it) }
        if (message != null) {
            NoticeCard(text = message, actionLabel = actionLabel, onAction = onAction, error = error)
        }
    }
}

@Composable
internal fun NoticeCard(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (error) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (error) colors.onErrorContainer else colors.onSecondaryContainer,
        modifier = modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp))
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(actionLabel) }
        }
    }
}

/** Tomato's progress ring: a plain thick ring for focus, a wavy one for rest. */
@Composable
internal fun ProgressRing(
    progress: () -> Float,
    wavy: Boolean,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 16.dp,
) {
    if (wavy) {
        val stroke = with(LocalDensity.current) { Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round) }
        CircularWavyProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            stroke = stroke,
            trackStroke = stroke,
            wavelength = 60.dp,
            gapSize = strokeWidth / 2,
        )
    } else {
        CircularProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            strokeWidth = strokeWidth,
            gapSize = strokeWidth / 2,
        )
    }
}
