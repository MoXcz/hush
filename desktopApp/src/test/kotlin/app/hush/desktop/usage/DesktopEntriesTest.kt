// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopEntriesTest {
    private val user = Files.createTempDirectory("user-apps").toFile()
    private val system = Files.createTempDirectory("system-apps").toFile()

    private fun write(
        dir: File,
        name: String,
        text: String,
    ) {
        File(dir, name).apply { parentFile.mkdirs() }.writeText(text.trimIndent())
    }

    private fun labels(vararg classes: String) = DesktopEntries(listOf(user, system)).labels(classes.toList())

    @Test
    fun `StartupWMClass matches the window class, ignoring case`() {
        write(system, "obsidian.desktop", "[Desktop Entry]\nName=Obsidian\nStartupWMClass=md.obsidian.Obsidian")
        assertEquals(mapOf("md.obsidian.obsidian" to "Obsidian"), labels("md.obsidian.obsidian"))
    }

    @Test
    fun `the desktop file id matches when there is no StartupWMClass`() {
        write(system, "kitty.desktop", "[Desktop Entry]\nName=kitty")
        write(system, "helium.desktop", "[Desktop Entry]\nName=Helium Browser\n\n[Desktop Action new-window]\nName=New Window")
        assertEquals(mapOf("kitty" to "kitty", "helium" to "Helium Browser"), labels("kitty", "helium"))
    }

    @Test
    fun `a subdirectory is part of the id`() {
        write(system, "kde/dolphin.desktop", "[Desktop Entry]\nName=Dolphin")
        assertEquals(mapOf("kde-dolphin" to "Dolphin"), labels("kde-dolphin", "dolphin"))
    }

    @Test
    fun `the user's file overrides the system's, and a hidden one hides it`() {
        write(user, "zen.desktop", "[Desktop Entry]\nName=Zen (mine)\nStartupWMClass=zen")
        write(system, "zen.desktop", "[Desktop Entry]\nName=Zen Browser\nStartupWMClass=zen")
        write(user, "steam.desktop", "[Desktop Entry]\nName=Steam\nHidden=true")
        write(system, "steam.desktop", "[Desktop Entry]\nName=Steam")
        assertEquals(mapOf("zen" to "Zen (mine)"), labels("zen", "steam"))
    }

    @Test
    fun `unknown classes and missing directories give nothing`() {
        assertEquals(emptyMap(), DesktopEntries(listOf(File(user, "missing"))).labels(listOf("kitty")))
        assertEquals(emptyMap(), labels("nothing"))
    }

    @Test
    fun `directories follow the XDG order`() {
        val dirs =
            DesktopEntries.directories(
                mapOf("XDG_DATA_HOME" to "/data", "XDG_DATA_DIRS" to "/opt/share:relative:/usr/share"),
                home = "/home/me",
            )
        assertEquals(
            listOf("/data", "/home/me/.local/share", "/opt/share", "/usr/share").map { File(it, "applications") },
            dirs,
        )
        assertEquals(
            listOf("/home/me/.local/share", "/usr/local/share", "/usr/share").map { File(it, "applications") },
            DesktopEntries.directories(emptyMap(), home = "/home/me"),
        )
    }
}
