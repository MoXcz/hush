// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.settings.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hush.activities.ACTIVITY_NAME_MAX_LENGTH
import app.hush.activities.Activity
import app.hush.activities.ActivityId
import app.hush.activities.ActivityRepository
import app.hush.blocker.Availability
import app.hush.blocker.Blocker
import app.hush.blocker.BlockerSetup
import app.hush.blocklists.BlockList
import app.hush.blocklists.BlockListRepository
import app.hush.core.DeviceFeatures
import app.hush.rules.ActivityRule
import app.hush.rules.ActivityRuleRepository
import app.hush.settings.SettingsRepository
import app.hush.sync.SyncController
import app.hush.sync.SyncRole
import app.hush.sync.SyncStatus
import app.hush.sync.ui.SyncLine
import app.hush.sync.ui.syncLine
import app.hush.tags.TAG_NAME_MAX_LENGTH
import app.hush.tags.Tag
import app.hush.tags.TagColor
import app.hush.tags.TagId
import app.hush.tags.TagRepository
import app.hush.timer.TimerDurations
import app.hush.usage.AppUsageReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** A tag's timer values, with their range and step (minutes, or work phases). */
enum class TimerSetting(
    val range: IntRange,
    val step: Int,
) {
    Work(1..120, 5),
    ShortBreak(1..30, 1),
    LongBreak(1..60, 5),
    LongBreakAfter(2..8, 1),
    ;

    fun valueIn(durations: TimerDurations): Int =
        when (this) {
            Work -> durations.work.inWholeMinutes.toInt()
            ShortBreak -> durations.shortBreak.inWholeMinutes.toInt()
            LongBreak -> durations.longBreak.inWholeMinutes.toInt()
            LongBreakAfter -> durations.longBreakAfter
        }

    fun stepped(
        durations: TimerDurations,
        up: Boolean,
    ): TimerDurations {
        val current = valueIn(durations)
        // Single minutes below the coarse step, so short test or micro sessions are possible.
        val delta = if (current < step || (!up && current == step)) 1 else step
        val value = (current + if (up) delta else -delta).coerceIn(range)
        return when (this) {
            Work -> durations.copy(work = value.minutes)
            ShortBreak -> durations.copy(shortBreak = value.minutes)
            LongBreak -> durations.copy(longBreak = value.minutes)
            LongBreakAfter -> durations.copy(longBreakAfter = value)
        }
    }
}

/** Detox goal range and step, in minutes: levels are on a 30-minute grid from 1 h. */
val DETOX_GOAL_RANGE = 60..480
const val DETOX_GOAL_STEP = 30

@Immutable
data class SettingsUiState(
    val tags: List<Tag> = emptyList(),
    /** The tag being added or edited, in a sheet; null when the sheet is closed. */
    val editor: TagEditor? = null,
    val dimClockShowWhenLocked: Boolean = false,
    val detoxGoalMinutes: Int = 240,
    /** Detox goal and blocking; hidden where nothing can be blocked (desktop). */
    val showBlocking: Boolean = true,
    /** Whether the blocker is turned on; null until checked. */
    val blockingOn: Boolean? = null,
    val blockLists: List<BlockList> = emptyList(),
    /** The dim clock's lock-screen switch; hidden where there's no lock screen (desktop). */
    val showLockScreen: Boolean = true,
    /** The Sync row; null where there's no sync, or before the pairing is read. */
    val sync: SyncSummary? = null,
    val activities: List<Activity> = emptyList(),
    /** The activity being added or renamed, in a sheet; null when the sheet is closed. */
    val activityEditor: ActivityEditor? = null,
    /** The app usage row; hidden where apps can't be recorded (desktop). */
    val showAppUsage: Boolean = true,
    /** Whether recording is on; null until checked. */
    val appUsageOn: Boolean? = null,
    /** The row is Hush's own switch (desktop), not a link to the system's usage access page. */
    val appUsageSwitch: Boolean = false,
    /** Rules in force; the activity editor lists the apps sorted into its activity. */
    val rules: List<ActivityRule> = emptyList(),
) {
    /** The apps sorted into the activity being edited. */
    val editorRules: List<ActivityRule> get() = activityEditor?.id?.let { id -> rules.filter { it.activityId == id } }.orEmpty()

    val canDecreaseGoal: Boolean get() = detoxGoalMinutes > DETOX_GOAL_RANGE.first

    val canIncreaseGoal: Boolean get() = detoxGoalMinutes < DETOX_GOAL_RANGE.last
}

/** The Sync row: the paired device and its last sync, or an invitation to pair. */
@Immutable
data class SyncSummary(
    val role: SyncRole,
    /** Null while unpaired. */
    val peerName: String? = null,
    val line: SyncLine? = null,
)

