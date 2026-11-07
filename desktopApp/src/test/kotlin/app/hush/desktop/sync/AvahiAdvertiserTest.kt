// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.sync

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AvahiAdvertiserTest {
    @Test
    fun `the service carries the device id, and pair=1 only while pairing`() {
        assertEquals(
            listOf("avahi-publish", "-s", "Hush on desktop", "_hush._tcp", "47447", "id=abc"),
            avahiArguments("desktop", 47447, "abc", pairing = false),
        )
        assertEquals(
            listOf("avahi-publish", "-s", "Hush on desktop", "_hush._tcp", "50123", "id=abc", "pair=1"),
            avahiArguments("desktop", 50123, "abc", pairing = true),
        )
    }

    @Test
    fun `long host names are cut to an mDNS instance name`() {
        val name = serviceName("ö".repeat(60))
        assertTrue(name.startsWith("Hush on ö"))
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 63)
    }

    @Test
    fun `a missing avahi-publish is reported as not advertising`() {
        val reports = LinkedBlockingQueue<Boolean>()
        AvahiAdvertiser("desktop", 47447, "abc", reports::put, executable = "/nonexistent/avahi-publish").use {
            it.start()
            assertEquals(false, reports.poll(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `an avahi-publish that exits at once is reported as not advertising`() {
        val reports = LinkedBlockingQueue<Boolean>()
        AvahiAdvertiser("desktop", 47447, "abc", reports::put, executable = "false").use {
            it.start()
            assertEquals(false, reports.poll(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `the host name comes from etc-hostname, else the resolver`() {
        assertEquals("desktop", localHostName(etcHostname = { "desktop\n" }, fallback = { "other" }))
        assertEquals("other", localHostName(etcHostname = { null }, fallback = { "other" }))
        assertEquals("other", localHostName(etcHostname = { "  " }, fallback = { "other" }))
        assertEquals("Computer", localHostName(etcHostname = { null }, fallback = { "" }))
    }
}
