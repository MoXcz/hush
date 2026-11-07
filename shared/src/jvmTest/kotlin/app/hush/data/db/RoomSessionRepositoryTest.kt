// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Room
import app.hush.blocker.ProfileId
import app.hush.core.AppDispatchers
import app.hush.history.RoomSessionRepository
import app.hush.history.SessionOutcome
import app.hush.history.SessionType
import app.hush.sync.UUID_PATTERN
import app.hush.tags.TagId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RoomSessionRepositoryTest {
    private val start = Instant.parse("2026-11-05T09:00:00Z")
    private val detox = ProfileId("detox")
    private val database =
        buildAppDatabase(
            Room.databaseBuilder<AppDatabase>(Files.createTempDirectory("db").resolve("test.db").toString()),
            AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default),
        )
    private val repo = RoomSessionRepository(database.sessionDao())

    @AfterTest
    fun close() = database.close()

    @Test
    fun `an open detox attempt is found again and finished once`() =
        runBlocking {
            val attempt = repo.startDetoxAttempt(detox, 90, start)
            assertEquals(attempt, repo.openDetoxAttempt())
            assertEquals(emptyList(), repo.history.first())

            repo.finish(attempt.id, start + 90.minutes, SessionOutcome.Completed)
            repo.finish(attempt.id, start + 2.hours, SessionOutcome.Stopped)
            assertNull(repo.openDetoxAttempt())
            val finished = repo.history.first().single()
            assertEquals(start + 90.minutes, finished.endedAt)
            assertEquals(SessionOutcome.Completed, finished.outcome)
        }

    @Test
    fun `history lists work and detox newest first`() =
        runBlocking {
            repo.recordWork(null, TagId(1), 25, start, start + 25.minutes, SessionOutcome.Completed)
            val attempt = repo.startDetoxAttempt(detox, 60, start + 1.hours)
            repo.finish(attempt.id, start + 1.hours + 5.minutes, SessionOutcome.Abandoned)
            assertEquals(listOf(SessionType.Detox, SessionType.Work), repo.history.first().map { it.type })
            assertEquals(listOf(detox, null), repo.history.first().map { it.profile })
            assertEquals(listOf(null, TagId(1)), repo.history.first().map { it.tagId })
        }

    @Test
    fun `path progress is the longest passed detox level`() =
        runBlocking {
            assertNull(repo.longestPassedLevelMinutes.first())
            repo.recordWork(null, null, 120, start, start + 2.hours, SessionOutcome.Completed)
            listOf(60 to SessionOutcome.Completed, 90 to SessionOutcome.Completed, 120 to SessionOutcome.Stopped)
                .forEach { (minutes, outcome) ->
                    val attempt = repo.startDetoxAttempt(detox, minutes, start)
                    repo.finish(attempt.id, start + minutes.minutes, outcome)
                }
            assertEquals(90, assertNotNull(repo.longestPassedLevelMinutes.first()))
        }

    @Test
    fun `every recorded session gets its own uid`() =
        runBlocking {
            repo.recordWork(null, TagId(1), 25, start, start + 25.minutes, SessionOutcome.Completed)
            repo.recordWork(null, TagId(1), 25, start, start + 25.minutes, SessionOutcome.Completed)
            val attempt = repo.startDetoxAttempt(detox, 60, start)
            repo.finish(attempt.id, start + 1.hours, SessionOutcome.Completed)
            val uids =
                database
                    .sessionDao()
                    .finished()
                    .first()
                    .map { it.uid }
            assertEquals(3, uids.toSet().size)
            assertTrue(uids.all(UUID_PATTERN::matches))
        }
}
