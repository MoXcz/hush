// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.history.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Date
import kotlin.time.Instant

@Composable
actual fun formatDateTime(instant: Instant): String {
    val context = LocalContext.current
    val date = Date(instant.toEpochMilliseconds())
    return "${DateFormat.getMediumDateFormat(context).format(date)}, ${DateFormat.getTimeFormat(context).format(date)}"
}
