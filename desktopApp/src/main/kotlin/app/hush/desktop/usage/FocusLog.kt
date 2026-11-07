// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

const val SITE_KEY_PREFIX = "web:"
const val TERMINAL_KEY_PREFIX = "term:"

// A browser site or terminal program gets its own key and label (null: label the window class from
// its .desktop file). Only that name is kept, never the window title.
data class FocusTarget(
    val key: String,
    val label: String?,
) {
    companion object {
        fun of(window: FocusedWindow): FocusTarget =
            siteFromTitle(window.windowClass, window.title)?.let { FocusTarget(SITE_KEY_PREFIX + it.lowercase(), it) }
                ?: programFromTitle(window.windowClass, window.title)?.let { FocusTarget(TERMINAL_KEY_PREFIX + it.name, it.label) }
                ?: FocusTarget(window.windowClass, null)
    }
}

// A null target is a gap: no window, or not watching.
data class FocusEntry(
    val atMillis: Long,
    val target: FocusTarget?,
)

// In memory only: nothing is kept beyond what work phases record.
class FocusLog(
    private val clock: Clock,
    private val keep: Duration = 24.hours,
) {
    private val entries = ArrayDeque<FocusEntry>()

    @Synchronized
    fun record(window: FocusedWindow?) {
        val now = clock.now().toEpochMilliseconds()
        val target = window?.let { FocusTarget.of(it) }
        if (entries.lastOrNull()?.target == target) return
        entries.addLast(FocusEntry(now, target))
        // Keep the last entry before the cutoff: it says what had focus at the cutoff.
        val cutoff = now - keep.inWholeMilliseconds
        while (entries.size > 1 && entries[1].atMillis <= cutoff) entries.removeFirst()
    }

    @Synchronized
    fun entries(): List<FocusEntry> = entries.toList()
}

object FocusTime {
    /** Visits shorter than this in total are noise (a window flashing past while switching). */
    const val MINIMUM_MILLIS = 5_000L

    // Each entry lasts until the next one (the last until now), clipped to the window. Time before the
    // first entry is unknown and counts for nothing.
    fun perTarget(
        entries: List<FocusEntry>,
        startMillis: Long,
        endMillis: Long,
        nowMillis: Long,
        excluded: Set<String> = emptySet(),
        minimumMillis: Long = MINIMUM_MILLIS,
    ): Map<FocusTarget, Long> {
        val totals = mutableMapOf<String, Long>()
        val targets = mutableMapOf<String, FocusTarget>()
        val sorted = entries.sortedBy { it.atMillis }
        sorted.forEachIndexed { index, entry ->
            val target = entry.target ?: return@forEachIndexed
            val until = sorted.getOrNull(index + 1)?.atMillis ?: nowMillis
            val from = maxOf(entry.atMillis, startMillis)
            val to = minOf(until, endMillis)
            if (to <= from) return@forEachIndexed
            totals[target.key] = (totals[target.key] ?: 0L) + (to - from)
            targets[target.key] = target
        }
        return totals
            .filter { (key, millis) -> key !in excluded && millis >= minimumMillis }
            .mapKeys { (key, _) -> targets.getValue(key) }
    }
}
