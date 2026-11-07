// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer.ui.dimclock

import androidx.compose.runtime.Composable
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Instant

// Desktop: the black, full-window screen is the whole effect; no brightness or lock-screen control.
@Composable
actual fun DimClockWindowEffect(showWhenLocked: Boolean) = Unit

@Composable
actual fun formatTimeOfDay(instant: Instant): String =
    DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds()))
