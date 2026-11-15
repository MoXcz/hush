// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2025-2026 Nishant Mishra (Tomato, https://github.com/nsh07/Tomato): adapted from the settings screens.
package app.hush.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.hush.resources.Res
import app.hush.resources.ic_add
import app.hush.resources.ic_check
import app.hush.resources.ic_keyboard_arrow_right
import app.hush.resources.ic_open_in_new
import app.hush.resources.ic_remove
import app.hush.resources.settings_decrease
import app.hush.resources.settings_increase
import app.hush.ui.theme.segmentShape
import app.hush.ui.theme.segmentedColors
import app.hush.ui.theme.segmentedShapes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// The rows a settings page is made of. A group of rows shares one block: each row says which row
// it is ([index] of [count]) so the block's outer corners are round and its inner ones small.

@Composable
internal fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
    )
}

// A setting that is on or off; the whole row toggles it.
@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        shapes = segmentedShapes(index, count),
        colors = segmentedColors,
        // The item calls itself a checkbox; a screen reader should say switch.
        modifier = modifier.semantics { role = Role.Switch },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { RowSwitch(checked) },
    ) { Text(label) }
}

// The switch at the end of a row that handles the tap itself.
@Composable
internal fun RowSwitch(checked: Boolean) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        colors = SwitchDefaults.colors(checkedIconColor = MaterialTheme.colorScheme.primary),
        thumbContent = {
            if (checked) {
                Icon(painterResource(Res.drawable.ic_check), contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize))
            }
        },
    )
}

// A row that opens another screen.
@Composable
internal fun NavRow(
    label: String,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = segmentedShapes(index, count),
        colors = segmentedColors,
        modifier = modifier,
        leadingContent = leading,
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Icon(painterResource(Res.drawable.ic_keyboard_arrow_right), contentDescription = null) },
    ) { Text(label) }
}

// A row that opens [url] in the browser.
@Composable
internal fun LinkRow(
    label: String,
    url: String,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    SegmentedListItem(
        onClick = { uriHandler.openUri(url) },
        shapes = segmentedShapes(index, count),
        colors = segmentedColors,
        modifier = modifier,
        supportingContent = { Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Icon(painterResource(Res.drawable.ic_open_in_new), contentDescription = null) },
    ) { Text(label) }
}

// A number stepped down and up.
@Composable
internal fun StepperRow(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onStep: (up: Boolean) -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clip(segmentShape(index, count)),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        headlineContent = { Text(label) },
        trailingContent = { Stepper(value = value, canDecrease = canDecrease, canIncrease = canIncrease, onStep = onStep) },
    )
}

// −/+ around a value that rolls up or down with the step.
@Composable
internal fun Stepper(
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onStep: (up: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    var lastUp by remember { mutableStateOf(true) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = {
                lastUp = false
                onStep(false)
            },
            enabled = canDecrease,
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(painterResource(Res.drawable.ic_remove), contentDescription = stringResource(Res.string.settings_decrease))
        }
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                val spec = motionScheme.defaultSpatialSpec<IntOffset>()
                val sign = if (lastUp) 1 else -1
                (slideInVertically(spec) { sign * it } + fadeIn(motionScheme.defaultEffectsSpec())) togetherWith
                    (slideOutVertically(spec) { -sign * it } + fadeOut(motionScheme.fastEffectsSpec()))
            },
            contentAlignment = Alignment.Center,
            modifier = Modifier.widthIn(min = 80.dp),
        ) {
            Text(it, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        }
        FilledTonalIconButton(
            onClick = {
                lastUp = true
                onStep(true)
            },
            enabled = canIncrease,
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(painterResource(Res.drawable.ic_add), contentDescription = stringResource(Res.string.settings_increase))
        }
    }
}
