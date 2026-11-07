// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Window tracking is off until the user turns it on, like usage access on Android.
class UsageSwitch(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val key = booleanPreferencesKey("desktop_app_usage")

    val enabled: StateFlow<Boolean> =
        dataStore.data.map { it[key] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    suspend fun set(on: Boolean) {
        dataStore.edit { it[key] = on }
        enabled.first { it == on }
    }
}
