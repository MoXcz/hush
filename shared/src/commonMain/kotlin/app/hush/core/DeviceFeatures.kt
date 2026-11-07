// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.core

import app.hush.sync.SyncRole

/**
 * What the device running the app can do, bound by each platform app. The desktop app can't block
 * apps: screens hide what can't work there instead of showing setup steps or errors.
 */
data class DeviceFeatures(
    /** An app blocker can run here. False on desktop: blocking and detox attempts are phone-only. */
    val blocking: Boolean = true,
    /** The dim clock can be shown above a lock screen. */
    val lockScreen: Boolean = true,
    /** This device's part in LAN sync; [SyncRole.None] hides the sync UI. */
    val syncRole: SyncRole = SyncRole.None,
    /** The apps in the foreground during work phases can be recorded (with the user's usage access). */
    val appUsage: Boolean = true,
    /** Settings has a row to turn recording on: usage access on Android, Hush's own switch on the desktop. */
    val appUsageAccess: Boolean = true,
)
