// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.history.ui

import androidx.compose.runtime.Composable
import kotlin.time.Instant

/** Short date and time in the user's locale and 12/24-hour preference. */
@Composable
expect fun formatDateTime(instant: Instant): String
