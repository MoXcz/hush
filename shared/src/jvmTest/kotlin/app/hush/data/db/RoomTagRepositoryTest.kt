// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Room
import app.hush.core.AppDispatchers
import app.hush.sync.FakeDeviceIdentity
import app.hush.sync.UUID_PATTERN
import app.hush.tags.RoomTagRepository
import app.hush.tags.TagColor
import app.hush.tags.TagId
import app.hush.timer.TimerDurations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RoomTagRepositoryTest {
    private val database =
        buildAppDatabase(
            Room.databaseBuilder<AppDatabase>(Files.createTempDirectory("db").resolve("test.db").toString()),
            AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default),
        )
    private val clock = FixedClock(Instant.fromEpochMilliseconds(5_000))
    private val identity = FakeDeviceIdentity("phone")
    private val repo = RoomTagRepository(database.tagDao(), clock, identity)

    private suspend fun rows(): List<TagEntity> = database.tagDao().all().first()

    @AfterTest
    fun close() = database.close()

    @Test
    fun `a new database is seeded with Focus, Light and Deep`() =
        runBlocking {
            val tags = repo.tags.first()
            assertEquals(listOf("Focus", "Light", "Deep"), tags.map { it.name })
            assertEquals(TagId(FOCUS_TAG_ID), tags.first().id)
            assertEquals(TimerDurations(), tags[0].durations)
            assertEquals(TimerDurations(20.minutes, 10.minutes, 20.minutes, 4), tags[1].durations)
            assertEquals(TimerDurations(50.minutes, 10.minutes, 30.minutes, 4), tags[2].durations)
        }

    @Test
    fun `tags can be added and edited`() =
        runBlocking {
            val id = repo.add("Reading", TagColor.Sand, TimerDurations(work = 40.minutes))
            val added = repo.tags.first().last()
            assertEquals(id, added.id)
            repo.update(added.copy(name = "Books", color = TagColor.Rose))
            assertEquals(
                "Books",
                repo.tags
                    .first()
                    .last()
                    .name,
            )
            assertEquals(
                TagColor.Rose,
                repo.tags
                    .first()
                    .last()
                    .color,
            )
        }

    @Test
    fun `deleting hides a tag but keeps it for history, and never deletes the last one`() =
        runBlocking {
            assertTrue(repo.delete(TagId(2)))
            assertTrue(repo.delete(TagId(3)))
            assertFalse(repo.delete(TagId(1)))
            assertEquals(listOf("Focus"), repo.tags.first().map { it.name })
            assertEquals(listOf(false, true, true), repo.allTags.first().map { it.deleted })
        }

    @Test
    fun `a new database gives the seeded tags their fixed uids, never edited`() =
        runBlocking {
            assertEquals(SEED_TAG_UIDS, rows().map { it.uid })
            assertEquals(listOf(0L, 0L, 0L), rows().map { it.updatedAtMillis })
            assertEquals(listOf("", "", ""), rows().map { it.updatedBy })
        }

    @Test
    fun `adding, editing and deleting a tag stamp the clock and this device`() =
        runBlocking {
            val id = repo.add("Reading", TagColor.Sand, TimerDurations(work = 40.minutes))
            val added = rows().single { it.id == id.value }
            assertTrue(UUID_PATTERN.matches(added.uid))
            assertEquals(5_000L, added.updatedAtMillis)
            assertEquals("phone", added.updatedBy)

            clock.now = Instant.fromEpochMilliseconds(7_000)
            identity.id = "desktop"
            repo.update(
                repo.tags
                    .first()
                    .first()
                    .copy(name = "Work"),
            )
            assertEquals(listOf(7_000L, "desktop"), rows().first().let { listOf(it.updatedAtMillis, it.updatedBy) })

            clock.now = Instant.fromEpochMilliseconds(9_000)
            assertTrue(repo.delete(TagId(2)))
            assertEquals(listOf(9_000L, "desktop"), rows()[1].let { listOf(it.updatedAtMillis, it.updatedBy) })
        }

    @Test
    fun `an edit keeps the tag's uid`() =
        runBlocking {
            val id = repo.add("Reading", TagColor.Sand, TimerDurations())
            val uid = rows().single { it.id == id.value }.uid
            repo.update(
                repo.tags
                    .first()
                    .last()
                    .copy(name = "Books"),
            )
            assertEquals(uid, rows().single { it.id == id.value }.uid)
            repo.update(
                repo.tags
                    .first()
                    .first()
                    .copy(color = TagColor.Rose),
            )
            assertEquals(SEED_TAG_UIDS[0], rows().first().uid)
        }
}

private class FixedClock(
    var now: Instant,
) : Clock {
    override fun now(): Instant = now
}
