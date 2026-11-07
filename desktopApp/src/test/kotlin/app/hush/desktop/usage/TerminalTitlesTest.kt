// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalTitlesTest {
    private fun kitty(title: String) = FocusTarget.of(FocusedWindow("kitty", title))

    private val kitty = FocusTarget("kitty", null)
    private val neovim = FocusTarget("term:nvim", "Neovim")

    @Test
    fun `a command line in a terminal is its program`() {
        assertEquals(neovim, kitty("nvim notes.md"))
        assertEquals(FocusTarget("term:htop", "htop"), kitty("htop"))
    }

    @Test
    fun `sudo is skipped`() {
        assertEquals(neovim, kitty("sudo nvim /etc/hosts"))
    }

    @Test
    fun `a program run from a bin directory is the program`() {
        assertEquals(FocusTarget("term:htop", "htop"), kitty("/usr/bin/htop"))
        assertEquals(neovim, kitty("~/.local/bin/nvim notes.md"))
    }

    @Test
    fun `Neovim's and Vim's own titles are recognised`() {
        assertEquals(neovim, kitty("notes.md (~/x) - NVIM"))
        assertEquals(neovim, kitty("notes.md + (~/x) - NVIM"))
        assertEquals(FocusTarget("term:vim", "Vim"), kitty("notes.md (~/x) - VIM"))
    }

    @Test
    fun `aliases fold into one program`() {
        assertEquals(FocusTarget("term:vim", "Vim"), kitty("vi notes.md"))
        assertEquals(FocusTarget("term:hx", "Helix"), kitty("helix notes.md"))
        assertEquals(FocusTarget("term:python", "Python"), kitty("python3 script.py"))
    }

    @Test
    fun `a prompt, a directory or an unlisted command stays with the terminal`() {
        assertEquals(kitty, kitty("~/workspace/hush"))
        assertEquals(kitty, kitty("~/src/nvim"))
        assertEquals(kitty, kitty("./nvim"))
        assertEquals(kitty, kitty("zsh"))
        assertEquals(kitty, kitty("cat secret.txt"))
        assertEquals(kitty, kitty("sudo"))
        assertEquals(kitty, kitty(""))
    }

    @Test
    fun `arguments and paths never reach the key or the label`() {
        listOf("nvim ~/diary/2026-11-07.md", "sudo nvim /home/me/private/notes.md", "diary.md (~/private) - NVIM")
            .map { kitty(it) }
            .forEach { target ->
                assertEquals(neovim, target)
                assertFalse("diary" in target.key || "private" in target.key)
            }
    }

    @Test
    fun `only terminals give programs`() {
        assertEquals(FocusTarget("zen", null), FocusTarget.of(FocusedWindow("zen", "nvim notes.md")))
        assertNull(programFromTitle("org.gnome.Nautilus", "nvim"))
        assertEquals(neovim, FocusTarget.of(FocusedWindow("Alacritty", "nvim notes.md")))
        assertEquals(neovim, FocusTarget.of(FocusedWindow("com.mitchellh.ghostty", "nvim")))
        assertTrue(isTerminal("org.wezfurlong.wezterm"))
        assertFalse(isTerminal("zen"))
    }

    @Test
    fun `a terminal's time is split between its program and the terminal`() {
        val entries =
            listOf(
                FocusEntry(0, kitty("~/workspace/hush")),
                FocusEntry(10_000, kitty("nvim notes.md")),
                FocusEntry(70_000, kitty("~/workspace/hush")),
                FocusEntry(90_000, null),
            )
        assertEquals(mapOf(kitty to 30_000L, neovim to 60_000L), FocusTime.perTarget(entries, 0, 100_000, 100_000))
    }
}
