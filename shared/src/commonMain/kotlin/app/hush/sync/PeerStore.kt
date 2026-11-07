// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** How the last sync attempt ended. */
enum class SyncOutcome {
    Synced,
    NotFound,
    Rejected,
    TooNew,
    Failed,
}

data class PeerState(
    val peer: PairedPeer? = null,
    /** The last sync that went through; kept when later attempts fail. */
    val lastSyncedAt: Instant? = null,
    val lastResult: SyncOutcome? = null,
)

/** The paired device (one per install) and how syncing with it went. */
interface PeerStore {
    val state: Flow<PeerState>

    /** The paired device, or null. The host answers "who may sync" with this. */
    suspend fun peer(): PairedPeer?

    /** Stores [peer], replacing any previous one and its sync record. */
    suspend fun save(peer: PairedPeer)

    /** Client: where the host was last reached, tried first next time. No-op when unpaired. */
    suspend fun rememberAddress(
        host: String,
        port: Int,
    )

    /** Records [result]; [syncedAt] moves the last successful sync (null keeps it). */
    suspend fun recordResult(
        result: SyncOutcome,
        syncedAt: Instant?,
    )

    /** Forgets the peer and its sync record. */
    suspend fun clear()
}

/** Keeps the peer in the settings DataStore file (`sync_*` keys). */
class DataStorePeerStore(
    private val dataStore: DataStore<Preferences>,
) : PeerStore {
    override val state: Flow<PeerState> =
        dataStore.data.map { prefs ->
            val id = prefs[Keys.id]
            val pin = prefs[Keys.pin]
            val peer =
                if (id != null && pin != null) {
                    PairedPeer(id, prefs[Keys.name].orEmpty(), pin, prefs[Keys.host], prefs[Keys.port])
                } else {
                    null
                }
            PeerState(
                peer = peer,
                lastSyncedAt = prefs[Keys.lastAt]?.let(Instant::fromEpochMilliseconds),
                lastResult = prefs[Keys.lastResult]?.let { name -> SyncOutcome.entries.firstOrNull { it.name == name } },
            )
        }

    override suspend fun peer(): PairedPeer? = state.first().peer

    override suspend fun save(peer: PairedPeer) {
        dataStore.edit { prefs ->
            prefs.removeSyncKeys()
            prefs[Keys.id] = peer.deviceId
            prefs[Keys.name] = peer.name
            prefs[Keys.pin] = peer.pin
            peer.host?.let { prefs[Keys.host] = it }
            peer.port?.let { prefs[Keys.port] = it }
        }
    }

    override suspend fun rememberAddress(
        host: String,
        port: Int,
    ) {
        dataStore.edit { prefs ->
            if (prefs[Keys.id] == null) return@edit
            prefs[Keys.host] = host
            prefs[Keys.port] = port
        }
    }

    override suspend fun recordResult(
        result: SyncOutcome,
        syncedAt: Instant?,
    ) {
        dataStore.edit { prefs ->
            if (prefs[Keys.id] == null) return@edit
            prefs[Keys.lastResult] = result.name
            syncedAt?.let { prefs[Keys.lastAt] = it.toEpochMilliseconds() }
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.removeSyncKeys() }
    }

    // Only the sync keys: the settings file holds everything else too.
    private fun MutablePreferences.removeSyncKeys() {
        remove(Keys.id)
        remove(Keys.name)
        remove(Keys.pin)
        remove(Keys.host)
        remove(Keys.port)
        remove(Keys.lastAt)
        remove(Keys.lastResult)
    }

    private object Keys {
        val id = stringPreferencesKey("sync_peer_id")
        val name = stringPreferencesKey("sync_peer_name")
        val pin = stringPreferencesKey("sync_peer_pin")
        val host = stringPreferencesKey("sync_peer_host")
        val port = intPreferencesKey("sync_peer_port")
        val lastAt = longPreferencesKey("sync_last_at")
        val lastResult = stringPreferencesKey("sync_last_result")
    }
}
