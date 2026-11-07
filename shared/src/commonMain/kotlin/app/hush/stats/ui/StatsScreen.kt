// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.stats.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hush.activities.Activity
import app.hush.activities.ActivityId
import app.hush.detox.ui.levelLabel
import app.hush.history.SessionOutcome
import app.hush.history.SessionType
import app.hush.history.ui.HistoryItem
import app.hush.history.ui.HistoryRow
import app.hush.resources.Res
import app.hush.resources.activity_deleted
import app.hush.resources.history_empty
import app.hush.resources.history_title
import app.hush.resources.ic_activity
import app.hush.resources.ic_check
import app.hush.resources.months_short
import app.hush.resources.stats_activity
import app.hush.resources.stats_apps
import app.hush.resources.stats_apps_empty
import app.hush.resources.stats_apps_off
import app.hush.resources.stats_apps_other
import app.hush.resources.stats_apps_turn_on
import app.hush.resources.stats_attempts
import app.hush.resources.stats_back
import app.hush.resources.stats_by_activity
import app.hush.resources.stats_days
import app.hush.resources.stats_detox
import app.hush.resources.stats_focus
import app.hush.resources.stats_highest_level
import app.hush.resources.stats_none_yet
import app.hush.resources.stats_passed
import app.hush.resources.stats_rule_choose
import app.hush.resources.stats_rule_no_activities
import app.hush.resources.stats_rule_none
import app.hush.resources.stats_rule_title
import app.hush.resources.stats_title
import app.hush.resources.stats_total
import app.hush.resources.stats_under_a_minute
import app.hush.resources.stats_unsorted
import app.hush.resources.stats_untagged
import app.hush.resources.stats_week_label
import app.hush.resources.stats_weeks
import app.hush.resources.weekdays_short
import app.hush.stats.DetoxStats
import app.hush.stats.FocusApps
import app.hush.stats.FocusBucket
import app.hush.stats.FocusByActivity
import app.hush.stats.FocusUse
import app.hush.stats.SortedTime
import app.hush.tags.Tag
import app.hush.tags.TagColor
import app.hush.tags.TagId
import app.hush.tags.ui.TagDot
import app.hush.tags.ui.tagName
import app.hush.timer.TimerDurations
import app.hush.ui.BackButton
import app.hush.ui.ScreenTitle
import app.hush.ui.ScreenTopBar
import app.hush.ui.theme.HushTheme
import app.hush.ui.theme.color
import app.hush.ui.theme.segmentShape
import app.hush.ui.theme.segmentedColors
import app.hush.ui.theme.segmentedShapes
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Composable
fun StatsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(StatsAction.Refresh)
        onPauseOrDispose {}
    }
    StatsScreen(state = state, onAction = viewModel::onAction, onBack = onBack, modifier = modifier)
}

