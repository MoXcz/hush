// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Why a sync runs. */
enum class SyncTrigger {
    /** The app or the sync screen opened (or pairing just finished). Throttled. */
    Open,

    /** The periodic background job. Not throttled: the system already spaces it out. */
    Background,
}

sealed interface SyncStatus {
    data object Loading : SyncStatus

    data object Unpaired : SyncStatus

    data class Paired(
        val peer: PairedPeer,
        val lastSyncedAt: Instant?,
        val lastResult: SyncOutcome?,
        val syncing: Boolean,
    ) : SyncStatus
}

/** Every exchange (client [syncNow], host [serve], pairing changes) runs under one lock, so syncs never overlap. */
class SyncController(
    private val store: SyncStore,
    private val peers: PeerStore,
    private val transport: SyncTransport,
    private val scheduler: SyncScheduler,
    private val identity: DeviceIdentity,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val syncing = MutableStateFlow(false)
    private var lastAttempt: Instant? = null

    val status: StateFlow<SyncStatus> =
        combine(peers.state, syncing) { state, syncing ->
            val peer = state.peer
            if (peer == null) {
                SyncStatus.Unpaired
            } else {
                SyncStatus.Paired(peer, state.lastSyncedAt, state.lastResult, syncing)
            }
        }.stateIn(scope, SharingStarted.Eagerly, SyncStatus.Loading)

    /**
     * Client: sends this device's data to the peer and merges what comes back. Returns null when
     * nothing ran (unpaired, or an [SyncTrigger.Open] within a minute of the last attempt).
     */
    suspend fun syncNow(trigger: SyncTrigger): SyncOutcome? =
        mutex.withLock {
            val peer = peerOrNull() ?: return null
            val now = clock.now()
            val last = lastAttempt
            if (trigger == SyncTrigger.Open && last != null && now - last < THROTTLE) return null
            lastAttempt = now
            syncing.value = true
            try {
                val outcome =
                    try {
                        exchangeWith(peer)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // e.g. the database or DataStore failed: a failed sync, not a crash.
                        SyncOutcome.Failed
                    }
                recordQuietly(outcome)
                outcome
            } finally {
                syncing.value = false
            }
        }

    /** [syncNow] in the application scope, so leaving a screen doesn't cut a sync short. */
    fun launchSync(trigger: SyncTrigger) {
        scope.launch { syncNow(trigger) }
    }

    /**
     * Host: answers a sync from [peerId], or null (the host closes without answering) when [incoming]
     * is unreadable, not from [peerId], or fails. Exports before merging, so both sides get the same union.
     */
    suspend fun serve(
        peerId: String,
        incoming: ByteArray,
    ): ByteArray? =
        mutex.withLock {
            val payload =
                when (val decoded = SyncCodec.decode(incoming)) {
                    is DecodeResult.Ok -> {
                        decoded.payload
                    }

                    DecodeResult.TooNew -> {
                        recordQuietly(SyncOutcome.TooNew)
                        return@withLock null
                    }

                    DecodeResult.Malformed -> {
                        recordQuietly(SyncOutcome.Failed)
                        return@withLock null
                    }
                }
            if (payload.device != peerId) {
                recordQuietly(SyncOutcome.Rejected)
                return@withLock null
            }
            try {
                val self = identity.deviceId()
                val outgoing = SyncCodec.encode(store.export(self))
                store.apply(payload, self, clock.now())
                recordQuietly(SyncOutcome.Synced)
                outgoing
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                recordQuietly(SyncOutcome.Failed)
                null
            }
        }

    /** Stores a newly paired device (replacing any other) and starts background syncs. */
    suspend fun paired(peer: PairedPeer) {
        mutex.withLock {
            peers.save(peer)
            lastAttempt = null
        }
        scheduler.onPairedChanged(true)
    }

    /** Forgets the peer on this device only; the other device keeps its record until it pairs again. */
    suspend fun unpair() {
        mutex.withLock {
            peers.clear()
            lastAttempt = null
        }
        scheduler.onPairedChanged(false)
    }

    private suspend fun exchangeWith(peer: PairedPeer): SyncOutcome {
        val outgoing = SyncCodec.encode(store.export(identity.deviceId()))
        return when (val result = transport.exchange(peer, outgoing)) {
            is ExchangeResult.Received -> applyIncoming(peer.deviceId, result.payload)
            ExchangeResult.NotFound -> SyncOutcome.NotFound
            ExchangeResult.Rejected -> SyncOutcome.Rejected
            ExchangeResult.TooNew -> SyncOutcome.TooNew
            ExchangeResult.Failed -> SyncOutcome.Failed
        }
    }

    private suspend fun applyIncoming(
        peerId: String,
        bytes: ByteArray,
    ): SyncOutcome =
        when (val decoded = SyncCodec.decode(bytes)) {
            is DecodeResult.Ok -> {
                if (decoded.payload.device != peerId) {
                    SyncOutcome.Rejected
                } else {
                    store.apply(decoded.payload, identity.deviceId(), clock.now())
                    SyncOutcome.Synced
                }
            }

            DecodeResult.TooNew -> {
                SyncOutcome.TooNew
            }

            DecodeResult.Malformed -> {
                SyncOutcome.Failed
            }
        }

    private suspend fun peerOrNull(): PairedPeer? =
        try {
            peers.peer()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    // The outcome matters more than its record: a failed write mustn't turn into a crash.
    private suspend fun recordQuietly(outcome: SyncOutcome) {
        try {
            peers.recordResult(outcome, syncedAt = if (outcome == SyncOutcome.Synced) clock.now() else null)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private companion object {
        val THROTTLE = 60.seconds
    }
}