/** A draft of a tag. [id] is null for a new one. */
@Immutable
data class TagEditor(
    val id: TagId? = null,
    val name: String = "",
    val color: TagColor = TagColor.Indigo,
    val durations: TimerDurations = TimerDurations(),
    /** False for a new tag and for the last remaining one. */
    val canDelete: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank()

    fun value(setting: TimerSetting): Int = setting.valueIn(durations)

    fun canDecrease(setting: TimerSetting): Boolean = value(setting) > setting.range.first

    fun canIncrease(setting: TimerSetting): Boolean = value(setting) < setting.range.last
}

/** A draft of an activity's name. [id] is null for a new one. */
@Immutable
data class ActivityEditor(
    val id: ActivityId? = null,
    val name: String = "",
) {
    val canSave: Boolean get() = name.isNotBlank()

    val canDelete: Boolean get() = id != null
}

sealed interface SettingsAction {
    /** Re-checks the blocker. Sent on resume, since it's turned on in the system settings. */
    data object Refresh : SettingsAction

    /** Turns the blocker on or off; falls back to the system settings if the app may not. */
    data class SetBlocking(
        val on: Boolean,
    ) : SettingsAction

    data object AddTag : SettingsAction

    data class EditTag(
        val id: TagId,
    ) : SettingsAction

    data class EditName(
        val name: String,
    ) : SettingsAction

    data class EditColor(
        val color: TagColor,
    ) : SettingsAction

    data class Step(
        val setting: TimerSetting,
        val up: Boolean,
    ) : SettingsAction

    data object SaveTag : SettingsAction

    data object DeleteTag : SettingsAction

    data object CloseEditor : SettingsAction

    data class SetDimClockShowWhenLocked(
        val enabled: Boolean,
    ) : SettingsAction

    data class StepDetoxGoal(
        val up: Boolean,
    ) : SettingsAction

    data object AddActivity : SettingsAction

    data class EditActivity(
        val id: ActivityId,
    ) : SettingsAction

    data class EditActivityName(
        val name: String,
    ) : SettingsAction

    data object SaveActivity : SettingsAction

    data object DeleteActivity : SettingsAction

    data object CloseActivityEditor : SettingsAction

    /** Opens the system page where usage access is turned on or off. */
    data object OpenAppUsage : SettingsAction

