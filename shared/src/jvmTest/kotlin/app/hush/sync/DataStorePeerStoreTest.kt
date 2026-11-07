// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

import app.hush.data.prefs.createSettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class DataStorePeerStoreTest {
    private val phone = PairedPeer("phone-id", "Pixel", "pin-phone")
    private val at = Instant.parse("2026-11-06T10:00:00Z")

    private fun dataStore() = createSettingsDataStore { Files.createTempDirectory("peers").resolve("settings.preferences_pb").toString() }

    @Test
    fun `stores one peer with its address and sync record`() =
        runTest {
            val store = DataStorePeerStore(dataStore())
            assertEquals(PeerState(), store.state.first())
            store.save(phone)
            store.rememberAddress("192.168.1.20", 47447)
            store.recordResult(SyncOutcome.Synced, at)
            store.recordResult(SyncOutcome.NotFound, syncedAt = null)
            assertEquals(
                PeerState(phone.copy(host = "192.168.1.20", port = 47447), at, SyncOutcome.NotFound),
                store.state.first(),
            )
        }

    @Test
    fun `pairing again replaces the peer and its record, and unpair keeps other settings`() =
        runTest {
            val dataStore = dataStore()
            val identity = DataStoreDeviceIdentity(dataStore)
            val id = identity.deviceId()
            val store = DataStorePeerStore(dataStore)
            store.save(phone)
            store.recordResult(SyncOutcome.Synced, at)
            val other = PairedPeer("other-id", "Laptop", "pin-other", "10.0.0.2", 50000)
            store.save(other)
            assertEquals(PeerState(other), store.state.first())
            store.clear()
            assertNull(store.peer())
            assertEquals(PeerState(), store.state.first())
            assertEquals(id, identity.deviceId())
        }

    @Test
    fun `nothing is recorded while unpaired`() =
        runTest {
            val store = DataStorePeerStore(dataStore())
            store.rememberAddress("10.0.0.2", 1)
            store.recordResult(SyncOutcome.Synced, at)
            assertEquals(PeerState(), store.state.first())
        }
}
