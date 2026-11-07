// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Room
import app.hush.activities.RoomActivityRepository
import app.hush.core.AppDispatchers
import app.hush.rules.ActivityRule
import app.hush.rules.RoomActivityRuleRepository
import app.hush.sync.FakeDeviceIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class RoomActivityRuleRepositoryTest {
    private val database =
        buildAppDatabase(
            Room.databaseBuilder<AppDatabase>(Files.createTempDirectory("db").resolve("test.db").toString()),
            AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default),
        )
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(5_000)
        }
    private val identity = FakeDeviceIdentity("phone")
    private val repo = RoomActivityRuleRepository(database.activityRuleDao(), clock, identity)
    private val activities = RoomActivityRepository(database.activityDao(), clock, identity)

    @AfterTest
    fun close() = database.close()

    private suspend fun stored(): List<ActivityRuleEntity> = database.syncDao().rules()

    private suspend fun uidOf(name: String): String =
        database
            .syncDao()
            .activities()
            .single { it.name == name }
            .uid

    @Test
    fun `a rule is stored by activity uid, stamped, and replaced by the next one for the same app`() =
        runBlocking {
            val study = activities.add("Study")
            val leisure = activities.add("Leisure")
            repo.set("org.anki", "Anki", study)
            repo.set("org.anki", "Anki", leisure)
            assertEquals(listOf(ActivityRule("org.anki", "Anki", leisure)), repo.rules.first())
            assertEquals(listOf(ActivityRuleEntity("org.anki", "Anki", uidOf("Leisure"), false, 5_000, "phone")), stored())
        }

    @Test
    fun `clearing a rule leaves a tombstone and hides it`() =
        runBlocking {
            val study = activities.add("Study")
            repo.set("web:youtube", "YouTube", study)
            repo.clear("web:youtube")
            assertEquals(emptyList(), repo.rules.first())
            assertEquals(true, stored().single().deleted)
        }

    @Test
    fun `a rule for a deleted activity still reads with it, and one for an unknown uid without`() =
        runBlocking {
            val study = activities.add("Study")
            repo.set("maps", "Maps", study)
            activities.delete(study)
            database.syncDao().upsertRules(listOf(ActivityRuleEntity("notes", "Notes", "missing", false, 1, "desktop")))
            assertEquals(
                listOf(ActivityRule("maps", "Maps", study), ActivityRule("notes", "Notes", null)),
                repo.rules.first(),
            )
        }
}
