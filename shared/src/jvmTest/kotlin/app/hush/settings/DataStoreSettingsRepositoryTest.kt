// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.settings

import app.hush.activities.ActivityId
import app.hush.data.prefs.createSettingsDataStore
import app.hush.tags.TagId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class DataStoreSettingsRepositoryTest {
    private val file = Files.createTempDirectory("settings").resolve("settings.preferences_pb")

    @Test
    fun `defaults come from Settings`() =
        runTest {
            val repo = DataStoreSettingsRepository(createSettingsDataStore { file.toString() })
            assertEquals(Settings(), repo.settings.first())
        }

    @Test
    fun `stores the selected tag and the dim clock lock screen setting`() =
        runTest {
            val repo = DataStoreSettingsRepository(createSettingsDataStore { file.toString() })
            assertEquals(null, repo.settings.first().selectedTagId)
            repo.setSelectedTag(TagId(3))
            repo.setDimClockShowWhenLocked(true)
            val stored = repo.settings.first()
            assertEquals(TagId(3), stored.selectedTagId)
            assertEquals(true, stored.dimClockShowWhenLocked)
        }

    @Test
    fun `stores the detox goal`() =
        runTest {
            val repo = DataStoreSettingsRepository(createSettingsDataStore { file.toString() })
            assertEquals(240.minutes, repo.settings.first().detoxGoal)
            repo.setDetoxGoal(150.minutes)
            assertEquals(150.minutes, repo.settings.first().detoxGoal)
        }

    @Test
    fun `stores the selected activity and clears it`() =
        runTest {
            val repo = DataStoreSettingsRepository(createSettingsDataStore { file.toString() })
            repo.setSelectedActivity(ActivityId(2))
            assertEquals(ActivityId(2), repo.settings.first().selectedActivityId)
            repo.setSelectedActivity(null)
            assertEquals(null, repo.settings.first().selectedActivityId)
        }
}
