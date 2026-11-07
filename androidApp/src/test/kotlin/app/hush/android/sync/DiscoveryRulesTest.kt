// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.sync

import android.app.job.JobInfo
import android.net.NetworkCapabilities
import app.hush.sync.lan.LocalAddress
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoveryRulesTest {
    private val homeWifi =
        listOf(
            LocalAddress.InterfacePrefix(InetAddress.getByName("192.168.1.10"), 24),
            LocalAddress.InterfacePrefix(InetAddress.getByName("2001:db8:d:b47c::130"), 64),
        )

    private fun txt(vararg pairs: Pair<String, String?>): Map<String, ByteArray?> = pairs.associate { (k, v) -> k to v?.toByteArray() }

    @Test
    fun `a sync looks for the paired host by its device id`() {
        val target = DiscoveryTarget.Peer("host-1")
        assertTrue(DiscoveryRules.matches(txt("id" to "host-1"), target))
        assertTrue(DiscoveryRules.matches(txt("id" to "host-1", "pair" to "1"), target))
        assertFalse(DiscoveryRules.matches(txt("id" to "host-2"), target))
        assertFalse(DiscoveryRules.matches(txt("pair" to "1"), target))
        assertFalse(DiscoveryRules.matches(txt("id" to null), target))
    }

    @Test
    fun `pairing looks for a host showing a code`() {
        val target = DiscoveryTarget.ShowingCode
        assertTrue(DiscoveryRules.matches(txt("id" to "host-1", "pair" to "1"), target))
        assertFalse(DiscoveryRules.matches(txt("id" to "host-1"), target))
        assertFalse(DiscoveryRules.matches(txt("id" to "host-1", "pair" to "0"), target))
        assertFalse(DiscoveryRules.matches(txt("pair" to null), target))
    }

    @Test
    fun `only local addresses are tried, IPv4 first and link-local IPv6 last`() {
        val addresses =
            listOf("fe80::1", "8.8.8.8", "2001:db8:d:b47c::70", "fd00::5", "192.168.1.20", "2001:db8::1", "192.168.1.20")
                .map(InetAddress::getByName)
        assertEquals(
            listOf("192.168.1.20", "2001:db8:d:b47c::70", "fd00::5", "fe80::1").map(InetAddress::getByName),
            DiscoveryRules.connectOrder(addresses, homeWifi),
        )
        assertEquals(
            listOf("192.168.1.20", "fd00::5", "fe80::1").map(InetAddress::getByName),
            DiscoveryRules.connectOrder(addresses, emptyList()),
        )
        assertTrue(DiscoveryRules.connectOrder(listOf(InetAddress.getByName("1.1.1.1")), homeWifi).isEmpty())
    }

    @Test
    fun `a global IPv6 answer counts only when it is on the phone's own network`() {
        val target = DiscoveryTarget.Peer("host-1")
        val host = txt("id" to "host-1")
        val onLink = listOf(InetAddress.getByName("2001:db8:d:b47c:1a2b:3c4d:5e6f:7081"))
        assertEquals(DiscoveryRules.Verdict.Usable(onLink, 47447), DiscoveryRules.judge(host, onLink, 47447, target, homeWifi))
        assertEquals(DiscoveryRules.Verdict.NoLocalAddress, DiscoveryRules.judge(host, onLink, 47447, target, emptyList()))
        val offLink = listOf(InetAddress.getByName("2001:db8:d:b47d::70"))
        assertEquals(DiscoveryRules.Verdict.NoLocalAddress, DiscoveryRules.judge(host, offLink, 47447, target, homeWifi))
    }

    @Test
    fun `a resolve update counts only with a matching TXT and a local address`() {
        val target = DiscoveryTarget.ShowingCode
        val pairing = txt("id" to "host-1", "pair" to "1")
        val globalOnly = listOf(InetAddress.getByName("2001:db8:ffff::1"))
        assertEquals(DiscoveryRules.Verdict.NoLocalAddress, DiscoveryRules.judge(pairing, globalOnly, 47447, target, homeWifi))
        assertEquals(DiscoveryRules.Verdict.NoLocalAddress, DiscoveryRules.judge(pairing, emptyList(), 47447, target, homeWifi))
        assertEquals(
            DiscoveryRules.Verdict.OtherHost,
            DiscoveryRules.judge(txt("id" to "host-1"), listOf(InetAddress.getByName("192.168.1.20")), 47447, target, homeWifi),
        )
        assertEquals(
            DiscoveryRules.Verdict.Usable(listOf(InetAddress.getByName("192.168.1.20")), 47447),
            DiscoveryRules.judge(pairing, globalOnly + InetAddress.getByName("192.168.1.20"), 47447, target, homeWifi),
        )
    }

    @Test
    fun `a multicast lock is taken only before the NSD module receives mDNS itself`() {
        assertTrue(DiscoveryRules.needsMulticastLock(sdkInt = 29, tiramisuExtension = 0))
        assertTrue(DiscoveryRules.needsMulticastLock(sdkInt = 31, tiramisuExtension = 6))
        assertFalse(DiscoveryRules.needsMulticastLock(sdkInt = 31, tiramisuExtension = 7))
        assertFalse(DiscoveryRules.needsMulticastLock(sdkInt = 36, tiramisuExtension = 18))
    }

    @Test
    fun `the remembered address is tried first only on Wi-Fi or Ethernet`() {
        assertTrue(DiscoveryRules.tryRememberedFirst("192.168.1.20", 47447, onWifiOrEthernet = true))
        assertFalse(DiscoveryRules.tryRememberedFirst("192.168.1.20", 47447, onWifiOrEthernet = false))
        assertFalse(DiscoveryRules.tryRememberedFirst(null, 47447, onWifiOrEthernet = true))
        assertFalse(DiscoveryRules.tryRememberedFirst("192.168.1.20", null, onWifiOrEthernet = true))
        assertFalse(DiscoveryRules.tryRememberedFirst("192.168.1.20", 0, onWifiOrEthernet = true))
    }

    @Test
    fun `a remembered address is only ever a numeric literal, never a name to look up`() {
        assertEquals(InetAddress.getByName("192.168.1.20"), literalAddress("192.168.1.20"))
        assertEquals(InetAddress.getByName("fd00::5"), literalAddress("fd00::5"))
        assertNull(literalAddress("cafe.de"))
        assertNull(literalAddress("beef"))
        assertNull(literalAddress("desktop.local"))
        assertNull(literalAddress(""))
    }

    @Test
    fun `the background job asks for unmetered Wi-Fi on every API level`() {
        assertEquals(
            SyncJobNetwork.Request(listOf(NetworkCapabilities.TRANSPORT_WIFI), listOf(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)),
            SyncJobNetwork.forSdk(28),
        )
        assertEquals(SyncJobNetwork.forSdk(28), SyncJobNetwork.forSdk(37))
        assertEquals(SyncJobNetwork.Type(JobInfo.NETWORK_TYPE_UNMETERED), SyncJobNetwork.forSdk(26))
        assertEquals(SyncJobNetwork.Type(JobInfo.NETWORK_TYPE_UNMETERED), SyncJobNetwork.forSdk(27))
    }
}
