// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.settings.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hush.blocker.ProfileId
import app.hush.blocklists.ui.BlockListRows
import app.hush.detox.ui.levelLabel
import app.hush.resources.Res
import app.hush.resources.activity_add
import app.hush.resources.activity_edit
import app.hush.resources.activity_name
import app.hush.resources.ic_activity
import app.hush.resources.ic_add
import app.hush.resources.ic_check
import app.hush.resources.ic_keyboard_arrow_right
import app.hush.resources.ic_remove
import app.hush.resources.settings_activities
import app.hush.resources.settings_add_activity
import app.hush.resources.settings_add_tag
import app.hush.resources.settings_app_usage
import app.hush.resources.settings_app_usage_off
import app.hush.resources.settings_app_usage_off_switch
import app.hush.resources.settings_app_usage_on
import app.hush.resources.settings_app_usage_on_switch
import app.hush.resources.settings_blocking
import app.hush.resources.settings_blocking_off
import app.hush.resources.settings_blocking_on
import app.hush.resources.settings_blocking_service
import app.hush.resources.settings_decrease
import app.hush.resources.settings_detox
import app.hush.resources.settings_detox_goal
import app.hush.resources.settings_dim_clock_lock_screen
import app.hush.resources.settings_dim_clock_lock_screen_summary
import app.hush.resources.settings_increase
import app.hush.resources.settings_long_break
import app.hush.resources.settings_long_break_after
import app.hush.resources.settings_minutes
import app.hush.resources.settings_short_break
import app.hush.resources.settings_sync
import app.hush.resources.settings_sync_pair_computer
import app.hush.resources.settings_sync_pair_phone
import app.hush.resources.settings_tags
import app.hush.resources.settings_timer
import app.hush.resources.settings_title
import app.hush.resources.settings_work
import app.hush.resources.settings_work_phases
import app.hush.resources.tag_delete
import app.hush.resources.tag_edit
import app.hush.resources.tag_name
import app.hush.resources.tag_new
import app.hush.resources.tag_rule_remove
import app.hush.resources.tag_rules
import app.hush.resources.tag_save
import app.hush.rules.ActivityRule
import app.hush.sync.SyncRole
import app.hush.sync.ui.syncLineText
import app.hush.tags.Tag
import app.hush.tags.TagColor
import app.hush.tags.TagId
import app.hush.tags.ui.TagDot
import app.hush.tags.ui.tagSummary
import app.hush.timer.TimerDurations
import app.hush.ui.ScreenTitle
import app.hush.ui.ScreenTopBar
import app.hush.ui.theme.HushTheme
import app.hush.ui.theme.color
import app.hush.ui.theme.segmentShape
import app.hush.ui.theme.segmentedColors
import app.hush.ui.theme.segmentedShapes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.minutes

