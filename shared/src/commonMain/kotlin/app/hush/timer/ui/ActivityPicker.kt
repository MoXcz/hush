// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.hush.activities.ACTIVITY_NAME_MAX_LENGTH
import app.hush.activities.Activity
import app.hush.activities.ActivityId
import app.hush.resources.Res
import app.hush.resources.activity_add
import app.hush.resources.activity_add_action
import app.hush.resources.activity_choose
import app.hush.resources.activity_choose_hint
import app.hush.resources.activity_label
import app.hush.resources.activity_name
import app.hush.resources.activity_none
import app.hush.resources.ic_activity
import app.hush.resources.ic_check
import app.hush.ui.theme.segmentedColors
import app.hush.ui.theme.segmentedShapes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** What the work phases are spent doing, next to the tag chip; "Doing" while nothing is picked. */
@Composable
internal fun ActivityChip(
    activity: Activity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val none = stringResource(Res.string.activity_label)
    AssistChip(
        onClick = onClick,
        label = {
            AnimatedContent(
                activity?.name ?: none,
                transitionSpec = { fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec()) },
            ) {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (activity == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        leadingIcon = {
            Icon(
                painterResource(Res.drawable.ic_activity),
                contentDescription = stringResource(Res.string.activity_choose),
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier,
    )
}

@Composable
internal fun ActivitySheet(
    activities: List<Activity>,
    selected: ActivityId?,
    onSelect: (ActivityId?) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val close = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    var draft by rememberSaveable { mutableStateOf("") }
    val add = {
        if (draft.isNotBlank()) {
            onAdd(draft)
            close()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding()
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(Res.string.activity_choose),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
            Text(
                stringResource(Res.string.activity_choose_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            val rows = activities.size + 1
            SegmentedListItem(
                selected = selected == null,
                onClick = {
                    onSelect(null)
                    close()
                },
                shapes = segmentedShapes(0, rows),
                colors = segmentedColors,
                trailingContent = { if (selected == null) Icon(painterResource(Res.drawable.ic_check), contentDescription = null) },
            ) { Text(stringResource(Res.string.activity_none)) }
            activities.forEachIndexed { index, activity ->
                val isSelected = activity.id == selected
                SegmentedListItem(
                    selected = isSelected,
                    onClick = {
                        onSelect(activity.id)
                        close()
                    },
                    shapes = segmentedShapes(index + 1, rows),
                    colors = segmentedColors,
                    trailingContent = { if (isSelected) Icon(painterResource(Res.drawable.ic_check), contentDescription = null) },
                ) { Text(activity.name) }
            }
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(ACTIVITY_NAME_MAX_LENGTH) },
                    label = { Text(stringResource(Res.string.activity_add)) },
                    placeholder = { Text(stringResource(Res.string.activity_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = { add() }, enabled = draft.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.activity_add_action))
                }
            }
        }
    }
}
