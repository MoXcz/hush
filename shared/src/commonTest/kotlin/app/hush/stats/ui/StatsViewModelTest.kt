// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.stats.ui

import app.hush.activities.Activity
import app.hush.activities.ActivityId
import app.hush.activities.FakeActivityRepository
import app.hush.blocker.ProfileId
import app.hush.core.DeviceFeatures
import app.hush.history.FakeSessionRepository
import app.hush.history.Session
import app.hush.history.SessionId
import app.hush.history.SessionOutcome
import app.hush.history.SessionType
import app.hush.history.ui.HistoryItem
import app.hush.rules.ActivityRule
import app.hush.rules.FakeActivityRuleRepository
import app.hush.stats.DetoxStats
import app.hush.stats.FocusUse
import app.hush.stats.SortedTime
import app.hush.tags.FakeTagRepository
import app.hush.tags.Tag
import app.hush.tags.TagId
import app.hush.tags.defaultTags
import app.hush.timer.TestClock
import app.hush.timer.TestClock.Companion.START
import app.hush.usage.FakeAppUsageReader
import app.hush.usage.FakeAppUsageRepository
import app.hush.usage.SessionApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    private val detox = ProfileId("detox")
    private val tags = FakeTagRepository()
    private val sessions =
        FakeSessionRepository(
            listOf(
                Session(SessionId(1), SessionType.Work, null, 25, START, START + 25.minutes, SessionOutcome.Completed, TagId(1)),
                Session(SessionId(2), SessionType.Detox, detox, 60, START + 1.hours, START + 2.hours, SessionOutcome.Completed),
                Session(SessionId(3), SessionType.Detox, detox, 90, START + 3.hours),
                Session(SessionId(4), SessionType.Work, null, 50, START - 2.hours, START - 1.hours, SessionOutcome.Completed, TagId(3)),
            ),
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val activities = FakeActivityRepository()
    private val appUsage = FakeAppUsageRepository()
    private val reader = FakeAppUsageReader()
    private val rules = FakeActivityRuleRepository()

    private fun TestScope.viewModel(features: DeviceFeatures = DeviceFeatures()): StatsViewModel =
        StatsViewModel(
            sessions,
            tags,
            activities,
            appUsage,
            rules,
            reader,
            TestClock(testScheduler, START + 4.hours),
            TimeZone.UTC,
            features,
        ).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }

    @Test
    fun `lists finished sessions newest first with their tag and leaves out an open attempt`() =
        runTest {
            val state = viewModel().uiState.value
            assertFalse(state.loading)
            assertEquals(
                listOf(
                    HistoryItem(SessionType.Detox, 60, START + 1.hours, SessionOutcome.Completed),
                    HistoryItem(SessionType.Work, 25, START, SessionOutcome.Completed, defaultTags[0]),
                    HistoryItem(SessionType.Work, 50, START - 2.hours, SessionOutcome.Completed, defaultTags[2]),
                ),
                state.history,
            )
        }

    @Test
    fun `shows today's focus time per tag, the total and detox progress`() =
        runTest {
            val state = viewModel().uiState.value
            assertEquals(StatsRange.Days, state.range)
            assertEquals(7, state.buckets.size)
            assertEquals(75.minutes, state.buckets.last().total)
            assertEquals(listOf(TagTotal(defaultTags[2], 50.minutes), TagTotal(defaultTags[0], 25.minutes)), state.totals)
            assertEquals(75.minutes, state.total)
            assertEquals(DetoxStats(longestPassedMinutes = 60, attempts = 1, passed = 1), state.detox)
        }

    @Test
    fun `switches to weeks`() =
        runTest {
            val vm = viewModel()
            vm.onAction(StatsAction.SelectRange(StatsRange.Weeks))
            assertEquals(StatsRange.Weeks, vm.uiState.value.range)
            assertEquals(8, vm.uiState.value.buckets.size)
            assertEquals(
                75.minutes,
                vm.uiState.value.buckets
                    .last()
                    .total,
            )
        }

    @Test
    fun `a deleted tag keeps its name in history and stats`() =
        runTest {
            val vm = viewModel()
            tags.delete(TagId(3))
            val deep =
                vm.uiState.value.totals
                    .first()
                    .tag
            assertEquals("Deep", deep?.name)
            assertTrue(deep?.deleted == true)
            assertEquals(
                deep,
                vm.uiState.value.history
                    .last()
                    .tag,
            )
        }

    @Test
    fun `bar labels stay short`() {
        assertEquals("45m", compactDuration(45.minutes))
        assertEquals("2h", compactDuration(2.hours))
        assertEquals("1h05", compactDuration(65.minutes))
        assertEquals("<1m", compactDuration(44.seconds))
    }

    @Test
    fun `time under a minute is not shown as zero`() {
        assertTrue(isUnderAMinute(12.seconds))
        assertTrue(isUnderAMinute(59.seconds))
        assertFalse(isUnderAMinute(Duration.ZERO))
        assertFalse(isUnderAMinute(1.minutes))
    }

    private fun app(
        pkg: String,
        minutes: Int,
        startedAt: kotlin.time.Instant = START,
        session: String = "s-$pkg",
    ) = SessionApp(session, pkg, pkg.uppercase(), minutes.minutes, startedAt)

    @Test
    fun `apps during focus sum each app over the range, largest first`() =
        runTest {
            appUsage.usage.value = listOf(app("notes", 10, session = "a"), app("notes", 5, session = "b"), app("maps", 20))
            val apps = viewModel().uiState.value.apps
            assertEquals(listOf(FocusUse.App("maps", "MAPS", 20.minutes), FocusUse.App("notes", "NOTES", 15.minutes)), apps)
        }

    @Test
    fun `apps during focus show the top eight and sum the rest as other`() =
        runTest {
            appUsage.usage.value = (1..10).map { app("app$it", it) }
            val apps = viewModel().uiState.value.apps
            assertEquals(9, apps.size)
            assertEquals(10.minutes, apps.first().time)
            assertEquals(FocusUse.Other(3.minutes), apps.last())
        }

    @Test
    fun `apps during focus follow the selected range`() =
        runTest {
            appUsage.usage.value = listOf(app("today", 5), app("lastWeek", 7, startedAt = START - (10 * 24).hours))
            val vm = viewModel()
            assertEquals(
                listOf("today"),
                vm.uiState.value.apps
                    .map { (it as FocusUse.App).packageName },
            )
            vm.onAction(StatsAction.SelectRange(StatsRange.Weeks))
            assertEquals(
                setOf("today", "lastWeek"),
                vm.uiState.value.apps
                    .map { (it as FocusUse.App).packageName }
                    .toSet(),
            )
        }

    @Test
    fun `an activity counts its work phase's focus time next to the apps`() =
        runTest {
            val kobo = Activity(ActivityId(1), "Reading on Kobo")
            activities.rows.value = listOf(kobo)
            sessions.rows.value +=
                Session(
                    SessionId(9),
                    SessionType.Work,
                    null,
                    50,
                    START + 1.hours,
                    START + 1.hours + 50.minutes,
                    SessionOutcome.Completed,
                    TagId(1),
                    kobo.id,
                )
            appUsage.usage.value = listOf(app("dictionary", 3, session = "session-9"))
            val state = viewModel().uiState.value
            assertEquals(listOf(FocusUse.Doing(kobo, 50.minutes), FocusUse.App("dictionary", "DICTIONARY", 3.minutes)), state.apps)
            assertTrue(state.showApps)
        }

    @Test
    fun `apps during focus are hidden on a device that can't record them until something is synced`() =
        runTest {
            val vm = viewModel(DeviceFeatures(appUsage = false))
            assertFalse(vm.uiState.value.showApps)
            appUsage.usage.value = listOf(app("notes", 10))
            assertTrue(vm.uiState.value.showApps)
            vm.onAction(StatsAction.Refresh)
            assertEquals(null, vm.uiState.value.appUsageAccess)
        }

    @Test
    fun `without usage access the section offers to turn it on`() =
        runTest {
            reader.permitted = false
            val vm = viewModel()
            vm.onAction(StatsAction.Refresh)
            assertTrue(vm.uiState.value.showApps)
            assertEquals(false, vm.uiState.value.appUsageAccess)
            vm.onAction(StatsAction.TurnOnAppUsage)
            assertEquals(1, reader.settingsOpened)
            reader.permitted = true
            vm.onAction(StatsAction.Refresh)
            assertEquals(true, vm.uiState.value.appUsageAccess)
        }

    @Test
    fun `tapping an app opens its rule with the current activity and choosing one sorts its time`() =
        runTest {
            val study = activities.add("Study")
            sessions.rows.value +=
                Session(
                    SessionId(9),
                    SessionType.Work,
                    null,
                    25,
                    START + 1.hours,
                    START + 1.hours + 25.minutes,
                    SessionOutcome.Completed,
                    TagId(1),
                    uid = "s9",
                )
            appUsage.usage.value = listOf(app("anki", 10, session = "s9"), app("maps", 5, session = "s9"))
            val vm = viewModel()
            assertFalse(vm.uiState.value.showByActivity)

            vm.onAction(StatsAction.EditRule("anki", "ANKI"))
            assertEquals(RuleEditor("anki", "ANKI", null), vm.uiState.value.ruleEditor)
            vm.onAction(StatsAction.SetRule(study))
            assertNull(vm.uiState.value.ruleEditor)
            assertEquals(listOf(ActivityRule("anki", "ANKI", study)), rules.rules.value)

            val state = vm.uiState.value
            val studyActivity = Activity(study, "Study")
            assertTrue(state.showByActivity)
            assertEquals(listOf(SortedTime(studyActivity, 10.minutes), SortedTime(null, 5.minutes)), state.byActivity)
            assertEquals(mapOf("anki" to studyActivity), state.ruleActivities)

            vm.onAction(StatsAction.EditRule("anki", "ANKI"))
            assertEquals(
                study,
                vm.uiState.value.ruleEditor
                    ?.activityId,
            )
            vm.onAction(StatsAction.SetRule(null))
            assertEquals(emptyList(), rules.rules.value)
            assertFalse(vm.uiState.value.showByActivity)
        }

    @Test
    fun `closing the rule sheet changes nothing and only live activities can be chosen`() =
        runTest {
            val kobo = activities.add("Kobo")
            val gone = activities.add("Gone")
            activities.delete(gone)
            val vm = viewModel()
            vm.onAction(StatsAction.EditRule("anki", "Anki"))
            vm.onAction(StatsAction.CloseRuleEditor)
            assertNull(vm.uiState.value.ruleEditor)
            assertEquals(emptyList(), rules.rules.value)
            assertEquals(
                listOf(kobo),
                vm.uiState.value.choosableActivities
                    .map { it.id },
            )
        }
}
