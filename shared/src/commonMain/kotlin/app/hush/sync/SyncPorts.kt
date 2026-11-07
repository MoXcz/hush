// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Instant

/** This device's part in a sync. Bound by each platform through `DeviceFeatures`. */
enum class SyncRole {
    /** Listens on the local network and shows the pairing code (desktop). */
    Host,

    /** Finds the host, types its code and starts every sync (phone). */
    Client,

    /** No sync here: the sync UI is hidden. */
    None,
}

/** The one device this install syncs with. [pin] is opaque here (the transport checks it). */
data class PairedPeer(
    val deviceId: String,
    val name: String,
    val pin: String,
    /** Where the client last reached the host; null on the host and before the first sync. */
    val host: String? = null,
    val port: Int? = null,
)

/** Carries one sync exchange to the paired host. Client only. */
interface SyncTransport {
    /**
     * Sends [outgoing] (an encoded payload) to [peer] and returns the host's payload. Main-safe;
     * expected failures are results, never exceptions.
     */
    suspend fun exchange(
        peer: PairedPeer,
        outgoing: ByteArray,
    ): ExchangeResult
}

sealed interface ExchangeResult {
    /** The host's payload. The host has received ours too. */
    class Received(
        val payload: ByteArray,
    ) : ExchangeResult

    /** The host wasn't found on this network (or isn't running). */
    data object NotFound : ExchangeResult

    /** The host doesn't know this device any more, or its key changed: pair again. */
    data object Rejected : ExchangeResult

    /** The host speaks a newer protocol. */
    data object TooNew : ExchangeResult

    /** The connection broke. Nothing to do but try again later; a half-done sync is harmless. */
    data object Failed : ExchangeResult
}

/**
 * Pairing with the other device. The host shows a code ([PairingState.ShowingCode], replaced by a
 * new one after every attempt or expiry); the client types it with [enterCode]. Whoever completes a
 * pairing stores the peer through [SyncController.paired] before reporting [PairingState.Paired].
 */
interface Pairing {
    val state: StateFlow<PairingState>

    /** Starts, or starts again: the host shows a fresh code, the client gets ready for one. */
    fun open()

    /** Client: finds the host showing a code and pairs with [code] (any case, dashes allowed). */
    suspend fun enterCode(code: String)

    /** Stops pairing: the host hides its code. Safe to call when not pairing. */
    fun close()

    /**
     * Host: false while other devices can't find this one on the network (its service
     * advertisement isn't running). Always true on a client.
     */
    val discoverable: StateFlow<Boolean> get() = ALWAYS_DISCOVERABLE
}

private val ALWAYS_DISCOVERABLE: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

sealed interface PairingState {
    data object Idle : PairingState

    /** Host: [code] is shown as typed (`XXXX-XXXX-XXXX`) until [expiresAt]. */
    data class ShowingCode(
        val code: String,
        val expiresAt: Instant,
    ) : PairingState

    /** Client: looking for a host showing a code. */
    data object Searching : PairingState

    /** Client: found one, checking the code. */
    data object Connecting : PairingState

    data class Paired(
        val peer: PairedPeer,
    ) : PairingState

    data class Failed(
        val reason: PairingFailure,
    ) : PairingState
}

enum class PairingFailure {
    WrongCode,
    NotFound,
    Expired,
    TooNew,

    /** The system's local-network permission was denied. */
    NeedsPermission,
    Error,
}

/** Runs syncs in the background while paired (Android: a periodic job). */
interface SyncScheduler {
    fun onPairedChanged(paired: Boolean)
}

/** Every sync port doing nothing, for platforms (and builds) without sync. */
class NoSync :
    SyncTransport,
    Pairing,
    SyncScheduler {
    override suspend fun exchange(
        peer: PairedPeer,
        outgoing: ByteArray,
    ): ExchangeResult = ExchangeResult.NotFound

    override val state: StateFlow<PairingState> = MutableStateFlow<PairingState>(PairingState.Idle).asStateFlow()

    override fun open() = Unit

    override suspend fun enterCode(code: String) = Unit

    override fun close() = Unit

    override fun onPairedChanged(paired: Boolean) = Unit
}
