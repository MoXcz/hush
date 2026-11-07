// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.history.ui

import androidx.compose.runtime.Composable
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Instant

@Composable
actual fun formatDateTime(instant: Instant): String =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds()))
