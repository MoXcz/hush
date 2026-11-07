// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocker

import androidx.compose.ui.graphics.ImageBitmap

/** Setup steps and data that need the platform. Implemented by the same engine as [Blocker]. */
interface BlockerSetup {
    /** Opens the system screen where the user turns the blocker on. False if it couldn't open. */
    fun openSystemSettings(): Boolean

    /**
     * Turns the blocker on or off without the system settings, for apps (e.g. banking) that refuse
     * to run while it's on. True if it's now in that state; false if this app isn't allowed to, and
     * the caller opens [openSystemSettings] instead.
     */
    suspend fun setEnabled(on: Boolean): Boolean

    /**
     * Launchable apps that can go on a block list, sorted by label. Apps the blocker never blocks
     * (this app, the launcher, phone, system settings) are left out.
     */
    suspend fun blockableApps(): List<BlockableApp>
}

data class BlockableApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap? = null,
)
