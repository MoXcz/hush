// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.uuid.Uuid

/** This install's identity in a sync. */
interface DeviceIdentity {
    /** A random id created once per install and never changed. Stamped on every tag edit. */
    suspend fun deviceId(): String
}

/** Keeps the device id in the settings DataStore file (`device_id`). */
class DataStoreDeviceIdentity(
    private val dataStore: DataStore<Preferences>,
) : DeviceIdentity {
    override suspend fun deviceId(): String {
        var id = ""
        // Read and created in one edit, so two first callers can't end up with different ids.
        dataStore.edit { prefs ->
            id = prefs[deviceIdKey] ?: Uuid.random().toString().also { prefs[deviceIdKey] = it }
        }
        return id
    }

    private companion object {
        val deviceIdKey = stringPreferencesKey("device_id")
    }
}
