// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.usage

import kotlin.time.Duration
import kotlin.time.Instant

/** One app's time in the foreground. [label] is the app's name when the time was read. */
data class AppTime(
    val packageName: String,
    val label: String,
    val duration: Duration,
)

/**
 * Which apps were in the foreground on this device. Off until the user turns it on (usage access in
 * the system settings on Android, Hush's own switch on the desktop): nothing is read before that.
 */
interface AppUsageReader {
    /**
     * Time per app in the foreground between [start] and [end], largest first, or null when usage
     * access isn't granted. Hush itself, launchers, the system UI and very short visits are left out.
     */
    suspend fun foregroundTime(
        start: Instant,
        end: Instant,
    ): List<AppTime>?

    /** Whether the user has turned recording on. */
    fun isPermitted(): Boolean

    /** Opens the system page where usage access is granted. False if none could be opened. */
    fun openSettings(): Boolean

    /** True when Hush itself turns recording on and off ([setEnabled]) instead of a system page. */
    val hasSwitch: Boolean get() = false

    /** Turns recording on or off, where [hasSwitch]. */
    suspend fun setEnabled(on: Boolean) = Unit
}