    /** Removes an app's rule from the activity being edited, at once (not on Save). */
    data class RemoveRule(
        val appKey: String,
    ) : SettingsAction
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val tagRepository: TagRepository,
    private val activityRepository: ActivityRepository,
    private val ruleRepository: ActivityRuleRepository,
    private val appUsage: AppUsageReader,
    blockLists: BlockListRepository,
    private val blocker: Blocker,
    private val setup: BlockerSetup,
    sync: SyncController,
    private val clock: Clock,
    private val features: DeviceFeatures = DeviceFeatures(),
) : ViewModel() {
    private val editor = MutableStateFlow<TagEditor?>(null)
    private val blockingOn = MutableStateFlow<Boolean?>(null)
    private val activityEditor = MutableStateFlow<ActivityEditor?>(null)
    private val appUsageOn = MutableStateFlow<Boolean?>(null)

    // Bumped on Refresh, so "Synced 5 min ago" is recomputed when the screen comes back.
    private val refreshes = MutableStateFlow(0)
    private val syncSummary =
        combine(sync.status, refreshes) { status, _ ->
            when {
                features.syncRole == SyncRole.None -> null
                status is SyncStatus.Paired -> SyncSummary(features.syncRole, status.peer.name, syncLine(status, clock.now()))
                status == SyncStatus.Unpaired -> SyncSummary(features.syncRole)
                else -> null
            }
        }

    private val showAppUsage = features.appUsage && features.appUsageAccess

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settingsRepository.settings,
            tagRepository.tags,
            combine(editor, ruleRepository.rules, ::Pair),
            blockLists.lists,
            combine(blockingOn, syncSummary, activityRepository.activities, activityEditor, appUsageOn, ::ExtraState),
        ) { settings, tags, (editor, rules), lists, extra ->
            SettingsUiState(
                tags = tags,
                editor = editor,
                rules = rules,
                dimClockShowWhenLocked = settings.dimClockShowWhenLocked,
                detoxGoalMinutes = settings.detoxGoal.inWholeMinutes.toInt(),
                showBlocking = features.blocking,
                blockingOn = extra.blockingOn,
                blockLists = lists,
                showLockScreen = features.lockScreen,
                sync = extra.sync,
                activities = extra.activities,
                activityEditor = extra.activityEditor,
                showAppUsage = showAppUsage,
                appUsageOn = extra.appUsageOn,
                appUsageSwitch = appUsage.hasSwitch,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsUiState(
                showBlocking = features.blocking,
                showLockScreen = features.lockScreen,
                showAppUsage = showAppUsage,
                appUsageSwitch = appUsage.hasSwitch,
            ),
        )

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.Refresh -> {
                refreshes.update { it + 1 }
                if (features.blocking) viewModelScope.launch { blockingOn.value = blocker.availability() == Availability.Ready }
                if (features.appUsage) appUsageOn.value = appUsage.isPermitted()
            }

            SettingsAction.OpenAppUsage -> {
                if (appUsage.hasSwitch) {
                    viewModelScope.launch {
                        appUsage.setEnabled(!appUsage.isPermitted())
                        appUsageOn.value = appUsage.isPermitted()
                    }
                } else {
                    appUsage.openSettings()
                }
            }

            is SettingsAction.RemoveRule -> {
                viewModelScope.launch { ruleRepository.clear(action.appKey) }
            }

            SettingsAction.AddActivity -> {
                activityEditor.value = ActivityEditor()
            }

            is SettingsAction.EditActivity -> {
                val activity = uiState.value.activities.firstOrNull { it.id == action.id } ?: return
                activityEditor.value = ActivityEditor(activity.id, activity.name)
            }

            is SettingsAction.EditActivityName -> {
                activityEditor.update { it?.copy(name = action.name.take(ACTIVITY_NAME_MAX_LENGTH)) }
            }

            SettingsAction.CloseActivityEditor -> {
                activityEditor.value = null
            }

            SettingsAction.SaveActivity -> {
                val draft = activityEditor.value?.takeIf { it.canSave } ?: return
                activityEditor.value = null
                viewModelScope.launch {
                    val name = draft.name.trim()
                    if (draft.id == null) activityRepository.add(name) else activityRepository.rename(draft.id, name)
                }
            }

            SettingsAction.DeleteActivity -> {
                val id = activityEditor.value?.id ?: return
                activityEditor.value = null
                viewModelScope.launch { activityRepository.delete(id) }
            }

            is SettingsAction.SetBlocking -> {
                viewModelScope.launch {
                    // Turning off is asynchronous; if it still reads on, the next Refresh corrects it.
                    if (setup.setEnabled(action.on)) {
                        blockingOn.value = blocker.availability() == Availability.Ready
                    } else {
                        setup.openSystemSettings()
                    }
                }
            }

            // Draft edits are synchronous, so the name field never lags behind typing.
            SettingsAction.AddTag -> {
                val used =
                    uiState.value.tags
                        .map { it.color }
                        .toSet()
                editor.value = TagEditor(color = TagColor.entries.firstOrNull { it !in used } ?: TagColor.Indigo)
            }

            is SettingsAction.EditTag -> {
                val tags = uiState.value.tags
                val tag = tags.firstOrNull { it.id == action.id } ?: return
                editor.value = TagEditor(tag.id, tag.name, tag.color, tag.durations, canDelete = tags.size > 1)
            }

            is SettingsAction.EditName -> {
                editor.update { it?.copy(name = action.name.take(TAG_NAME_MAX_LENGTH)) }
            }

            is SettingsAction.EditColor -> {
                editor.update { it?.copy(color = action.color) }
            }

            is SettingsAction.Step -> {
                editor.update { it?.copy(durations = action.setting.stepped(it.durations, action.up)) }
            }

            SettingsAction.CloseEditor -> {
                editor.value = null
            }

            SettingsAction.SaveTag -> {
                val draft = editor.value?.takeIf { it.canSave } ?: return
                editor.value = null
                viewModelScope.launch {
                    val name = draft.name.trim()
                    if (draft.id == null) {
                        tagRepository.add(name, draft.color, draft.durations)
                    } else {
                        tagRepository.update(Tag(draft.id, name, draft.color, draft.durations))
                    }
                }
            }

            SettingsAction.DeleteTag -> {
                val id = editor.value?.takeIf { it.canDelete }?.id ?: return
                editor.value = null
                viewModelScope.launch { tagRepository.delete(id) }
            }

            is SettingsAction.SetDimClockShowWhenLocked -> {
                viewModelScope.launch { settingsRepository.setDimClockShowWhenLocked(action.enabled) }
            }

            is SettingsAction.StepDetoxGoal -> {
                viewModelScope.launch {
                    val current =
                        settingsRepository.settings
                            .first()
                            .detoxGoal.inWholeMinutes
                            .toInt()
                    val next = (current + if (action.up) DETOX_GOAL_STEP else -DETOX_GOAL_STEP).coerceIn(DETOX_GOAL_RANGE)
                    settingsRepository.setDetoxGoal(next.minutes)
                }
            }
        }
    }
}

private data class ExtraState(
    val blockingOn: Boolean?,
    val sync: SyncSummary?,
    val activities: List<Activity>,
    val activityEditor: ActivityEditor?,
    val appUsageOn: Boolean?,
)
