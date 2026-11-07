// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.stats.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hush.activities.Activity
import app.hush.activities.ActivityId
import app.hush.activities.ActivityRepository
import app.hush.core.DeviceFeatures
import app.hush.history.Session
import app.hush.history.SessionRepository
import app.hush.history.ui.HistoryItem
import app.hush.rules.ActivityRule
import app.hush.rules.ActivityRuleRepository
import app.hush.stats.DetoxStats
import app.hush.stats.FocusApps
import app.hush.stats.FocusBucket
import app.hush.stats.FocusByActivity
import app.hush.stats.FocusStats
import app.hush.stats.FocusUse
import app.hush.stats.SortedTime
import app.hush.stats.totalsByTag
import app.hush.tags.Tag
import app.hush.tags.TagId
import app.hush.tags.TagRepository
import app.hush.usage.AppUsageReader
import app.hush.usage.AppUsageRepository
import app.hush.usage.SessionApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Clock
import kotlin.time.Duration

enum class StatsRange { Days, Weeks }

@Immutable
data class TagTotal(
    /** Null for work recorded without a tag. */
    val tag: Tag?,
    val time: Duration,
)

@Immutable
data class StatsUiState(
    val loading: Boolean = true,
    val range: StatsRange = StatsRange.Days,
    /** Oldest first: the last 7 days, or the last 8 weeks. */
    val buckets: List<FocusBucket> = emptyList(),
    /** Per tag over [buckets], largest first. */
    val totals: List<TagTotal> = emptyList(),
    val total: Duration = Duration.ZERO,
    /** Every tag, deleted ones included, to colour the bars. */
    val tags: Map<TagId, Tag> = emptyMap(),
    val detox: DetoxStats = DetoxStats(),
    /** Finished sessions, newest first. */
    val history: List<HistoryItem> = emptyList(),
    /** "Apps during focus" is shown: this device records apps, or some came with a sync, or activities were picked. */
    val showApps: Boolean = false,
    /** Apps and activities over the selected range, largest first, then the rest as one line. */
    val apps: List<FocusUse> = emptyList(),
    /** False while usage access is off on a device that could record apps; null when unknown or not applicable. */
    val appUsageAccess: Boolean? = null,
    /** Time per activity: phases where it was picked, plus apps sorted into it by the rules; then the unsorted rest. */
    val byActivity: List<SortedTime> = emptyList(),
    /** The activity each app key is sorted into. */
    val ruleActivities: Map<String, Activity> = emptyMap(),
    /** Activities a rule can point at. */
    val choosableActivities: List<Activity> = emptyList(),
    /** The row whose rule is being chosen, in a sheet; null when the sheet is closed. */
    val ruleEditor: RuleEditor? = null,
) {
    /** "By activity" is shown once a rule exists and some time in the range is sorted. */
    val showByActivity: Boolean get() = ruleActivities.isNotEmpty() && byActivity.any { it.activity != null }
}

@Immutable
data class RuleEditor(
    val appKey: String,
    val label: String,
    val activityId: ActivityId? = null,
)

sealed interface StatsAction {
    /** Re-checks usage access; sent on resume, since it's granted in the system settings. */
    data object Refresh : StatsAction

    /** Turns recording on: Hush's own switch where it has one, else the system's usage access page. */
    data object TurnOnAppUsage : StatsAction

    data class SelectRange(
        val range: StatsRange,
    ) : StatsAction

    /** Opens the sheet to sort an app, site or program into an activity. */
    data class EditRule(
        val appKey: String,
        val label: String,
    ) : StatsAction

    /** Sorts the editor's app into [activity], or removes its rule when null, and closes the sheet. */
    data class SetRule(
        val activity: ActivityId?,
    ) : StatsAction

    data object CloseRuleEditor : StatsAction
}

