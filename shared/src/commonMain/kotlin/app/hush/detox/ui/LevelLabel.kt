// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.detox.ui

import androidx.compose.runtime.Composable
import app.hush.resources.Res
import app.hush.resources.level_hours
import app.hush.resources.level_hours_minutes
import app.hush.resources.level_minutes
import org.jetbrains.compose.resources.stringResource

/** "1 h", "1 h 30 min" or "45 min". */
@Composable
fun levelLabel(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> stringResource(Res.string.level_minutes, rest)
        rest == 0 -> stringResource(Res.string.level_hours, hours)
        else -> stringResource(Res.string.level_hours_minutes, hours, rest)
    }
}
