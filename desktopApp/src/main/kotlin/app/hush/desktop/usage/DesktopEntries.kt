// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

import java.io.File
import java.io.IOException

// Earlier directories win, as in the XDG lookup order.
class DesktopEntries(
    private val directories: List<File>,
) {
    private class Entry(
        val id: String,
        val name: String,
        val wmClass: String?,
    )

    // Matched by StartupWMClass, then by the desktop file id.
    fun labels(windowClasses: Collection<String>): Map<String, String> {
        if (windowClasses.isEmpty()) return emptyMap()
        val entries = read()
        return windowClasses
            .mapNotNull { windowClass ->
                val entry =
                    entries.firstOrNull { it.wmClass.equals(windowClass, ignoreCase = true) }
                        ?: entries.firstOrNull { it.id.equals(windowClass, ignoreCase = true) }
                entry?.let { windowClass to it.name }
            }.toMap()
    }

    private fun read(): List<Entry> {
        val seen = mutableSetOf<String>()
        return directories.flatMap { dir ->
            dir
                .walkTopDown()
                .maxDepth(MAX_DEPTH)
                .filter { it.isFile && it.name.endsWith(".desktop") }
                // A subdirectory is part of the id: applications/kde/foo.desktop is kde-foo.
                .map {
                    it
                        .relativeTo(dir)
                        .path
                        .removeSuffix(".desktop")
                        .replace(File.separatorChar, '-') to it
                }.filter { (id, _) -> seen.add(id) }
                .mapNotNull { (id, file) -> parse(id, file) }
                .toList()
        }
    }

    private fun parse(
        id: String,
        file: File,
    ): Entry? {
        val lines =
            try {
                file.readLines()
            } catch (_: IOException) {
                return null
            }
        var inMain = false
        var name: String? = null
        var wmClass: String? = null
        var hidden = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) {
                inMain = trimmed == "[Desktop Entry]"
                continue
            }
            if (!inMain) continue
            when {
                trimmed.startsWith("Name=") -> name = trimmed.removePrefix("Name=").trim()
                trimmed.startsWith("StartupWMClass=") -> wmClass = trimmed.removePrefix("StartupWMClass=").trim()
                trimmed == "Hidden=true" -> hidden = true
            }
        }
        if (hidden) return null
        return Entry(id, name?.takeIf { it.isNotBlank() } ?: return null, wmClass?.takeIf { it.isNotBlank() })
    }

    companion object {
        private const val MAX_DEPTH = 3

        fun directories(
            environment: Map<String, String>,
            home: String,
        ): List<File> {
            val dataHome = environment["XDG_DATA_HOME"]?.takeIf { it.startsWith("/") }
            val dataDirs =
                environment["XDG_DATA_DIRS"]
                    ?.split(':')
                    ?.filter { it.startsWith("/") }
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf("/usr/local/share", "/usr/share")
            return (listOfNotNull(dataHome, "$home/.local/share") + dataDirs)
                .distinct()
                .map { File(it, "applications") }
        }
    }
}
