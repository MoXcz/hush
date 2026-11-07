// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.hush.tags.TagId
import app.hush.timer.Phase
import app.hush.timer.RunState
import app.hush.timer.TimerSnapshot
import app.hush.timer.TimerStateStore
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** Keeps the timer snapshot next to the settings, in the same DataStore file, as epoch millis. */
class DataStoreTimerStateStore(
    private val dataStore: DataStore<Preferences>,
) : TimerStateStore {
    override suspend fun load(): TimerSnapshot? {
        val prefs = dataStore.data.first()
        val phase = Phase.entries.firstOrNull { it.name == prefs[Keys.phase] } ?: return null
        val duration = prefs[Keys.durationMillis]?.milliseconds ?: return null
        val endsAt = prefs[Keys.endsAtMillis]
        val pausedRemaining = prefs[Keys.pausedRemainingMillis]
        return TimerSnapshot(
            phase = phase,
            completedWorkPhases = prefs[Keys.completedWorkPhases] ?: 0,
            duration = duration,
            run =
                when {
                    endsAt != null -> RunState.Running(Instant.fromEpochMilliseconds(endsAt))
                    pausedRemaining != null -> RunState.Paused(pausedRemaining.milliseconds)
                    else -> RunState.Ready
                },
            blocking = prefs[Keys.blocking] ?: false,
            canStopEarly = prefs[Keys.canStopEarly] ?: true,
            startedAt = prefs[Keys.startedAtMillis]?.let(Instant::fromEpochMilliseconds),
            tagId = prefs[Keys.tagId]?.let(::TagId),
        )
    }

    override suspend fun save(snapshot: TimerSnapshot) {
        dataStore.edit { prefs ->
            prefs[Keys.phase] = snapshot.phase.name
            prefs[Keys.completedWorkPhases] = snapshot.completedWorkPhases
            prefs[Keys.durationMillis] = snapshot.duration.inWholeMilliseconds
            prefs.remove(Keys.endsAtMillis)
            prefs.remove(Keys.pausedRemainingMillis)
            when (val run = snapshot.run) {
                RunState.Ready -> Unit
                is RunState.Running -> prefs[Keys.endsAtMillis] = run.endsAt.toEpochMilliseconds()
                is RunState.Paused -> prefs[Keys.pausedRemainingMillis] = run.remaining.inWholeMilliseconds
            }
            prefs[Keys.blocking] = snapshot.blocking
            prefs[Keys.canStopEarly] = snapshot.canStopEarly
            val startedAt = snapshot.startedAt
            if (startedAt == null) prefs.remove(Keys.startedAtMillis) else prefs[Keys.startedAtMillis] = startedAt.toEpochMilliseconds()
            val tagId = snapshot.tagId
            if (tagId == null) prefs.remove(Keys.tagId) else prefs[Keys.tagId] = tagId.value
        }
    }

    private object Keys {
        val phase = stringPreferencesKey("timer_phase")
        val completedWorkPhases = intPreferencesKey("timer_completed_work_phases")
        val durationMillis = longPreferencesKey("timer_duration_ms")
        val endsAtMillis = longPreferencesKey("timer_ends_at_ms")
        val pausedRemainingMillis = longPreferencesKey("timer_paused_remaining_ms")
        val blocking = booleanPreferencesKey("timer_blocking")
        val canStopEarly = booleanPreferencesKey("timer_can_stop_early")
        val startedAtMillis = longPreferencesKey("timer_started_at_ms")
        val tagId = longPreferencesKey("timer_tag_id")
    }
}
