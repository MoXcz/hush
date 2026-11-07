// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

import java.nio.file.Path

/** The focused window as Hyprland reports it; null where there is none (empty workspace). */
data class FocusedWindow(
    val windowClass: String,
    val title: String,
)

/** A change of focus read from Hyprland's event socket; [window] null means nothing has focus. */
data class FocusChange(
    val window: FocusedWindow?,
)

/** Hyprland's socket directory, or null when this isn't a Hyprland session. */
fun hyprlandSocketDirectory(environment: Map<String, String>): Path? {
    val signature = environment["HYPRLAND_INSTANCE_SIGNATURE"]?.takeIf { it.isNotBlank() } ?: return null
    val runtime = environment["XDG_RUNTIME_DIR"]?.takeIf { it.isNotBlank() } ?: return null
    return Path.of(runtime, "hypr", signature)
}

// `activewindow>>CLASS,TITLE`: the title is the last field and may contain commas. An empty class
// means nothing has focus. Other events are ignored.
fun parseEvent(line: String): FocusChange? {
    val data = line.removePrefix("activewindow>>").takeIf { it.length != line.length } ?: return null
    val comma = data.indexOf(',')
    if (comma < 0) return null
    val windowClass = data.substring(0, comma)
    return FocusChange(if (windowClass.isEmpty()) null else FocusedWindow(windowClass, data.substring(comma + 1)))
}

// The plain-text reply to `activewindow` on the request socket: `Window <addr> -> <title>:` then
// tab-indented `key: value` lines, or "Invalid" when nothing has focus.
fun parseActiveWindow(reply: String): FocusedWindow? {
    val fields =
        reply
            .lineSequence()
            .filter { it.startsWith("\t") }
            .mapNotNull { line ->
                val colon = line.indexOf(": ")
                if (colon < 0) null else line.substring(1, colon) to line.substring(colon + 2)
            }.toMap()
    val windowClass = fields["class"]?.takeIf { it.isNotEmpty() } ?: return null
    return FocusedWindow(windowClass, fields["title"].orEmpty())
}
