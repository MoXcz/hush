// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

const val SETTINGS_FILE_NAME = "settings.preferences_pb"

/** Create exactly one per file and process (a Koin single). [path] comes from platform code. */
fun createSettingsDataStore(path: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path().toPath() })
