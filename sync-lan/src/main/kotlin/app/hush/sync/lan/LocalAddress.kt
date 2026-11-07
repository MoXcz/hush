// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.lan

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

/** Which addresses count as the local network. Both client and server refuse every other address. */
object LocalAddress {
    /** One of this device's own interface addresses and its prefix length. */
    data class InterfacePrefix(
        val address: InetAddress,
        val prefixLength: Int,
    )

    // Narrower than this isn't "the LAN": a misreported /0 would otherwise accept the whole internet.
    private const val MIN_IPV4_PREFIX = 8
    private const val MIN_IPV6_PREFIX = 48

    // Loopback, RFC 1918, link-local, and IPv6 unique local (fc00::/7). IPv4-mapped IPv6 addresses
    // are already unwrapped to Inet4Address by InetAddress.
    fun isLocal(address: InetAddress): Boolean =
        when {
            address.isLoopbackAddress || address.isLinkLocalAddress -> true
            address is Inet4Address -> address.isSiteLocalAddress
            address is Inet6Address -> (address.address[0].toInt() and 0xFE) == 0xFC
            else -> false
        }

    /**
     * [isLocal], or on the same link as one of [prefixes]: home routers often hand out global IPv6
     * addresses, and mDNS may answer with only those.
     */
    fun isLocal(
        address: InetAddress,
        prefixes: List<InterfacePrefix>,
    ): Boolean = isLocal(address) || prefixes.any { isOnLink(address, it) }

    /** [isLocal] against this device's current interfaces. */
    fun isLocalHere(address: InetAddress): Boolean = isLocal(address) || interfacePrefixes().any { isOnLink(address, it) }

    /** The addresses of this device's up, non-loopback interfaces; empty if they can't be read. */
    fun interfacePrefixes(): List<InterfacePrefix> =
        try {
            NetworkInterface
                .getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { entry -> entry.address?.let { InterfacePrefix(it, entry.networkPrefixLength.toInt()) } }
        } catch (_: SocketException) {
            emptyList()
        }

    internal fun isOnLink(
        address: InetAddress,
        prefix: InterfacePrefix,
    ): Boolean {
        val target = address.address
        val own = prefix.address.address
        if (target.size != own.size || address.isMulticastAddress || address.isAnyLocalAddress) return false
        val minimum = if (own.size == 4) MIN_IPV4_PREFIX else MIN_IPV6_PREFIX
        val length = prefix.prefixLength
        if (length < minimum || length > own.size * 8) return false
        val fullBytes = length / 8
        for (i in 0 until fullBytes) {
            if (target[i] != own[i]) return false
        }
        val rest = length % 8
        if (rest == 0) return true
        val mask = (0xFF shl (8 - rest)) and 0xFF
        return (target[fullBytes].toInt() and mask) == (own[fullBytes].toInt() and mask)
    }
}
