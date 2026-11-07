// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TimerFormatTest {
    @Test
    fun `countdown shows minutes and seconds, with hours only from an hour up`() {
        assertEquals("25:00", formatCountdown(25.minutes))
        assertEquals("04:05", formatCountdown(4.minutes + 5.seconds))
        assertEquals("1:05:00", formatCountdown(1.hours + 5.minutes))
    }

    @Test
    fun `partial seconds round up, so zero only shows at the end`() {
        assertEquals("00:01", formatCountdown(1.milliseconds))
        assertEquals("25:00", formatCountdown(24.minutes + 59.seconds + 1.milliseconds))
        assertEquals("00:00", formatCountdown(Duration.ZERO))
        assertEquals("00:00", formatCountdown((-3).seconds))
    }
}
