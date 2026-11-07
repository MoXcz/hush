// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.sync

import app.hush.sync.lan.LocalAddress
import java.net.Inet4Address
import java.net.InetAddress

sealed interface DiscoveryTarget {
    data class Peer(
        val deviceId: String,
    ) : DiscoveryTarget

    data object ShowingCode : DiscoveryTarget
}

internal object DiscoveryRules {
    const val SERVICE_TYPE = "_hush._tcp"

    fun matches(
        txt: Map<String, ByteArray?>,
        target: DiscoveryTarget,
    ): Boolean =
        when (target) {
            is DiscoveryTarget.Peer -> txt.value("id") == target.deviceId
            DiscoveryTarget.ShowingCode -> txt.value("pair") == "1"
        }

    sealed interface Verdict {
        data class Usable(
            val addresses: List<InetAddress>,
            val port: Int,
        ) : Verdict

        data object OtherHost : Verdict

        /** Only addresses we don't connect to, e.g. a global IPv6 one outside this phone's networks. */
        data object NoLocalAddress : Verdict
    }

    // NSD may send the local address only in a later resolve update.
    fun judge(
        txt: Map<String, ByteArray?>,
        addresses: List<InetAddress>,
        port: Int,
        target: DiscoveryTarget,
        ownPrefixes: List<LocalAddress.InterfacePrefix>,
    ): Verdict {
        if (!matches(txt, target)) return Verdict.OtherHost
        val local = connectOrder(addresses, ownPrefixes)
        return if (local.isEmpty()) Verdict.NoLocalAddress else Verdict.Usable(local, port)
    }

    // Link-local IPv6 needs the right scope, so it goes last.
    fun connectOrder(
        addresses: List<InetAddress>,
        ownPrefixes: List<LocalAddress.InterfacePrefix>,
    ): List<InetAddress> =
        addresses
            .filter { LocalAddress.isLocal(it, ownPrefixes) }
            .distinct()
            .sortedBy {
                when {
                    it is Inet4Address -> 0
                    !it.isLinkLocalAddress -> 1
                    else -> 2
                }
            }

    // Until the NSD module receives mDNS itself (Android 11+ with Tiramisu extension 7), replies need the lock.
    fun needsMulticastLock(
        sdkInt: Int,
        tiramisuExtension: Int,
    ): Boolean = sdkInt < 30 || tiramisuExtension < 7

    // A remembered address is only likely to still be right on a home-network kind of link.
    fun tryRememberedFirst(
        host: String?,
        port: Int?,
        onWifiOrEthernet: Boolean,
    ): Boolean = onWifiOrEthernet && !host.isNullOrBlank() && port != null && port in 1..65535

    private fun Map<String, ByteArray?>.value(key: String): String? = get(key)?.toString(Charsets.UTF_8)
}
