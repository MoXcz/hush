// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocker.builtin

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.hush.blocker.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** A running block: which list, and until when. The list's apps are read live, so edits apply at once. */
data class BlockSession(
    val profile: ProfileId,
    val profileName: String,
    val endsAt: Instant,
)

/** Where the running block is kept, so it survives process death and reboots. */
interface BlockSessionStore {
    /** The stored block (possibly already over), or null. */
    val session: Flow<BlockSession?>

    suspend fun save(session: BlockSession)

    suspend fun clear()
}

suspend fun BlockSessionStore.current(): BlockSession? = session.first()

/** `block_*` keys in the app's settings DataStore file (one file, one process). */
internal class DataStoreBlockSessionStore(
    private val dataStore: DataStore<Preferences>,
) : BlockSessionStore {
    override val session: Flow<BlockSession?> =
        dataStore.data.map { prefs ->
            val profile = prefs[Keys.profile] ?: return@map null
            val endsAt = prefs[Keys.endsAtMillis] ?: return@map null
            BlockSession(ProfileId(profile), prefs[Keys.profileName].orEmpty(), Instant.fromEpochMilliseconds(endsAt))
        }

    override suspend fun save(session: BlockSession) {
        dataStore.edit { prefs ->
            prefs[Keys.profile] = session.profile.value
            prefs[Keys.profileName] = session.profileName
            prefs[Keys.endsAtMillis] = session.endsAt.toEpochMilliseconds()
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.profile)
            prefs.remove(Keys.profileName)
            prefs.remove(Keys.endsAtMillis)
        }
    }

    private object Keys {
        val profile = stringPreferencesKey("block_profile")
        val profileName = stringPreferencesKey("block_profile_name")
        val endsAtMillis = longPreferencesKey("block_ends_at_millis")
    }
}
