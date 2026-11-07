// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.usage

import kotlin.test.Test
import kotlin.test.assertEquals

class ForegroundTimeTest {
    private val min = 60_000L

    private fun on(
        pkg: String,
        at: Long,
        activity: String? = null,
    ) = UsageEvent(pkg, at, foreground = true, activity)

    private fun off(
        pkg: String,
        at: Long,
        activity: String? = null,
    ) = UsageEvent(pkg, at, foreground = false, activity)

    @Test
    fun `resume and pause pairs add up per app`() {
        val events = listOf(on("a", 0), off("a", 2 * min), on("b", 2 * min), off("b", 3 * min), on("a", 3 * min), off("a", 4 * min))
        assertEquals(mapOf("a" to 3 * min, "b" to 1 * min), ForegroundTime.perApp(events, 0, 10 * min))
    }

    @Test
    fun `time is clipped to the window at both edges`() {
        val events = listOf(on("a", -5 * min), off("a", 2 * min), on("b", 8 * min), off("b", 15 * min))
        assertEquals(mapOf("a" to 2 * min, "b" to 2 * min), ForegroundTime.perApp(events, 0, 10 * min))
    }

    @Test
    fun `an app still in front at the end counts up to the end`() {
        assertEquals(mapOf("a" to 4 * min), ForegroundTime.perApp(listOf(on("a", 6 * min)), 0, 10 * min))
    }

    @Test
    fun `a missing pause ends when the next app comes forward`() {
        val events = listOf(on("a", 0), on("b", 3 * min), off("b", 5 * min))
        assertEquals(mapOf("a" to 3 * min, "b" to 2 * min), ForegroundTime.perApp(events, 0, 10 * min))
    }

    @Test
    fun `a pause from another app doesn't end the one in front`() {
        val events = listOf(on("a", 0), off("b", 1 * min), off("a", 4 * min))
        assertEquals(mapOf("a" to 4 * min), ForegroundTime.perApp(events, 0, 10 * min))
    }

    @Test
    fun `events out of order are sorted first`() {
        val events = listOf(off("a", 2 * min), on("a", 0))
        assertEquals(mapOf("a" to 2 * min), ForegroundTime.perApp(events, 0, 10 * min))
    }

    @Test
    fun `our own package and launchers are left out without giving their time to anyone`() {
        val events = listOf(on("hush", 0), on("launcher", 1 * min), on("a", 2 * min), off("a", 3 * min))
        assertEquals(mapOf("a" to 1 * min), ForegroundTime.perApp(events, 0, 10 * min, excluded = setOf("hush", "launcher")))
    }

    @Test
    fun `visits shorter than five seconds in total are dropped`() {
        val events = listOf(on("flash", 0), off("flash", 4_000), on("a", 1 * min), off("a", 2 * min))
        assertEquals(mapOf("a" to 1 * min), ForegroundTime.perApp(events, 0, 10 * min))
    }

    @Test
    fun `events after the window are ignored`() {
        val events = listOf(on("a", 0), off("a", 1 * min), on("b", 11 * min))
        assertEquals(mapOf("a" to 1 * min), ForegroundTime.perApp(events, 0, 10 * min))
    }

    @Test
    fun `only the default launcher is excluded, not other home handlers like settings`() {
        val excluded = ForegroundTime.excluded("app.hush", defaultHome = "com.android.launcher")
        assertEquals(setOf("app.hush", "com.android.systemui", "com.android.launcher"), excluded)
        val events = listOf(on("com.android.launcher", 0), on("com.android.settings", min), off("com.android.settings", 2 * min))
        assertEquals(mapOf("com.android.settings" to min), ForegroundTime.perApp(events, 0, 3 * min, excluded))
    }

    @Test
    fun `the resolver is not excluded when no default launcher is set`() {
        assertEquals(setOf("app.hush", "com.android.systemui"), ForegroundTime.excluded("app.hush", "android"))
        assertEquals(setOf("app.hush", "com.android.systemui"), ForegroundTime.excluded("app.hush", null))
    }

    @Test
    fun `a second activity resumed before the first pauses keeps the app in front`() {
        val events = listOf(on("a", 0, "A1"), on("a", 2 * min, "A2"), off("a", 2 * min, "A1"), off("a", 5 * min, "A2"))
        assertEquals(mapOf("a" to 5 * min), ForegroundTime.perApp(events, 0, 10 * min))
    }

    @Test
    fun `the first activity pausing before the second resumes keeps the app's time`() {
        val events = listOf(on("a", 0, "A1"), off("a", 2 * min, "A1"), on("a", 2 * min, "A2"), off("a", 5 * min, "A2"))
        assertEquals(mapOf("a" to 5 * min), ForegroundTime.perApp(events, 0, 10 * min))
    }

    @Test
    fun `without class names a late pause of an older activity is ignored`() {
        val events = listOf(on("a", 0), on("a", 2 * min), off("a", 2 * min), off("a", 5 * min))
        assertEquals(mapOf("a" to 5 * min), ForegroundTime.perApp(events, 0, 10 * min))
    }

    @Test
    fun `another app resuming still ends an app with activities left resumed`() {
        val events =
            listOf(on("a", 0, "A1"), on("a", min, "A2"), on("b", 3 * min, "B1"), off("a", 4 * min, "A2"), off("b", 6 * min, "B1"))
        assertEquals(mapOf("a" to 3 * min, "b" to 3 * min), ForegroundTime.perApp(events, 0, 10 * min))
    }
}
