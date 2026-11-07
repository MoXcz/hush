// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.settings.ui

import app.hush.activities.ActivityId
import app.hush.activities.FakeActivityRepository
import app.hush.blocker.Availability
import app.hush.blocker.BlockerSetup
import app.hush.blocker.fake.FakeBlocker
import app.hush.blocklists.FakeBlockListRepository
import app.hush.core.DeviceFeatures
import app.hush.rules.ActivityRule
import app.hush.rules.FakeActivityRuleRepository
import app.hush.settings.FakeSettingsRepository
import app.hush.sync.COMPUTER
import app.hush.sync.FakeDeviceIdentity
import app.hush.sync.FakePeerStore
import app.hush.sync.FakeSyncScheduler
import app.hush.sync.FakeSyncStore
import app.hush.sync.FakeSyncTransport
import app.hush.sync.PeerState
import app.hush.sync.SyncController
import app.hush.sync.SyncOutcome
import app.hush.sync.SyncRole
import app.hush.sync.ui.SyncLine
import app.hush.tags.FakeTagRepository
import app.hush.tags.TAG_NAME_MAX_LENGTH
import app.hush.tags.Tag
import app.hush.tags.TagColor
import app.hush.tags.TagId
import app.hush.tags.defaultTags
import app.hush.timer.TestClock
import app.hush.timer.TimerDurations
import app.hush.usage.FakeAppUsageReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val settings = FakeSettingsRepository()
    private val tags = FakeTagRepository()
    private val blockLists = FakeBlockListRepository(deepWork = setOf("a", "b"), detox = emptySet())
    private val blocker = FakeBlocker(availability = Availability.NeedsApproval)
    private val activities = FakeActivityRepository()
    private var usageReader = FakeAppUsageReader(permitted = false)
    private val rules = FakeActivityRuleRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val peers = FakePeerStore()

    private fun TestScope.viewModel(
        features: DeviceFeatures = DeviceFeatures(),
        setup: BlockerSetup = blocker,
    ): SettingsViewModel {
        val clock = TestClock(testScheduler)
        val sync =
            SyncController(
                FakeSyncStore(),
                peers,
                FakeSyncTransport(),
                FakeSyncScheduler(),
                FakeDeviceIdentity(),
                clock,
                CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            )
        return SettingsViewModel(
            settings,
            tags,
            activities,
            rules,
            usageReader,
            blockLists,
            blocker,
            setup,
            sync,
            clock,
            features,
        ).also { vm -> backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} } }
    }

    private val editor get() = assertNotNull(vm.uiState.value.editor)

    private lateinit var vm: SettingsViewModel

    @Test
    fun `lists the tags and opens one for editing`() =
        runTest {
            vm = viewModel()
            assertEquals(
                listOf("Focus", "Light", "Deep"),
                vm.uiState.value.tags
                    .map { it.name },
            )
            vm.onAction(SettingsAction.EditTag(TagId(2)))
            assertEquals(TagEditor(TagId(2), "Light", TagColor.Teal, defaultTags[1].durations, canDelete = true), editor)
        }

    @Test
    fun `steps each value by its own step and saves the edited tag`() =
        runTest {
            vm = viewModel()
            vm.onAction(SettingsAction.EditTag(TagId(1)))
            vm.onAction(SettingsAction.Step(TimerSetting.Work, up = true))
            vm.onAction(SettingsAction.Step(TimerSetting.ShortBreak, up = false))
            vm.onAction(SettingsAction.Step(TimerSetting.LongBreak, up = true))
            vm.onAction(SettingsAction.Step(TimerSetting.LongBreakAfter, up = true))
            vm.onAction(SettingsAction.EditName("  Writing "))
            vm.onAction(SettingsAction.EditColor(TagColor.Sage))
            // Nothing is stored until Save.
            assertEquals(defaultTags[0], tags.rows.value[0])
            vm.onAction(SettingsAction.SaveTag)
            assertNull(vm.uiState.value.editor)
            assertEquals(Tag(TagId(1), "Writing", TagColor.Sage, TimerDurations(30.minutes, 4.minutes, 20.minutes, 5)), tags.rows.value[0])
        }

    @Test
    fun `work steps by single minutes below five`() =
        runTest {
            tags.setDurations(TagId(1), TimerDurations(work = 5.minutes))
            vm = viewModel()
            vm.onAction(SettingsAction.EditTag(TagId(1)))
            vm.onAction(SettingsAction.Step(TimerSetting.Work, up = false))
            assertEquals(4, editor.value(TimerSetting.Work))
            repeat(4) { vm.onAction(SettingsAction.Step(TimerSetting.Work, up = false)) }
            assertEquals(1, editor.value(TimerSetting.Work))
            assertFalse(editor.canDecrease(TimerSetting.Work))
            repeat(4) { vm.onAction(SettingsAction.Step(TimerSetting.Work, up = true)) }
            vm.onAction(SettingsAction.Step(TimerSetting.Work, up = true))
            assertEquals(10, editor.value(TimerSetting.Work))
        }

    @Test
    fun `steps stop at the ends of the range`() =
        runTest {
            tags.setDurations(TagId(1), TimerDurations(work = 120.minutes, shortBreak = 1.minutes, longBreakAfter = 2))
            vm = viewModel()
            vm.onAction(SettingsAction.EditTag(TagId(1)))
            assertFalse(editor.canIncrease(TimerSetting.Work))
            assertFalse(editor.canDecrease(TimerSetting.ShortBreak))
            assertFalse(editor.canDecrease(TimerSetting.LongBreakAfter))
            assertTrue(editor.canDecrease(TimerSetting.Work))
            vm.onAction(SettingsAction.Step(TimerSetting.Work, up = true))
            vm.onAction(SettingsAction.Step(TimerSetting.ShortBreak, up = false))
            assertEquals(120, editor.value(TimerSetting.Work))
            assertEquals(1, editor.value(TimerSetting.ShortBreak))
        }

    @Test
    fun `a new tag needs a name, gets an unused colour and the default durations`() =
        runTest {
            vm = viewModel()
            vm.onAction(SettingsAction.AddTag)
            assertEquals(TagEditor(color = TagColor.Sage), editor)
            assertFalse(editor.canSave)
            vm.onAction(SettingsAction.SaveTag)
            assertEquals(3, tags.rows.value.size)
            vm.onAction(SettingsAction.EditName("Reading, but with a very long name"))
            assertEquals(TAG_NAME_MAX_LENGTH, editor.name.length)
            vm.onAction(SettingsAction.SaveTag)
            assertEquals(
                TimerDurations(),
                tags.rows.value
                    .last()
                    .durations,
            )
            assertEquals(4, vm.uiState.value.tags.size)
        }

    @Test
    fun `deleting hides the tag but keeps it, and the last tag can't be deleted`() =
        runTest {
            vm = viewModel()
            vm.onAction(SettingsAction.EditTag(TagId(2)))
            vm.onAction(SettingsAction.DeleteTag)
            assertEquals(
                listOf("Focus", "Deep"),
                vm.uiState.value.tags
                    .map { it.name },
            )
            assertTrue(tags.rows.value[1].deleted)
            vm.onAction(SettingsAction.EditTag(TagId(3)))
            vm.onAction(SettingsAction.DeleteTag)
            vm.onAction(SettingsAction.EditTag(TagId(1)))
            assertFalse(editor.canDelete)
            vm.onAction(SettingsAction.DeleteTag)
            assertEquals(
                listOf("Focus"),
                vm.uiState.value.tags
                    .map { it.name },
            )
        }

    @Test
    fun `closing the editor discards the draft`() =
        runTest {
            vm = viewModel()
            vm.onAction(SettingsAction.EditTag(TagId(1)))
            vm.onAction(SettingsAction.EditName("Changed"))
            vm.onAction(SettingsAction.CloseEditor)
            assertNull(vm.uiState.value.editor)
            assertEquals("Focus", tags.rows.value[0].name)
        }

    @Test
    fun `toggles the dim clock lock screen setting`() =
        runTest {
            vm = viewModel()
            vm.onAction(SettingsAction.SetDimClockShowWhenLocked(true))
            assertTrue(vm.uiState.value.dimClockShowWhenLocked)
        }

    @Test
    fun `the detox goal steps by 30 minutes between 1 h and 8 h`() =
        runTest {
            vm = viewModel()
            assertEquals(240, vm.uiState.value.detoxGoalMinutes)
            vm.onAction(SettingsAction.StepDetoxGoal(up = true))
            assertEquals(270.minutes, settings.settings.value.detoxGoal)
            repeat(20) { vm.onAction(SettingsAction.StepDetoxGoal(up = true)) }
            assertEquals(480.minutes, settings.settings.value.detoxGoal)
            assertFalse(vm.uiState.value.canIncreaseGoal)
            repeat(20) { vm.onAction(SettingsAction.StepDetoxGoal(up = false)) }
            assertEquals(60.minutes, settings.settings.value.detoxGoal)
            assertFalse(vm.uiState.value.canDecreaseGoal)
            assertTrue(vm.uiState.value.canIncreaseGoal)
        }

    @Test
    fun `blocking and lock-screen settings are hidden where the device has neither`() =
        runTest {
            vm = viewModel()
            assertTrue(vm.uiState.value.showBlocking)
            assertTrue(vm.uiState.value.showLockScreen)
            vm = viewModel(DeviceFeatures(blocking = false, lockScreen = false))
            assertFalse(vm.uiState.value.showBlocking)
            assertFalse(vm.uiState.value.showLockScreen)
        }

    @Test
    fun `shows the block lists and whether blocking is on, re-checked on refresh`() =
        runTest {
            vm = viewModel()
            assertEquals(
                listOf("Deep work" to 2, "Detox" to 0),
                vm.uiState.value.blockLists
                    .map { it.name to it.packages.size },
            )
            assertNull(vm.uiState.value.blockingOn)
            vm.onAction(SettingsAction.Refresh)
            assertEquals(false, vm.uiState.value.blockingOn)
            blocker.availability = Availability.Ready
            vm.onAction(SettingsAction.Refresh)
            assertEquals(true, vm.uiState.value.blockingOn)
        }

    @Test
    fun `refresh doesn't ask the blocker where nothing can be blocked`() =
        runTest {
            vm = viewModel(DeviceFeatures(blocking = false, lockScreen = false))
            vm.onAction(SettingsAction.Refresh)
            assertNull(vm.uiState.value.blockingOn)
            assertTrue(blocker.calls.isEmpty())
        }

    @Test
    fun `the blocking switch turns the blocker on and off`() =
        runTest {
            vm = viewModel()
            vm.onAction(SettingsAction.SetBlocking(true))
            assertEquals(true, vm.uiState.value.blockingOn)
            vm.onAction(SettingsAction.SetBlocking(false))
            assertEquals(false, vm.uiState.value.blockingOn)
            assertEquals(listOf("setEnabled:true", "availability", "setEnabled:false", "availability"), blocker.calls)
        }

    @Test
    fun `the blocking switch shows what the blocker reports after switching`() =
        runTest {
            blocker.availability = Availability.Ready
            val slowOff =
                object : BlockerSetup by blocker {
                    override suspend fun setEnabled(on: Boolean) = true
                }
            vm = viewModel(setup = slowOff)
            vm.onAction(SettingsAction.SetBlocking(false))
            assertEquals(true, vm.uiState.value.blockingOn)
        }

    @Test
    fun `the blocking switch opens the system settings when the app may not switch it`() =
        runTest {
            blocker.availability = Availability.Ready
            blocker.canSetEnabled = false
            vm = viewModel()
            vm.onAction(SettingsAction.Refresh)
            vm.onAction(SettingsAction.SetBlocking(false))
            assertEquals(true, vm.uiState.value.blockingOn)
            assertEquals(listOf("availability", "setEnabled:false", "openSystemSettings"), blocker.calls)
        }

    @Test
    fun `the sync row is hidden where there's no sync`() =
        runTest {
            vm = viewModel()
            assertNull(vm.uiState.value.sync)
        }

    @Test
    fun `the sync row invites to pair, then shows the peer and its last sync`() =
        runTest {
            vm = viewModel(DeviceFeatures(syncRole = SyncRole.Client))
            assertEquals(SyncSummary(SyncRole.Client), vm.uiState.value.sync)
            peers.current.value = PeerState(COMPUTER, TestClock.START, SyncOutcome.Synced)
            assertEquals(SyncSummary(SyncRole.Client, "arch", SyncLine.Synced(0)), vm.uiState.value.sync)
            advanceTimeBy(5.minutes)
            vm.onAction(SettingsAction.Refresh)
            assertEquals(
                SyncLine.Synced(5),
                vm.uiState.value.sync
                    ?.line,
            )
        }

    @Test
    fun `the app usage row shows whether usage access is on, re-checked on resume`() =
        runTest {
            vm = viewModel()
            vm.onAction(SettingsAction.Refresh)
            assertEquals(false, vm.uiState.value.appUsageOn)
            usageReader.permitted = true
            vm.onAction(SettingsAction.Refresh)
            assertEquals(true, vm.uiState.value.appUsageOn)
        }

    @Test
    fun `the app usage row opens the system's usage access page`() =
        runTest {
            vm = viewModel()
            vm.onAction(SettingsAction.OpenAppUsage)
            assertEquals(1, usageReader.settingsOpened)
        }

    @Test
    fun `where Hush has its own switch, the app usage row turns recording on and off`() =
        runTest {
            usageReader = FakeAppUsageReader(permitted = false, hasSwitch = true)
            vm = viewModel()
            vm.onAction(SettingsAction.Refresh)
            assertTrue(vm.uiState.value.appUsageSwitch)
            assertEquals(false, vm.uiState.value.appUsageOn)
            vm.onAction(SettingsAction.OpenAppUsage)
            assertEquals(true, vm.uiState.value.appUsageOn)
            vm.onAction(SettingsAction.OpenAppUsage)
            assertEquals(false, vm.uiState.value.appUsageOn)
            assertEquals(0, usageReader.settingsOpened)
        }

    @Test
    fun `the app usage row is hidden where apps can't be recorded`() =
        runTest {
            vm = viewModel(features = DeviceFeatures(appUsage = false))
            vm.onAction(SettingsAction.Refresh)
            assertFalse(vm.uiState.value.showAppUsage)
            assertNull(vm.uiState.value.appUsageOn)
        }

    @Test
    fun `the app usage row is hidden where recording needs no access`() =
        runTest {
            vm = viewModel(features = DeviceFeatures(appUsage = true, appUsageAccess = false))
            vm.onAction(SettingsAction.Refresh)
            assertFalse(vm.uiState.value.showAppUsage)
        }

    @Test
    fun `activities can be added, renamed and deleted`() =
        runTest {
            vm = viewModel()
            vm.onAction(SettingsAction.AddActivity)
            vm.onAction(SettingsAction.EditActivityName("  Reading on Kobo  "))
            vm.onAction(SettingsAction.SaveActivity)
            assertEquals(
                listOf("Reading on Kobo"),
                vm.uiState.value.activities
                    .map { it.name },
            )
            assertNull(vm.uiState.value.activityEditor)

            vm.onAction(SettingsAction.EditActivity(ActivityId(1)))
            vm.onAction(SettingsAction.EditActivityName("Reading"))
            vm.onAction(SettingsAction.SaveActivity)
            assertEquals(
                listOf("Reading"),
                vm.uiState.value.activities
                    .map { it.name },
            )

            vm.onAction(SettingsAction.EditActivity(ActivityId(1)))
            vm.onAction(SettingsAction.DeleteActivity)
            assertTrue(
                vm.uiState.value.activities
                    .isEmpty(),
            )
            assertTrue(
                activities.rows.value
                    .single()
                    .deleted,
            )
        }

    @Test
    fun `an activity name is capped and a blank one can't be saved`() =
        runTest {
            vm = viewModel()
            vm.onAction(SettingsAction.AddActivity)
            vm.onAction(SettingsAction.EditActivityName("x".repeat(40)))
            assertEquals(24, assertNotNull(vm.uiState.value.activityEditor).name.length)
            vm.onAction(SettingsAction.EditActivityName("   "))
            vm.onAction(SettingsAction.SaveActivity)
            assertTrue(activities.rows.value.isEmpty())
        }

    @Test
    fun `the activity editor lists the apps sorted into it and removes a rule at once`() =
        runTest {
            val study = activities.add("Study")
            val anki = ActivityRule("anki", "Anki", study)
            val notes = ActivityRule("notes", "Notes", study)
            val maps = ActivityRule("maps", "Maps", ActivityId(99))
            rules.rules.value = listOf(anki, notes, maps)
            val vm = viewModel()
            assertEquals(emptyList(), vm.uiState.value.editorRules)
            vm.onAction(SettingsAction.EditActivity(study))
            assertEquals(listOf(anki, notes), vm.uiState.value.editorRules)
            vm.onAction(SettingsAction.RemoveRule("anki"))
            assertEquals(listOf(notes), vm.uiState.value.editorRules)
            assertEquals(listOf(notes, maps), rules.rules.value)
            assertNotNull(vm.uiState.value.activityEditor)
        }
}
