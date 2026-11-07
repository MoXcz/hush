// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import app.hush.resources.Res
import app.hush.resources.ic_check
import app.hush.resources.ic_keyboard_arrow_right
import app.hush.resources.tag_choose
import app.hush.resources.tag_locked
import app.hush.tags.Tag
import app.hush.tags.TagId
import app.hush.tags.ui.TagDot
import app.hush.tags.ui.tagSummary
import app.hush.ui.theme.segmentedColors
import app.hush.ui.theme.segmentedShapes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The current tag above the timer; tapping it opens [TagSheet]. */
@Composable
internal fun TagChip(
    tag: Tag,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    AssistChip(
        onClick = onClick,
        label = {
            AnimatedContent(
                tag.name,
                transitionSpec = { fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec()) },
            ) { Text(it, style = MaterialTheme.typography.labelLarge) }
        },
        leadingIcon = { TagDot(tag.color, size = 10.dp) },
        trailingIcon = {
            Icon(
                painterResource(Res.drawable.ic_keyboard_arrow_right),
                contentDescription = stringResource(Res.string.tag_choose),
                modifier = Modifier.size(AssistChipDefaults.IconSize).rotate(90f),
            )
        },
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier,
    )
}

@Composable
internal fun TagSheet(
    tags: List<Tag>,
    selected: TagId?,
    canSwitch: Boolean,
    onSelect: (TagId) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(Res.string.tag_choose),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
            if (!canSwitch) {
                Text(
                    stringResource(Res.string.tag_locked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
            tags.forEachIndexed { index, tag ->
                val isSelected = tag.id == selected
                SegmentedListItem(
                    selected = isSelected,
                    onClick = {
                        onSelect(tag.id)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                    enabled = canSwitch,
                    shapes = segmentedShapes(index, tags.size),
                    colors = segmentedColors,
                    leadingContent = { TagDot(tag.color) },
                    supportingContent = { Text(tagSummary(tag)) },
                    trailingContent = { if (isSelected) Icon(painterResource(Res.drawable.ic_check), contentDescription = null) },
                ) { Text(tag.name) }
            }
        }
    }
}
