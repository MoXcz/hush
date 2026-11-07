// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer.ui

import kotlin.time.Duration

/** "25:00", or "1:05:00" from an hour up. Seconds round up, so the last second shows "0:01". */
internal fun formatCountdown(remaining: Duration): String {
    val totalSeconds = (remaining.inWholeMilliseconds.coerceAtLeast(0) + 999) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mmss = "${minutes.twoDigits()}:${seconds.twoDigits()}"
    return if (hours > 0) "$hours:$mmss" else mmss
}

private fun Long.twoDigits(): String = toString().padStart(2, '0')