@Composable
fun SettingsRoute(
    onOpenList: (ProfileId) -> Unit,
    onOpenSync: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(SettingsAction.Refresh)
        onPauseOrDispose {}
    }
    SettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenList = onOpenList,
        onOpenSync = onOpenSync,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onOpenList: (ProfileId) -> Unit,
    onOpenSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    state.editor?.let { TagEditorSheet(editor = it, onAction = onAction) }
    state.activityEditor?.let { ActivityEditorSheet(editor = it, rules = state.editorRules, onAction = onAction) }
    // Background first, so it also fills the space the caller pads out for the bottom bar.
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).then(modifier)) {
        ScreenTopBar(title = { ScreenTitle(stringResource(Res.string.settings_title)) })
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SectionTitle(stringResource(Res.string.settings_tags))
            val tagRows = state.tags.size + 1
            state.tags.forEachIndexed { index, tag ->
                SegmentedListItem(
                    onClick = { onAction(SettingsAction.EditTag(tag.id)) },
                    shapes = segmentedShapes(index, tagRows),
                    colors = segmentedColors,
                    leadingContent = { TagDot(tag.color) },
                    supportingContent = { Text(tagSummary(tag)) },
                    trailingContent = { Icon(painterResource(Res.drawable.ic_keyboard_arrow_right), contentDescription = null) },
                ) { Text(tag.name) }
            }
            SegmentedListItem(
                onClick = { onAction(SettingsAction.AddTag) },
                shapes = segmentedShapes(tagRows - 1, tagRows),
                colors = segmentedColors,
                leadingContent = { Icon(painterResource(Res.drawable.ic_add), contentDescription = null) },
            ) { Text(stringResource(Res.string.settings_add_tag)) }

            SectionTitle(stringResource(Res.string.settings_activities))
            val activityRows = state.activities.size + 1
            state.activities.forEachIndexed { index, activity ->
                SegmentedListItem(
                    onClick = { onAction(SettingsAction.EditActivity(activity.id)) },
                    shapes = segmentedShapes(index, activityRows),
                    colors = segmentedColors,
                    leadingContent = { Icon(painterResource(Res.drawable.ic_activity), contentDescription = null) },
                    trailingContent = { Icon(painterResource(Res.drawable.ic_keyboard_arrow_right), contentDescription = null) },
                ) { Text(activity.name) }
            }
            SegmentedListItem(
                onClick = { onAction(SettingsAction.AddActivity) },
                shapes = segmentedShapes(activityRows - 1, activityRows),
                colors = segmentedColors,
                leadingContent = { Icon(painterResource(Res.drawable.ic_add), contentDescription = null) },
            ) { Text(stringResource(Res.string.settings_add_activity)) }

            if (state.showLockScreen || state.showAppUsage) {
                SectionTitle(stringResource(Res.string.settings_timer))
            }
            val timerRows = listOf(state.showAppUsage, state.showLockScreen).count { it }
            if (state.showAppUsage) {
                SegmentedListItem(
                    onClick = { onAction(SettingsAction.OpenAppUsage) },
                    shapes = segmentedShapes(0, timerRows),
                    colors = segmentedColors,
                    supportingContent = {
                        val on = if (state.appUsageSwitch) Res.string.settings_app_usage_on_switch else Res.string.settings_app_usage_on
                        val off = if (state.appUsageSwitch) Res.string.settings_app_usage_off_switch else Res.string.settings_app_usage_off
                        when (state.appUsageOn) {
                            true -> {
                                Text(stringResource(on))
                            }

                            false -> {
                                Text(stringResource(off))
                            }

                            null -> {
                                Unit
                            }
                        }
                    },
                    trailingContent = {
                        if (state.appUsageSwitch) {
                            val on = state.appUsageOn == true
                            Switch(
                                checked = on,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(checkedIconColor = MaterialTheme.colorScheme.primary),
                                thumbContent = { if (on) SwitchIcon() },
                            )
                        } else {
                            Icon(painterResource(Res.drawable.ic_keyboard_arrow_right), contentDescription = null)
                        }
                    },
                ) { Text(stringResource(Res.string.settings_app_usage)) }
            }
            if (state.showLockScreen) {
                SegmentedListItem(
                    checked = state.dimClockShowWhenLocked,
                    onCheckedChange = { onAction(SettingsAction.SetDimClockShowWhenLocked(it)) },
                    shapes = segmentedShapes(timerRows - 1, timerRows),
                    colors = segmentedColors,
                    supportingContent = { Text(stringResource(Res.string.settings_dim_clock_lock_screen_summary)) },
                    trailingContent = {
                        Switch(
                            checked = state.dimClockShowWhenLocked,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(checkedIconColor = MaterialTheme.colorScheme.primary),
                            thumbContent = { if (state.dimClockShowWhenLocked) SwitchIcon() },
                        )
                    },
                ) { Text(stringResource(Res.string.settings_dim_clock_lock_screen)) }
            }

            if (state.showBlocking) {
                SectionTitle(stringResource(Res.string.settings_detox))
                ListItem(
                    modifier = Modifier.clip(segmentShape(0, 1)),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                    headlineContent = { Text(stringResource(Res.string.settings_detox_goal)) },
                    trailingContent = {
                        Stepper(
                            value = levelLabel(state.detoxGoalMinutes),
                            canDecrease = state.canDecreaseGoal,
                            canIncrease = state.canIncreaseGoal,
                            onStep = { up -> onAction(SettingsAction.StepDetoxGoal(up)) },
                        )
                    },
                )

                SectionTitle(stringResource(Res.string.settings_blocking))
                val rows = state.blockLists.size + 1
                val on = state.blockingOn == true
                SegmentedListItem(
                    checked = on,
                    onCheckedChange = { onAction(SettingsAction.SetBlocking(it)) },
                    shapes = segmentedShapes(0, rows),
                    colors = segmentedColors,
                    supportingContent = {
                        when (state.blockingOn) {
                            true -> Text(stringResource(Res.string.settings_blocking_on))
                            false -> Text(stringResource(Res.string.settings_blocking_off))
                            null -> Unit
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = on,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(checkedIconColor = MaterialTheme.colorScheme.primary),
                            thumbContent = { if (on) SwitchIcon() },
                        )
                    },
                ) { Text(stringResource(Res.string.settings_blocking_service)) }
                BlockListRows(lists = state.blockLists, onOpen = onOpenList, firstIndex = 1, count = rows)
            }

            state.sync?.let { sync ->
                SectionTitle(stringResource(Res.string.settings_sync))
                SyncRow(sync = sync, onClick = onOpenSync)
            }
        }
    }
}

@Composable
private fun SyncRow(
    sync: SyncSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val invitation = if (sync.role == SyncRole.Host) Res.string.settings_sync_pair_phone else Res.string.settings_sync_pair_computer
    SegmentedListItem(
        onClick = onClick,
        shapes = segmentedShapes(0, 1),
        colors = segmentedColors,
        modifier = modifier,
        supportingContent = sync.line?.let { line -> @Composable { Text(syncLineText(line)) } },
        trailingContent = { Icon(painterResource(Res.drawable.ic_keyboard_arrow_right), contentDescription = null) },
    ) { Text(sync.peerName ?: stringResource(invitation)) }
}

