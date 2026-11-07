// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocker.builtin

import kotlin.test.Test
import kotlin.test.assertEquals

class WithServiceTest {
    private val ours = "app.hush/app.hush.blocker.builtin.AppBlockerService"
    private val debug = "app.hush.debug/app.hush.blocker.builtin.AppBlockerService"

    private fun with(
        enabled: String?,
        on: Boolean,
    ) = withService(enabled, ours, on) { it == ours }

    @Test
    fun `turning on adds ours and keeps the other services`() {
        assertEquals("$debug:$ours", with(debug, on = true))
        assertEquals(ours, with(null, on = true))
        assertEquals(ours, with("", on = true))
    }

    @Test
    fun `turning on twice doesn't list ours twice`() {
        assertEquals("$debug:$ours", with("$ours:$debug", on = true))
    }

    @Test
    fun `turning off removes only ours`() {
        assertEquals(debug, with("$debug:$ours", on = false))
        assertEquals("", with(ours, on = false))
        assertEquals("", with(null, on = false))
    }
}
