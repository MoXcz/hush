// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop

import app.hush.blocker.Availability
import app.hush.blocker.BlockProfile
import app.hush.blocker.BlockResult
import app.hush.blocker.BlockableApp
import app.hush.blocker.Blocker
import app.hush.blocker.BlockerSetup
import app.hush.blocker.ProfileId
import app.hush.blocker.SessionStatus

/**
 * Desktop has no app blocker. Nothing can start, so work phases run unblocked and detox attempts
 * can't start; `DeviceFeatures(blocking = false)` makes the screens say so quietly.
 */
class NoBlocker :
    Blocker,
    BlockerSetup {
    override suspend fun availability(): Availability = Availability.Unsupported

    override suspend fun profiles(): List<BlockProfile> = emptyList()

    override suspend fun start(
        profile: ProfileId,
        minutes: Int,
    ): BlockResult = BlockResult.Denied

    override suspend fun stop(): BlockResult = BlockResult.Ok

    override suspend fun activeSession(): SessionStatus = SessionStatus.None

    override fun openSystemSettings(): Boolean = false

    override suspend fun setEnabled(on: Boolean): Boolean = false

    override suspend fun blockableApps(): List<BlockableApp> = emptyList()
}