@Composable
private fun SwitchIcon() {
    Icon(painterResource(Res.drawable.ic_check), contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize))
}

@Composable
private fun StepperRow(
    setting: TimerSetting,
    editor: TagEditor,
    onAction: (SettingsAction) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val label =
        when (setting) {
            TimerSetting.Work -> Res.string.settings_work
            TimerSetting.ShortBreak -> Res.string.settings_short_break
            TimerSetting.LongBreak -> Res.string.settings_long_break
            TimerSetting.LongBreakAfter -> Res.string.settings_long_break_after
        }
    val unit = if (setting == TimerSetting.LongBreakAfter) Res.string.settings_work_phases else Res.string.settings_minutes
    ListItem(
        modifier = modifier.clip(shape),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        headlineContent = { Text(stringResource(label)) },
        trailingContent = {
            Stepper(
                value = stringResource(unit, editor.value(setting)),
                canDecrease = editor.canDecrease(setting),
                canIncrease = editor.canIncrease(setting),
                onStep = { up -> onAction(SettingsAction.Step(setting, up)) },
            )
        },
    )
}

@Composable
private fun TagEditorSheet(
    editor: TagEditor,
    onAction: (SettingsAction) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    // Animate the sheet away first, then send the action that clears the editor.
    val closeWith: (SettingsAction) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { onAction(action) }
    }
    ModalBottomSheet(
        onDismissRequest = { onAction(SettingsAction.CloseEditor) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
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
                stringResource(if (editor.id == null) Res.string.tag_new else Res.string.tag_edit),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = editor.name,
                onValueChange = { onAction(SettingsAction.EditName(it)) },
                label = { Text(stringResource(Res.string.tag_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            ColorPicker(
                selected = editor.color,
                onSelect = { onAction(SettingsAction.EditColor(it)) },
                modifier = Modifier.padding(vertical = 16.dp),
            )
            TimerSetting.entries.forEachIndexed { index, setting ->
                StepperRow(setting = setting, editor = editor, onAction = onAction, shape = segmentShape(index, TimerSetting.entries.size))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editor.canDelete) {
                    TextButton(
                        onClick = { closeWith(SettingsAction.DeleteTag) },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(Res.string.tag_delete)) }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { closeWith(SettingsAction.SaveTag) },
                    enabled = editor.canSave,
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.tag_save)) }
            }
        }
    }
}

/** An app, site or program sorted into the activity being edited, with a button that removes the rule. */
@Composable
private fun RuleRow(
    rule: ActivityRule,
    onRemove: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clip(shape),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        trailingContent = {
            IconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.ic_remove), contentDescription = stringResource(Res.string.tag_rule_remove, rule.label))
            }
        },
        headlineContent = { Text(rule.label, maxLines = 1) },
    )
}

@Composable
private fun ActivityEditorSheet(
    editor: ActivityEditor,
    rules: List<ActivityRule>,
    onAction: (SettingsAction) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val closeWith: (SettingsAction) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { onAction(action) }
    }
    ModalBottomSheet(
        onDismissRequest = { onAction(SettingsAction.CloseActivityEditor) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
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
                stringResource(if (editor.id == null) Res.string.activity_add else Res.string.activity_edit),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = editor.name,
                onValueChange = { onAction(SettingsAction.EditActivityName(it)) },
                label = { Text(stringResource(Res.string.activity_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            if (rules.isNotEmpty()) {
                Text(
                    stringResource(Res.string.tag_rules),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
                )
                rules.forEachIndexed { index, rule ->
                    RuleRow(rule, onRemove = { onAction(SettingsAction.RemoveRule(rule.appKey)) }, shape = segmentShape(index, rules.size))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editor.canDelete) {
                    TextButton(
                        onClick = { closeWith(SettingsAction.DeleteActivity) },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(Res.string.tag_delete)) }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { closeWith(SettingsAction.SaveActivity) },
                    enabled = editor.canSave,
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.tag_save)) }
            }
        }
    }
}

@Composable
private fun ColorPicker(
    selected: TagColor,
    onSelect: (TagColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TagColor.entries.forEach { color ->
            val isSelected = color == selected
            val swatch = color.color()
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(color) })
                        .padding(6.dp)
                        .background(swatch, CircleShape),
            ) {
                if (isSelected) {
                    Icon(
                        painterResource(Res.drawable.ic_check),
                        contentDescription = color.name,
                        tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Stepper(
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
        // The value rolls up or down with the step, like Tomato's "Up next" numbers.
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

@Composable
private fun SectionTitle(
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

@Preview
@Composable
private fun SettingsScreenPreview() {
    val tags =
        listOf(
            Tag(TagId(1), "Focus", TagColor.Indigo, TimerDurations()),
            Tag(TagId(2), "Light", TagColor.Teal, TimerDurations(work = 20.minutes, shortBreak = 10.minutes)),
        )
    HushTheme { SettingsScreen(state = SettingsUiState(tags = tags), onAction = {}, onOpenList = {}, onOpenSync = {}) }
}