@Composable
fun StatsScreen(
    state: StatsUiState,
    onAction: (StatsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    state.ruleEditor?.let { RuleSheet(editor = it, activities = state.choosableActivities, onAction = onAction) }
    // Background first, so it also fills the space the caller pads out for the bottom bar.
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).then(modifier)) {
        ScreenTopBar(
            title = { ScreenTitle(stringResource(Res.string.stats_title)) },
            navigationIcon = {
                BackButton(stringResource(Res.string.stats_back), onClick = onBack, modifier = Modifier.padding(start = 8.dp))
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "focus") {
                FocusCard(state = state, onAction = onAction)
            }
            if (state.showApps) {
                item(key = "apps") {
                    AppsSection(state = state, onAction = onAction)
                }
            }
            if (state.showByActivity) {
                item(key = "by-activity") {
                    ByActivitySection(state.byActivity, Modifier.animateItem())
                }
            }
            item(key = "detox") {
                DetoxSection(state.detox)
            }
            item(key = "history-title") { SectionTitle(stringResource(Res.string.history_title)) }
            if (!state.loading && state.history.isEmpty()) {
                item(key = "history-empty") {
                    Text(
                        stringResource(Res.string.history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }
            val count = state.history.size
            itemsIndexed(state.history) { index, item ->
                HistoryRow(item, shape = segmentShape(index, count), modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun FocusCard(
    state: StatsUiState,
    onAction: (StatsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionTitle(stringResource(Res.string.stats_focus))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceBright) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RangeToggle(range = state.range, onSelect = { onAction(StatsAction.SelectRange(it)) })
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        timeLabel(state.total),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.stats_total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                FocusBars(buckets = state.buckets, order = state.totals.map { it.tag?.id }, tags = state.tags, range = state.range)
                state.totals.forEach { total -> TagTotalRow(total) }
            }
        }
    }
}

@Composable
private fun RangeToggle(
    range: StatsRange,
    onSelect: (StatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        StatsRange.entries.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == range,
                onCheckedChange = { onSelect(option) },
                shapes =
                    if (index == 0) {
                        ButtonGroupDefaults.connectedLeadingButtonShapes()
                    } else {
                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                    },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) {
                Text(stringResource(if (option == StatsRange.Days) Res.string.stats_days else Res.string.stats_weeks))
            }
        }
    }
}

/** Stacked columns, one per day or week, split by tag in the order of the totals below them. */
@Composable
private fun FocusBars(
    buckets: List<FocusBucket>,
    order: List<TagId?>,
    tags: Map<TagId, Tag>,
    range: StatsRange,
    modifier: Modifier = Modifier,
) {
    val max = buckets.maxOfOrNull { it.total }?.takeIf { it > Duration.ZERO } ?: 1.minutes
    val weekdays = stringArrayResource(Res.array.weekdays_short)
    val months = stringArrayResource(Res.array.months_short)
    val untagged = MaterialTheme.colorScheme.outline
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        buckets.forEach { bucket ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (bucket.total > Duration.ZERO) compactDuration(bucket.total) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                val share by animateFloatAsState(
                    (bucket.total / max).toFloat(),
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                )
                Box(Modifier.height(BAR_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(share.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(6.dp)),
                    ) {
                        order.forEach { id ->
                            val time = bucket.byTag[id] ?: return@forEach
                            val color = id?.let(tags::get)?.color?.color() ?: untagged
                            Box(Modifier.fillMaxWidth().weight((time / bucket.total).toFloat().coerceAtLeast(0.001f)).background(color))
                        }
                    }
                }
                Text(
                    when (range) {
                        StatsRange.Days -> weekdays.getOrElse(bucket.start.dayOfWeek.isoDayNumber - 1) { "" }
                        StatsRange.Weeks -> weekLabel(bucket.start, months)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun weekLabel(
    start: LocalDate,
    months: List<String>,
): String = stringResource(Res.string.stats_week_label, start.day, months.getOrElse(start.month.number - 1) { "" })

@Composable
private fun TagTotalRow(
    total: TagTotal,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val tag = total.tag
        if (tag != null) TagDot(tag.color) else TagDotColor(MaterialTheme.colorScheme.outline)
        Text(
            tag?.let { tagName(it) } ?: stringResource(Res.string.stats_untagged),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        Text(timeLabel(total.time), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TagDotColor(color: Color) {
    Box(
        Modifier
            .width(12.dp)
            .height(12.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}

@Composable
private fun AppsSection(
    state: StatsUiState,
    onAction: (StatsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionTitle(stringResource(Res.string.stats_apps))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceBright) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.apps.zip(FocusApps.bars(state.apps)).forEach { (use, bar) ->
                    val appKey = (use as? FocusUse.App)?.packageName
                    FocusUseRow(
                        use = use,
                        bar = bar,
                        ruleActivity = appKey?.let(state.ruleActivities::get),
                        onClick = appKey?.let { { label: String -> onAction(StatsAction.EditRule(it, label)) } },
                    )
                }
                if (!state.loading && state.apps.isEmpty()) {
                    QuietLine(stringResource(Res.string.stats_apps_empty), Modifier.padding(8.dp))
                }
                if (state.appUsageAccess == false) {
                    Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        QuietLine(stringResource(Res.string.stats_apps_off), Modifier.weight(1f))
                        TextButton(onClick = { onAction(StatsAction.TurnOnAppUsage) }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.stats_apps_turn_on))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusUseRow(
    use: FocusUse,
    bar: Float,
    ruleActivity: Activity?,
    onClick: ((label: String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // The rule keeps the stored name, not the "(deleted)" display form.
    val storedLabel =
        when (use) {
            is FocusUse.App -> use.label
            is FocusUse.Doing -> use.activity.name
            is FocusUse.Other -> ""
        }
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable { onClick(storedLabel) } else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (use is FocusUse.Doing) {
                Icon(
                    painterResource(Res.drawable.ic_activity),
                    contentDescription = stringResource(Res.string.stats_activity),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp).width(18.dp).height(18.dp),
                )
            }
            Text(
                when (use) {
                    is FocusUse.App -> use.label
                    is FocusUse.Doing -> activityName(use.activity)
                    is FocusUse.Other -> stringResource(Res.string.stats_apps_other)
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (ruleActivity != null) {
                Text(
                    ruleActivity.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp).widthIn(max = 120.dp),
                )
            }
            Text(timeLabel(use.time), style = MaterialTheme.typography.bodyLarge)
        }
        ThinBar(
            share = bar,
            color = if (use is FocusUse.Other) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun ThinBar(
    share: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(share.coerceIn(0f, 1f), animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec())
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

/** Time per activity: picked on the timer or sorted by the rules, then the unsorted rest. */
@Composable
private fun ByActivitySection(
    lines: List<SortedTime>,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionTitle(stringResource(Res.string.stats_by_activity))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceBright) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                lines.zip(FocusByActivity.bars(lines)).forEach { (line, bar) ->
                    val activity = line.activity
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(Res.drawable.ic_activity),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(18.dp).height(18.dp),
                            )
                            Text(
                                activity?.let { activityName(it) } ?: stringResource(Res.string.stats_unsorted),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                            )
                            Text(timeLabel(line.time), style = MaterialTheme.typography.bodyLarge)
                        }
                        ThinBar(
                            share = bar,
                            color = if (activity != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

/** Chooses the activity an app, site or program is sorted into, or none. */
@Composable
private fun RuleSheet(
    editor: RuleEditor,
    activities: List<Activity>,
    onAction: (StatsAction) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val choose: (ActivityId?) -> Unit = { activity ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { onAction(StatsAction.SetRule(activity)) }
    }
    ModalBottomSheet(
        onDismissRequest = { onAction(StatsAction.CloseRuleEditor) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(Res.string.stats_rule_title, editor.label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
            Text(
                stringResource(Res.string.stats_rule_choose),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            if (activities.isEmpty()) {
                QuietLine(
                    stringResource(Res.string.stats_rule_no_activities),
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
            val rows = activities.size + 1
            activities.forEachIndexed { index, activity ->
                val selected = activity.id == editor.activityId
                SegmentedListItem(
                    selected = selected,
                    onClick = { choose(activity.id) },
                    shapes = segmentedShapes(index, rows),
                    colors = segmentedColors,
                    leadingContent = { Icon(painterResource(Res.drawable.ic_activity), contentDescription = null) },
                    trailingContent = { if (selected) Icon(painterResource(Res.drawable.ic_check), contentDescription = null) },
                ) { Text(activity.name) }
            }
            SegmentedListItem(
                selected = editor.activityId == null,
                onClick = { choose(null) },
                shapes = segmentedShapes(rows - 1, rows),
                colors = segmentedColors,
                trailingContent = {
                    if (editor.activityId == null) Icon(painterResource(Res.drawable.ic_check), contentDescription = null)
                },
            ) { Text(stringResource(Res.string.stats_rule_none)) }
        }
    }
}

@Composable
private fun activityName(activity: Activity): String =
    if (activity.deleted) stringResource(Res.string.activity_deleted, activity.name) else activity.name

@Composable
private fun QuietLine(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun DetoxSection(
    detox: DetoxStats,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionTitle(stringResource(Res.string.stats_detox))
        val rows =
            listOf(
                stringResource(Res.string.stats_highest_level) to
                    (detox.longestPassedMinutes?.let { levelLabel(it) } ?: stringResource(Res.string.stats_none_yet)),
                stringResource(Res.string.stats_attempts) to detox.attempts.toString(),
                stringResource(Res.string.stats_passed) to detox.passed.toString(),
            )
        rows.forEachIndexed { index, (label, value) ->
            ListItem(
                modifier = Modifier.clip(segmentShape(index, rows.size)),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                headlineContent = { Text(label) },
                trailingContent = { Text(value, style = MaterialTheme.typography.titleMedium) },
            )
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

/** "1 h 30 min", "45 min" or, for a few seconds, "<1 min" instead of "0 min". */
@Composable
private fun timeLabel(duration: Duration): String =
    if (isUnderAMinute(duration)) stringResource(Res.string.stats_under_a_minute) else levelLabel(duration.inWholeMinutes.toInt())

internal fun isUnderAMinute(duration: Duration): Boolean = duration > Duration.ZERO && duration < 1.minutes

/** "45m", "2h", "1h20": short enough to sit above a narrow bar. */
internal fun compactDuration(duration: Duration): String {
    val minutes = duration.inWholeMinutes
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        isUnderAMinute(duration) -> "<1m"
        hours == 0L -> "${rest}m"
        rest == 0L -> "${hours}h"
        else -> "${hours}h${rest.toString().padStart(2, '0')}"
    }
}

private val BAR_HEIGHT = 140.dp

@Preview
@Composable
private fun StatsScreenPreview() {
    val focus = Tag(TagId(1), "Focus", TagColor.Indigo, TimerDurations())
    val deep = Tag(TagId(3), "Deep", TagColor.Plum, TimerDurations(work = 50.minutes))
    val start = LocalDate(2026, 11, 2)
    HushTheme {
        StatsScreen(
            state =
                StatsUiState(
                    loading = false,
                    buckets =
                        (0 until 7).map { day ->
                            FocusBucket(
                                LocalDate(start.year, start.month, start.day + day),
                                mapOf(focus.id to (25 * day).minutes, deep.id to (50 * (day % 3)).minutes),
                            )
                        },
                    totals = listOf(TagTotal(focus, 525.minutes), TagTotal(deep, 300.minutes)),
                    total = 825.minutes,
                    tags = mapOf(focus.id to focus, deep.id to deep),
                    detox = DetoxStats(90, 4, 2),
                    showApps = true,
                    apps =
                        listOf(
                            FocusUse.Doing(Activity(ActivityId(1), "Reading on Kobo"), 150.minutes),
                            FocusUse.App("org.example.notes", "Notes", 62.minutes),
                            FocusUse.App("org.example.dictionary", "Dictionary", 9.minutes),
                            FocusUse.Other(4.minutes),
                        ),
                    ruleActivities = mapOf("org.example.notes" to Activity(ActivityId(2), "Study")),
                    byActivity =
                        listOf(
                            SortedTime(Activity(ActivityId(1), "Reading on Kobo"), 150.minutes),
                            SortedTime(Activity(ActivityId(2), "Study"), 62.minutes),
                            SortedTime(null, 13.minutes),
                        ),
                    history =
                        listOf(
                            HistoryItem(SessionType.Work, 25, Instant.parse("2026-11-05T09:00:00Z"), SessionOutcome.Completed, focus),
                            HistoryItem(SessionType.Detox, 90, Instant.parse("2026-11-04T18:00:00Z"), SessionOutcome.Completed),
                        ),
                ),
            onAction = {},
            onBack = {},
        )
    }
}
