// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop

import app.hush.usage.AppTime
import app.hush.usage.AppUsageReader
import kotlin.time.Instant

/** Outside Hyprland the desktop records no apps (`DeviceFeatures.appUsage` is false); Stats still shows synced ones. */
class NoAppUsage : AppUsageReader {
    override suspend fun foregroundTime(
        start: Instant,
        end: Instant,
    ): List<AppTime>? = null

    override fun isPermitted(): Boolean = false

    override fun openSettings(): Boolean = false
}