class StatsViewModel(
    sessions: SessionRepository,
    tagRepository: TagRepository,
    activityRepository: ActivityRepository,
    appUsage: AppUsageRepository,
    private val rules: ActivityRuleRepository,
    private val reader: AppUsageReader,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val features: DeviceFeatures = DeviceFeatures(),
) : ViewModel() {
    private val range = MutableStateFlow(StatsRange.Days)
    private val access = MutableStateFlow<Boolean?>(null)
    private val ruleEditor = MutableStateFlow<RuleEditor?>(null)

    val uiState: StateFlow<StatsUiState> =
        combine(
            combine(sessions.history, tagRepository.allTags, range, ::Triple),
            combine(activityRepository.allActivities, appUsage.usage, rules.rules, ::Triple),
            access,
            ruleEditor,
        ) { (history, tags, range), (activities, apps, rules), access, editor ->
            build(history, tags.associateBy { it.id }, range, activities, apps, rules, access).copy(ruleEditor = editor)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun onAction(action: StatsAction) {
        when (action) {
            is StatsAction.SelectRange -> {
                range.value = action.range
            }

            StatsAction.Refresh -> {
                if (features.appUsage) access.value = reader.isPermitted()
            }

            StatsAction.TurnOnAppUsage -> {
                if (reader.hasSwitch) {
                    viewModelScope.launch {
                        reader.setEnabled(true)
                        access.value = reader.isPermitted()
                    }
                } else {
                    reader.openSettings()
                }
            }

            is StatsAction.EditRule -> {
                ruleEditor.value = RuleEditor(action.appKey, action.label, uiState.value.ruleActivities[action.appKey]?.id)
            }

            StatsAction.CloseRuleEditor -> {
                ruleEditor.value = null
            }

            is StatsAction.SetRule -> {
                setRule(action.activity)
            }
        }
    }

    private fun setRule(activity: ActivityId?) {
        val editor = ruleEditor.value ?: return
        ruleEditor.value = null
        viewModelScope.launch {
            if (activity == null) rules.clear(editor.appKey) else rules.set(editor.appKey, editor.label, activity)
        }
    }

    private fun build(
        history: List<Session>,
        tags: Map<TagId, Tag>,
        range: StatsRange,
        activities: List<Activity>,
        apps: List<SessionApp>,
        rules: List<ActivityRule>,
        access: Boolean?,
    ): StatsUiState {
        val now = clock.now()
        val buckets =
            when (range) {
                StatsRange.Days -> FocusStats.days(history, now, timeZone)
                StatsRange.Weeks -> FocusStats.weeks(history, now, timeZone)
            }
        val totals = buckets.totalsByTag().map { (id, time) -> TagTotal(id?.let(tags::get), time) }
        val activitiesById = activities.associateBy { it.id }
        val since = buckets.first().start.atStartOfDayIn(timeZone)
        return StatsUiState(
            loading = false,
            range = range,
            buckets = buckets,
            totals = totals,
            total = totals.fold(Duration.ZERO) { sum, it -> sum + it.time },
            tags = tags,
            detox = FocusStats.detox(history),
            history = history.mapNotNull { session -> session.toItem(tags) },
            showApps = features.appUsage || apps.isNotEmpty() || history.any { it.activityId != null },
            apps =
                FocusApps.since(
                    since = since,
                    sessions = history,
                    apps = apps,
                    activities = activitiesById,
                ),
            appUsageAccess = access.takeIf { features.appUsage },
            byActivity = FocusByActivity.since(since, history, apps, rules, activitiesById),
            ruleActivities =
                rules
                    .mapNotNull { rule ->
                        rule.activityId
                            ?.let(activitiesById::get)
                            ?.takeIf { !it.deleted }
                            ?.let { rule.appKey to it }
                    }.toMap(),
            choosableActivities = activities.filter { !it.deleted },
        )
    }
}

private fun Session.toItem(tags: Map<TagId, Tag>): HistoryItem? =
    outcome?.let { HistoryItem(type, plannedMinutes, startedAt, it, tagId?.let(tags::get)) }
