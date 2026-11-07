// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocker.builtin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlwaysAllowedTest {
    private fun packages(
        launchers: List<String> = emptyList(),
        settingsApps: List<String> = emptyList(),
        defaultDialer: String? = null,
        defaultInputMethod: String? = null,
    ) = AlwaysAllowed.packages("app.hush", launchers, settingsApps, defaultDialer, defaultInputMethod)

    private val fixed =
        setOf("app.hush", "com.android.systemui", "com.android.settings", "com.android.phone", "com.android.server.telecom")

    @Test
    fun `own package and fixed system packages are always allowed`() {
        assertEquals(fixed, packages())
    }

    @Test
    fun `every launcher and settings app is allowed`() {
        val result = packages(launchers = listOf("com.launcher.a", "com.launcher.b"), settingsApps = listOf("com.oem.settings"))

        assertTrue(result.containsAll(listOf("com.launcher.a", "com.launcher.b", "com.oem.settings")))
    }

    @Test
    fun `default dialer is allowed`() {
        assertEquals(fixed + "com.dialer", packages(defaultDialer = "com.dialer"))
    }

    @Test
    fun `keyboard package is taken from the flattened input method component`() {
        assertEquals(fixed + "com.keyboard", packages(defaultInputMethod = "com.keyboard/.ImeService"))
    }

    @Test
    fun `input method without a slash is used whole`() {
        assertEquals(fixed + "com.keyboard", packages(defaultInputMethod = "com.keyboard"))
    }

    @Test
    fun `missing or empty input method adds nothing`() {
        assertEquals(fixed, packages(defaultInputMethod = null))
        assertEquals(fixed, packages(defaultInputMethod = ""))
        assertEquals(fixed, packages(defaultInputMethod = "/.ImeService"))
    }
}
