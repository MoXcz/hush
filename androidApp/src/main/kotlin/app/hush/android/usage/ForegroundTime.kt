// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.usage

/** An activity coming to the foreground or leaving it, as the system's usage records report it. */
data class UsageEvent(
    val packageName: String,
    val timeMillis: Long,
    val foreground: Boolean,
    val className: String? = null,
)

/** Turns usage events into time in the foreground per app, inside a window. */
object ForegroundTime {
    /** Visits shorter than this in total are noise (an app flashing past while switching). */
    const val MINIMUM_MILLIS = 5_000L

    private const val SYSTEM_UI = "com.android.systemui"

    // With no default launcher chosen, resolveActivity returns the system chooser, owned by "android".
    private const val RESOLVER = "android"

    // Hush is in front when a phase starts; defaultHome is null or the resolver when there is none.
    fun excluded(
        ownPackage: String,
        defaultHome: String?,
    ): Set<String> = setOfNotNull(ownPackage, SYSTEM_UI, defaultHome?.takeIf { it != RESOLVER })

    // One app is in front at a time, until all its resumed activities pause or another app resumes.
    // Events may begin before the window, to know what was already in front at its start.
    fun perApp(
        events: List<UsageEvent>,
        startMillis: Long,
        endMillis: Long,
        excluded: Set<String> = emptySet(),
        minimumMillis: Long = MINIMUM_MILLIS,
    ): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        var current: String? = null
        var since = 0L
        var lastResumed = 0L
        // Activities of the current app still resumed: a second one can resume before the first pauses.
        val resumed = mutableSetOf<String>()

        fun close(at: Long) {
            val pkg = current ?: return
            val from = maxOf(since, startMillis)
            val to = minOf(at, endMillis)
            if (to > from) totals[pkg] = (totals[pkg] ?: 0L) + (to - from)
            current = null
            resumed.clear()
        }

        for (event in events.sortedBy { it.timeMillis }) {
            if (event.timeMillis > endMillis) break
            if (event.foreground) {
                if (event.packageName != current) {
                    close(event.timeMillis)
                    current = event.packageName
                    since = event.timeMillis
                }
                lastResumed = event.timeMillis
                event.className?.let { resumed += it }
            } else if (event.packageName == current) {
                val className = event.className
                // Without class names, a pause no later than the app's latest resume belongs to an older activity.
                val stillInFront =
                    if (className != null && resumed.isNotEmpty()) {
                        resumed.remove(className)
                        resumed.isNotEmpty()
                    } else {
                        event.timeMillis <= lastResumed
                    }
                if (!stillInFront) close(event.timeMillis)
            }
        }
        close(endMillis)
        return totals.filter { (pkg, millis) -> pkg !in excluded && millis >= minimumMillis }
    }
}
