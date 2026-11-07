// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.hush.resources.Res
import app.hush.resources.blocked_back
import app.hush.resources.blocked_detox
import app.hush.resources.blocked_focus
import app.hush.resources.blocked_panic
import app.hush.resources.blocked_this_app
import app.hush.resources.blocked_title
import app.hush.timer.ui.dimclock.formatTimeOfDay
import app.hush.ui.ScreenTitle
import app.hush.ui.theme.HushTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Shown by the blocker over a blocked app. Back (home) is the default; during a detox level the
 * panic flow is offered too, so breaking a session costs a minute of grounding, not a wall.
 */
@Composable
fun BlockedScreen(
    appLabel: String?,
    endsAt: Instant,
    detox: Boolean,
    onBack: () -> Unit,
    onPanic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = appLabel ?: stringResource(Res.string.blocked_this_app)
    val until = formatTimeOfDay(endsAt)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenTitle(stringResource(Res.string.blocked_title), color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(if (detox) Res.string.blocked_detox else Res.string.blocked_focus, app, until),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Column(
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onBack,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) { Text(stringResource(Res.string.blocked_back), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)) }
            if (detox) {
                FilledTonalButton(
                    onClick = onPanic,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                ) {
                    Text(
                        stringResource(Res.string.blocked_panic),
                        style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun BlockedScreenPreview() {
    HushTheme {
        BlockedScreen(appLabel = "Video", endsAt = Instant.fromEpochSeconds(0), detox = true, onBack = {}, onPanic = {})
    }
}
