// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.sync

import app.hush.core.AppDispatchers
import app.hush.sync.ExchangeResult
import app.hush.sync.PairedPeer
import app.hush.sync.PeerStore
import app.hush.sync.SyncTransport
import app.hush.sync.lan.SyncClient
import app.hush.sync.lan.SyncResult
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException

// The remembered address first, then the host found by its device id.
class AndroidSyncTransport(
    private val clients: LanClients,
    private val discovery: NsdDiscovery,
    private val permission: LocalNetworkPermission,
    private val peers: PeerStore,
    private val dispatchers: AppDispatchers,
) : SyncTransport {
    override suspend fun exchange(
        peer: PairedPeer,
        outgoing: ByteArray,
    ): ExchangeResult =
        withContext(dispatchers.io) {
            // Never asked for here: the permission is requested on Pair, with the user looking.
            if (!permission.granted) return@withContext ExchangeResult.NotFound
            val client = clients.client() ?: return@withContext ExchangeResult.Failed
            val host = peer.host
            val port = peer.port
            if (host != null && port != null && DiscoveryRules.tryRememberedFirst(host, port, clients.onWifiOrEthernet())) {
                val remembered = literalAddress(host)
                if (remembered != null) {
                    when (val result = sync(client, remembered, port, peer, outgoing)) {
                        is ExchangeResult.Received, ExchangeResult.TooNew -> return@withContext result

                        // Another device may have that address now, so look the host up before giving up.
                        ExchangeResult.Rejected, ExchangeResult.Failed, ExchangeResult.NotFound -> Unit
                    }
                }
            }
            when (val found = discovery.find(DiscoveryTarget.Peer(peer.deviceId))) {
                DiscoveryResult.NotFound, DiscoveryResult.PermissionDenied -> {
                    ExchangeResult.NotFound
                }

                is DiscoveryResult.Found -> {
                    var last: ExchangeResult = ExchangeResult.Failed
                    for (address in found.addresses) {
                        last = sync(client, address, found.port, peer, outgoing)
                        if (last != ExchangeResult.Failed) break
                    }
                    last
                }
            }
        }

    private suspend fun sync(
        client: SyncClient,
        address: InetAddress,
        port: Int,
        peer: PairedPeer,
        outgoing: ByteArray,
    ): ExchangeResult {
        val result =
            when (val reply = cancellableCall { client.sync(address, port, peer.pin, outgoing, it) }) {
                is SyncResult.Received -> ExchangeResult.Received(reply.payload)
                SyncResult.Rejected -> ExchangeResult.Rejected
                SyncResult.TooNew -> ExchangeResult.TooNew
                is SyncResult.Failed -> ExchangeResult.Failed
            }
        if (result is ExchangeResult.Received) address.hostAddress?.let { peers.rememberAddress(it, port) }
        return result
    }
}

// Only numeric literals: a remembered address must never trigger a DNS lookup.
internal fun literalAddress(host: String): InetAddress? {
    val literal = host.substringBefore('%')
    val ipv4 = literal.isNotEmpty() && literal.all { it.isDigit() || it == '.' }
    val ipv6 = ':' in literal && literal.all { it.isDigit() || it in "abcdefABCDEF.:" }
    if (!ipv4 && !ipv6) return null
    return try {
        InetAddress.getByName(host)
    } catch (_: UnknownHostException) {
        null
    }
}
