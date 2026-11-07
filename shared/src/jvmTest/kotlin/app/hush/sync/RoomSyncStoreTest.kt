// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

import androidx.room3.Room
import app.hush.activities.RoomActivityRepository
import app.hush.blocker.ProfileId
import app.hush.core.AppDispatchers
import app.hush.data.db.AppDatabase
import app.hush.data.db.FOCUS_TAG_ID
import app.hush.data.db.SEED_TAG_UIDS
import app.hush.data.db.buildAppDatabase
import app.hush.history.RoomSessionRepository
import app.hush.history.SessionOutcome
import app.hush.rules.ActivityRule
import app.hush.rules.RoomActivityRuleRepository
import app.hush.tags.RoomTagRepository
import app.hush.tags.TagColor
import app.hush.tags.TagId
import app.hush.timer.TimerDurations
import app.hush.usage.AppTime
import app.hush.usage.RoomAppUsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RoomSyncStoreTest {
    private val start = Instant.parse("2026-11-05T09:00:00Z")
    private val clock =
        object : Clock {
            override fun now(): Instant = start
        }
    private val phone = TestDevice("phone")
    private val desktop = TestDevice("desktop")

    @AfterTest
    fun close() {
        phone.database.close()
        desktop.database.close()
    }

    private inner class TestDevice(
        val id: String,
    ) {
        val database =
            buildAppDatabase(
                Room.databaseBuilder<AppDatabase>(Files.createTempDirectory("db").resolve("test.db").toString()),
                AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default),
            )
        val identity = FakeDeviceIdentity(id)
        val store = RoomSyncStore(database.syncDao())
        val tags = RoomTagRepository(database.tagDao(), clock, identity)
        val sessions = RoomSessionRepository(database.sessionDao())
        val activities = RoomActivityRepository(database.activityDao(), clock, identity)
        val apps = RoomAppUsageRepository(database.sessionAppDao())
        val rules = RoomActivityRuleRepository(database.activityRuleDao(), clock, identity)

        suspend fun syncFrom(other: TestDevice) = store.apply(other.store.export(other.id), id, start)
    }

    @Test
    fun `two databases synced both ways export the same data`() =
        runBlocking {
            phone.tags.add("Reading", TagColor.Sand, TimerDurations(work = 40.minutes))
            phone.sessions.recordWork(null, TagId(4), 40, start, start + 40.minutes, SessionOutcome.Completed)
            desktop.tags.update(
                desktop.tags.tags
                    .first()[1]
                    .copy(name = "Easy"),
            )
            desktop.tags.add("Writing", TagColor.Rose, TimerDurations())
            desktop.sessions.recordWork(null, TagId(FOCUS_TAG_ID), 25, start, start + 25.minutes, SessionOutcome.Stopped)

            phone.syncFrom(desktop)
            desktop.syncFrom(phone)

            val phoneExport = phone.store.export(phone.id)
            val desktopExport = desktop.store.export(desktop.id)
            assertEquals(phoneExport.tags, desktopExport.tags)
            assertEquals(phoneExport.sessions, desktopExport.sessions)
            assertEquals(
                listOf("Focus", "Easy", "Deep", "Reading", "Writing"),
                phone.tags.tags
                    .first()
                    .map { it.name },
            )
            assertEquals(2, phoneExport.sessions.size)
            assertEquals(MergeSummary(0, 0, 0), phone.syncFrom(desktop))
        }

    @Test
    fun `the seeded Focus tag is the same row on both devices`() =
        runBlocking {
            desktop.sessions.recordWork(null, TagId(FOCUS_TAG_ID), 25, start, start + 25.minutes, SessionOutcome.Completed)
            phone.syncFrom(desktop)
            assertEquals(
                3,
                phone.tags.allTags
                    .first()
                    .size,
            )
            assertEquals(
                TagId(FOCUS_TAG_ID),
                phone.sessions.history
                    .first()
                    .single()
                    .tagId,
            )
            assertEquals(
                SEED_TAG_UIDS[0],
                phone.store
                    .export(phone.id)
                    .sessions
                    .single()
                    .tag,
            )
        }

    @Test
    fun `an open detox attempt is not exported`() =
        runBlocking {
            phone.sessions.startDetoxAttempt(ProfileId("detox"), 60, start)
            assertTrue(
                phone.store
                    .export(phone.id)
                    .sessions
                    .isEmpty(),
            )
            desktop.syncFrom(phone)
            assertNull(desktop.sessions.openDetoxAttempt())
        }

    @Test
    fun `detox levels passed on the phone raise the desktop's path progress`() =
        runBlocking {
            listOf(60, 90).forEach { minutes ->
                val attempt = phone.sessions.startDetoxAttempt(ProfileId("detox"), minutes, start)
                phone.sessions.finish(attempt.id, start + minutes.minutes, SessionOutcome.Completed)
            }
            assertNull(desktop.sessions.longestPassedLevelMinutes.first())
            desktop.syncFrom(phone)
            assertEquals(90, desktop.sessions.longestPassedLevelMinutes.first())
            assertEquals(
                start + 1.hours + 30.minutes,
                desktop.sessions.history
                    .first()
                    .first { it.plannedMinutes == 90 }
                    .endedAt,
            )
        }

    @Test
    fun `activities and the apps of work phases travel with their sessions`() =
        runBlocking {
            val kobo = phone.activities.add("Reading on Kobo")
            val uid = phone.sessions.recordWork(null, TagId(FOCUS_TAG_ID), 25, start, start + 25.minutes, SessionOutcome.Completed, kobo)
            phone.apps.record(uid, listOf(AppTime("org.notes", "Notes", 3.minutes), AppTime("org.dict", "Dictionary", 1.minutes)))

            assertEquals(1, desktop.syncFrom(phone).activitiesChanged)
            val session =
                desktop.sessions.history
                    .first()
                    .single()
            assertEquals(
                "Reading on Kobo",
                desktop.activities.activities
                    .first()
                    .single { it.id == session.activityId }
                    .name,
            )
            assertEquals(
                setOf("org.notes" to 3.minutes, "org.dict" to 1.minutes),
                desktop.apps.usage
                    .first()
                    .map { it.packageName to it.foreground }
                    .toSet(),
            )
            assertEquals(
                start,
                desktop.apps.usage
                    .first()
                    .first()
                    .sessionStartedAt,
            )

            desktop.syncFrom(phone)
            assertEquals(
                2,
                desktop.apps.usage
                    .first()
                    .size,
            )
            val phoneExport = phone.store.export(phone.id)
            val desktopExport = desktop.store.export(desktop.id)
            assertEquals(phoneExport.activities, desktopExport.activities)
            assertEquals(phoneExport.apps, desktopExport.apps)
            assertEquals(phoneExport.sessions, desktopExport.sessions)
        }

    @Test
    fun `an activity deleted on one device is deleted on the other, and its sessions keep its name`() =
        runBlocking {
            val kobo = phone.activities.add("Reading on Kobo")
            phone.sessions.recordWork(null, null, 25, start, start + 25.minutes, SessionOutcome.Completed, kobo)
            desktop.syncFrom(phone)
            val later =
                object : Clock {
                    override fun now(): Instant = start + 1.hours
                }
            RoomActivityRepository(phone.database.activityDao(), later, phone.identity).delete(kobo)
            desktop.syncFrom(phone)
            assertTrue(
                desktop.activities.activities
                    .first()
                    .isEmpty(),
            )
            assertEquals(
                "Reading on Kobo",
                desktop.activities.allActivities
                    .first()
                    .single()
                    .name,
            )
        }

    @Test
    fun `recording the same apps twice keeps one row each`() =
        runBlocking {
            val uid = phone.sessions.recordWork(null, null, 25, start, start + 25.minutes, SessionOutcome.Completed)
            phone.apps.record(uid, listOf(AppTime("org.notes", "Notes", 3.minutes)))
            phone.apps.record(uid, listOf(AppTime("org.notes", "Notes", 9.minutes)))
            assertEquals(
                listOf(3.minutes),
                phone.apps.usage
                    .first()
                    .map { it.foreground },
            )
        }

    @Test
    fun `rules sync by activity uid, and a cleared rule is cleared on the other device`() =
        runBlocking {
            desktop.activities.add("Sketching")
            val study = phone.activities.add("Study")
            phone.rules.set("org.anki", "Anki", study)
            phone.rules.set("web:youtube", "YouTube", study)
            desktop.syncFrom(phone)

            val desktopStudy =
                desktop.activities.activities
                    .first()
                    .single { it.name == "Study" }
                    .id
            assertEquals(
                setOf(ActivityRule("org.anki", "Anki", desktopStudy), ActivityRule("web:youtube", "YouTube", desktopStudy)),
                desktop.rules.rules
                    .first()
                    .toSet(),
            )

            val later =
                object : Clock {
                    override fun now(): Instant = start + 1.hours
                }
            RoomActivityRuleRepository(desktop.database.activityRuleDao(), later, desktop.identity).clear("web:youtube")
            phone.syncFrom(desktop)
            assertEquals(
                listOf("org.anki"),
                phone.rules.rules
                    .first()
                    .map { it.appKey },
            )
            assertEquals(phone.store.export(phone.id).activityRules, desktop.store.export(desktop.id).activityRules)
        }
}
