// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HyprlandTest {
    @Test
    fun `an active window event gives its class and title`() {
        assertEquals(
            FocusChange(FocusedWindow("zen", "Dashboard | Boot.dev — Zen Browser")),
            parseEvent("activewindow>>zen,Dashboard | Boot.dev — Zen Browser"),
        )
    }

    @Test
    fun `a title with commas is split on the first comma only`() {
        assertEquals(FocusChange(FocusedWindow("kitty", "a, b, c")), parseEvent("activewindow>>kitty,a, b, c"))
    }

    @Test
    fun `an empty class means nothing has focus`() {
        assertEquals(FocusChange(null), parseEvent("activewindow>>,"))
    }

    @Test
    fun `other events and malformed lines are ignored`() {
        assertNull(parseEvent("activewindowv2>>55769d52ebb0"))
        assertNull(parseEvent("windowtitlev2>>55769d52ebb0,Title"))
        assertNull(parseEvent("focusedmon>>DP-3,2"))
        assertNull(parseEvent("activewindow>>noComma"))
        assertNull(parseEvent(""))
    }

    @Test
    fun `the active window reply gives class and title`() {
        val reply =
            """
            Window 55769c583010 -> notes - Obsidian:
            	mapped: 1
            	class: md.obsidian.Obsidian
            	title: notes - Obsidian: plans
            	initialClass: md.obsidian.Obsidian
            	xdgTag: 
            """.trimIndent()
        assertEquals(FocusedWindow("md.obsidian.Obsidian", "notes - Obsidian: plans"), parseActiveWindow(reply))
    }

    @Test
    fun `no active window is null`() {
        assertNull(parseActiveWindow("Invalid"))
        assertNull(parseActiveWindow(""))
    }

    @Test
    fun `the reader is unavailable without a Hyprland signature`() {
        assertNull(hyprlandSocketDirectory(mapOf("XDG_RUNTIME_DIR" to "/run/user/1000")))
        assertNull(hyprlandSocketDirectory(mapOf("XDG_RUNTIME_DIR" to "/run/user/1000", "HYPRLAND_INSTANCE_SIGNATURE" to "")))
        assertNull(hyprlandSocketDirectory(mapOf("HYPRLAND_INSTANCE_SIGNATURE" to "abc")))
        assertEquals(
            Path.of("/run/user/1000/hypr/abc"),
            hyprlandSocketDirectory(mapOf("XDG_RUNTIME_DIR" to "/run/user/1000", "HYPRLAND_INSTANCE_SIGNATURE" to "abc")),
        )
    }
}
