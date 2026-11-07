// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.settings

import app.hush.activities.ActivityId
import app.hush.tags.TagId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration

class FakeSettingsRepository(
    initial: Settings = Settings(),
) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun setSelectedTag(id: TagId) {
        settings.update { it.copy(selectedTagId = id) }
    }

    override suspend fun setSelectedActivity(id: ActivityId?) {
        settings.update { it.copy(selectedActivityId = id) }
    }

    override suspend fun setDimClockShowWhenLocked(enabled: Boolean) {
        settings.update { it.copy(dimClockShowWhenLocked = enabled) }
    }

    override suspend fun setDetoxGoal(goal: Duration) {
        settings.update { it.copy(detoxGoal = goal) }
    }
}
