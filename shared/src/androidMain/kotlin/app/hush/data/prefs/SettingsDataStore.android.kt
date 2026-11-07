// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

fun createSettingsDataStore(context: Context): DataStore<Preferences> =
    createSettingsDataStore { context.filesDir.resolve(SETTINGS_FILE_NAME).absolutePath }
