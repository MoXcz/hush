// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.lan

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalAddressTest {
    @Test
    fun `only loopback, private, link-local and unique local addresses are local`() {
        val table =
            mapOf(
                "127.0.0.1" to true,
                "::1" to true,
                "10.1.2.3" to true,
                "172.16.0.1" to true,
                "172.31.255.254" to true,
                "192.168.1.10" to true,
                "169.254.10.20" to true,
                "fe80::1" to true,
                "fc00::1" to true,
                "fd12:3456::1" to true,
                "::ffff:192.168.1.1" to true,
                "172.32.0.1" to false,
                "8.8.8.8" to false,
                "100.64.0.1" to false,
                "0.0.0.0" to false,
                "224.0.0.251" to false,
                "2001:db8::1" to false,
                "fe00::1" to false,
                "::ffff:8.8.8.8" to false,
            )
        for ((literal, local) in table) {
            assertEquals(local, LocalAddress.isLocal(InetAddress.getByName(literal)), literal)
        }
    }

    private fun ip(literal: String) = InetAddress.getByName(literal)

    private fun prefix(
        literal: String,
        length: Int,
    ) = LocalAddress.InterfacePrefix(ip(literal), length)

    @Test
    fun `a public IPv4 address on one of this device's own subnets is local`() {
        val own = listOf(prefix("203.0.113.70", 24))
        assertTrue(LocalAddress.isLocal(ip("203.0.113.5"), own))
        assertFalse(LocalAddress.isLocal(ip("203.0.114.5"), own))
        assertTrue(LocalAddress.isLocal(ip("198.51.100.200"), listOf(prefix("198.51.100.129", 25))))
        assertFalse(LocalAddress.isLocal(ip("198.51.100.100"), listOf(prefix("198.51.100.129", 25))))
    }

    @Test
    fun `a global IPv6 address in this device's own prefix is local`() {
        val own = listOf(prefix("2001:db8:d:b47c::70", 64))
        assertTrue(LocalAddress.isLocal(ip("2001:db8:d:b47c:1234:5678:9abc:def0"), own))
        assertFalse(LocalAddress.isLocal(ip("2001:db8:d:b47d::1"), own))
        assertFalse(LocalAddress.isLocal(ip("2001:db8::1"), own))
    }

    @Test
    fun `prefixes broader than a LAN never make an address local`() {
        assertFalse(LocalAddress.isLocal(ip("2001:db8::1"), listOf(prefix("2001:db8:d:b47c::70", 32))))
        assertTrue(LocalAddress.isLocal(ip("2001:db8:d:ffff::1"), listOf(prefix("2001:db8:d:b47c::70", 48))))
        assertFalse(LocalAddress.isLocal(ip("8.1.2.3"), listOf(prefix("8.8.8.8", 7))))
        assertTrue(LocalAddress.isLocal(ip("8.1.2.3"), listOf(prefix("8.8.8.8", 8))))
        assertFalse(LocalAddress.isLocal(ip("8.8.8.8"), listOf(prefix("8.8.8.8", 0))))
        assertFalse(LocalAddress.isLocal(ip("8.8.8.8"), listOf(prefix("8.8.8.8", 33))))
    }

    @Test
    fun `an IPv4 prefix never matches an IPv6 address and the other way round`() {
        val own = listOf(prefix("203.0.113.70", 24), prefix("2001:db8:d:b47c::70", 64))
        assertFalse(LocalAddress.isLocal(ip("2001:db8::1"), listOf(prefix("203.0.113.70", 8))))
        assertFalse(LocalAddress.isLocal(ip("32.6.16.142"), listOf(prefix("2001:db8:d:b47c::70", 64))))
        assertTrue(LocalAddress.isLocal(ip("203.0.113.9"), own))
        assertTrue(LocalAddress.isLocal(ip("2001:db8:d:b47c::9"), own))
        assertFalse(LocalAddress.isLocal(ip("ff02::fb"), listOf(prefix("ff02::1", 64))))
    }

    @Test
    fun `this device's own interface addresses are local here`() {
        for (own in LocalAddress.interfacePrefixes()) {
            val broad = if (own.address.address.size == 4) own.prefixLength < 8 else own.prefixLength < 48
            if (!broad && !own.address.isMulticastAddress) assertTrue(LocalAddress.isLocalHere(own.address), own.toString())
        }
        assertTrue(LocalAddress.isLocalHere(ip("127.0.0.1")))
    }
}
