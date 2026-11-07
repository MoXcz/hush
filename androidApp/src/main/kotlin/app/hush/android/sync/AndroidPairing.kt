// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.sync

import app.hush.core.AppDispatchers
import app.hush.sync.PairedPeer
import app.hush.sync.Pairing
import app.hush.sync.PairingFailure
import app.hush.sync.PairingState
import app.hush.sync.SyncController
import app.hush.sync.lan.PairResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AndroidPairing(
    private val clients: LanClients,
    private val discovery: NsdDiscovery,
    private val permission: LocalNetworkPermission,
    private val controller: SyncController,
    private val dispatchers: AppDispatchers,
) : Pairing {
    private val current = MutableStateFlow<PairingState>(PairingState.Idle)

    override val state: StateFlow<PairingState> = current.asStateFlow()

    override fun open() {
        current.value = PairingState.Idle
    }

    override suspend fun enterCode(code: String) {
        current.value = PairingState.Searching
        try {
            current.value = pair(code)
        } finally {
            // Cancelled half way (the screen closed): don't leave a spinner behind.
            if (current.value == PairingState.Searching || current.value == PairingState.Connecting) {
                current.value = PairingState.Idle
            }
        }
    }

    override fun close() {
        current.value = PairingState.Idle
    }

    private suspend fun pair(code: String): PairingState {
        if (!permission.request()) return PairingState.Failed(PairingFailure.NeedsPermission)
        return withContext(dispatchers.io) {
            val client = clients.client() ?: return@withContext PairingState.Failed(PairingFailure.Error)
            val found =
                when (val result = discovery.find(DiscoveryTarget.ShowingCode)) {
                    is DiscoveryResult.Found -> result
                    DiscoveryResult.NotFound -> return@withContext PairingState.Failed(PairingFailure.NotFound)
                    DiscoveryResult.PermissionDenied -> return@withContext PairingState.Failed(PairingFailure.NeedsPermission)
                }
            current.value = PairingState.Connecting
            var outcome: PairingState = PairingState.Failed(PairingFailure.Error)
            for (address in found.addresses) {
                // Failed is almost always "couldn't connect", so the next address is worth a try.
                outcome =
                    when (val result = cancellableCall { client.pair(address, found.port, code, it) }) {
                        is PairResult.Paired -> {
                            val peer = PairedPeer(result.peer.deviceId, result.peer.name, result.peer.pin, address.hostAddress, found.port)
                            controller.paired(peer)
                            PairingState.Paired(peer)
                        }

                        PairResult.WrongCode -> {
                            PairingState.Failed(PairingFailure.WrongCode)
                        }

                        PairResult.Expired -> {
                            PairingState.Failed(PairingFailure.Expired)
                        }

                        PairResult.TooNew -> {
                            PairingState.Failed(PairingFailure.TooNew)
                        }

                        is PairResult.Failed -> {
                            PairingState.Failed(PairingFailure.Error)
                        }
                    }
                if (outcome != PairingState.Failed(PairingFailure.Error)) break
            }
            outcome
        }
    }
}
