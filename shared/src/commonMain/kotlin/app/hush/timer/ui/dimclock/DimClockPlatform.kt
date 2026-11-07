// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer.ui.dimclock

import androidx.compose.runtime.Composable
import kotlin.time.Instant

/**
 * While in composition: keep the screen on, dim the window, hide the system bars and, if
 * [showWhenLocked], show above the lock screen. Everything is restored when it leaves.
 */
@Composable
expect fun DimClockWindowEffect(showWhenLocked: Boolean)

/** The time of day in the user's locale and 12/24-hour preference. */
@Composable
expect fun formatTimeOfDay(instant: Instant): String
