// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Room
import app.hush.blocklists.ProfileRole
import app.hush.blocklists.RoomBlockListRepository
import app.hush.blocklists.profileFor
import app.hush.core.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomBlockListRepositoryTest {
    private val database =
        buildAppDatabase(
            Room.databaseBuilder<AppDatabase>(Files.createTempDirectory("db").resolve("test.db").toString()),
            AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default),
        )
    private val repo = RoomBlockListRepository(database.blockListDao())

    @AfterTest
    fun close() = database.close()

    @Test
    fun `a new database has an empty Deep work and Detox list, one per role`() =
        runBlocking {
            val lists = repo.lists.first()
            assertEquals(listOf("Deep work", "Detox"), lists.map { it.name })
            assertEquals(ProfileRole.entries.map { it.profileId }, lists.map { it.id })
            assertEquals(listOf(emptySet<String>(), emptySet()), lists.map { it.packages })
            assertNull(repo.profileFor(ProfileRole.DeepWork))
        }

    @Test
    fun `apps are replaced per list, and a list with apps is a profile`() =
        runBlocking {
            val deep = ProfileRole.DeepWork.profileId
            repo.setApps(deep, setOf("com.example.video", "com.example.mail"))
            repo.setApps(deep, setOf("com.example.video"))
            val lists = repo.lists.first()
            assertEquals(setOf("com.example.video"), lists[0].packages)
            assertEquals(emptySet(), lists[1].packages)
            assertEquals(deep, repo.profileFor(ProfileRole.DeepWork))
            assertNull(repo.profileFor(ProfileRole.Detox))
        }
}
