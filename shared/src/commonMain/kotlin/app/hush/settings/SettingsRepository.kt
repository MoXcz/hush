// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import app.hush.activities.ActivityId
import app.hush.tags.TagId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** The user's settings. Single source of truth for everything in [Settings]. */
interface SettingsRepository {
    val settings: Flow<Settings>

    suspend fun setSelectedTag(id: TagId)

    /** Null clears the activity. */
    suspend fun setSelectedActivity(id: ActivityId?)

    suspend fun setDimClockShowWhenLocked(enabled: Boolean)

    suspend fun setDetoxGoal(goal: Duration)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<Settings> =
        dataStore.data.map { prefs ->
            Settings(
                selectedTagId = prefs[Keys.selectedTagId]?.let(::TagId),
                selectedActivityId = prefs[Keys.selectedActivityId]?.let(::ActivityId),
                dimClockShowWhenLocked = prefs[Keys.dimClockShowWhenLocked] ?: defaults.dimClockShowWhenLocked,
                detoxGoal = prefs[Keys.detoxGoalMinutes]?.minutes ?: defaults.detoxGoal,
            )
        }

    override suspend fun setSelectedTag(id: TagId) {
        dataStore.edit { prefs -> prefs[Keys.selectedTagId] = id.value }
    }

    override suspend fun setSelectedActivity(id: ActivityId?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.selectedActivityId) else prefs[Keys.selectedActivityId] = id.value
        }
    }

    override suspend fun setDimClockShowWhenLocked(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.dimClockShowWhenLocked] = enabled }
    }

    override suspend fun setDetoxGoal(goal: Duration) {
        dataStore.edit { prefs -> prefs[Keys.detoxGoalMinutes] = goal.inWholeMinutes.toInt() }
    }

    private object Keys {
        val selectedTagId = longPreferencesKey("selected_tag_id")
        val selectedActivityId = longPreferencesKey("selected_activity_id")
        val dimClockShowWhenLocked = booleanPreferencesKey("dim_clock_show_when_locked")
        val detoxGoalMinutes = intPreferencesKey("detox_goal_minutes")
    }

    private companion object {
        val defaults = Settings()
    }
}
