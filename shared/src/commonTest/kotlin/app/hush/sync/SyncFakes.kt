// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

class FakeSyncStore(
    var exported: SyncPayload = SyncPayload(device = "device-a", tags = emptyList(), sessions = emptyList()),
) : SyncStore {
    val calls = mutableListOf<String>()
    val applied = mutableListOf<Triple<SyncPayload, String, Instant>>()

    /** When set, [export] and [apply] throw it, like a failing database. */
    var failure: Exception? = null

    override suspend fun export(self: String): SyncPayload {
        calls += "export"
        failure?.let { throw it }
        return exported
    }

    override suspend fun apply(
        remote: SyncPayload,
        self: String,
        now: Instant,
    ): MergeSummary {
        calls += "apply"
        failure?.let { throw it }
        applied += Triple(remote, self, now)
        return MergeSummary(tagsChanged = 0, sessionsAdded = 0, dropped = 0)
    }
}

class FakePeerStore(
    peer: PairedPeer? = null,
) : PeerStore {
    val current = MutableStateFlow(PeerState(peer = peer))

    override val state = current

    override suspend fun peer(): PairedPeer? = current.value.peer

    override suspend fun save(peer: PairedPeer) {
        current.value = PeerState(peer = peer)
    }

    override suspend fun rememberAddress(
        host: String,
        port: Int,
    ) {
        current.update { it.copy(peer = it.peer?.copy(host = host, port = port)) }
    }

    override suspend fun recordResult(
        result: SyncOutcome,
        syncedAt: Instant?,
    ) {
        current.update { if (it.peer == null) it else it.copy(lastResult = result, lastSyncedAt = syncedAt ?: it.lastSyncedAt) }
    }

    override suspend fun clear() {
        current.value = PeerState()
    }
}

class FakeSyncTransport : SyncTransport {
    val sent = mutableListOf<ByteArray>()
    var next: ExchangeResult = ExchangeResult.NotFound

    /** When set, exchanges wait for it, so a test can look at a sync in progress. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun exchange(
        peer: PairedPeer,
        outgoing: ByteArray,
    ): ExchangeResult {
        sent += outgoing
        gate?.await()
        return next
    }
}

class FakePairing : Pairing {
    val calls = mutableListOf<String>()
    val current = MutableStateFlow<PairingState>(PairingState.Idle)

    override val state: StateFlow<PairingState> = current

    val advertising = MutableStateFlow(true)

    override val discoverable: StateFlow<Boolean> = advertising

    override fun open() {
        calls += "open"
    }

    override suspend fun enterCode(code: String) {
        calls += "enterCode:$code"
    }

    override fun close() {
        calls += "close"
    }
}

class FakeSyncScheduler : SyncScheduler {
    val calls = mutableListOf<Boolean>()

    override fun onPairedChanged(paired: Boolean) {
        calls += paired
    }
}

val PHONE = PairedPeer(deviceId = "phone-id", name = "Pixel", pin = "pin-phone")
val COMPUTER = PairedPeer(deviceId = "computer-id", name = "arch", pin = "pin-computer", host = "192.168.1.20", port = 47447)

fun payloadFrom(device: String) = SyncPayload(device = device, tags = emptyList(), sessions = emptyList())
