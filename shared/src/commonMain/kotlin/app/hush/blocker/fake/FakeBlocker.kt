// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocker.fake

import app.hush.blocker.Availability
import app.hush.blocker.BlockProfile
import app.hush.blocker.BlockResult
import app.hush.blocker.BlockableApp
import app.hush.blocker.Blocker
import app.hush.blocker.BlockerSetup
import app.hush.blocker.ProfileId
import app.hush.blocker.SessionStatus

/** In-memory [Blocker] and [BlockerSetup] for tests, previews and running without a real blocker. */
class FakeBlocker(
    var availability: Availability = Availability.Ready,
    var profiles: List<BlockProfile> = listOf(BlockProfile(ProfileId("deep"), "Deep work", true)),
) : Blocker,
    BlockerSetup {
    val calls = mutableListOf<String>()
    var nextResult: BlockResult = BlockResult.Ok

    /** Settable, e.g. to simulate a session ended inside the blocker or an unreadable state. */
    var session: SessionStatus = SessionStatus.None

    var apps: List<BlockableApp> = emptyList()

    /** Whether the system settings screen opens. Opening it doesn't turn the blocker on. */
    var settingsOpen: Boolean = true

    override suspend fun availability(): Availability {
        calls += "availability"
        return availability
    }

    override suspend fun profiles(): List<BlockProfile> {
        calls += "profiles"
        return profiles
    }

    override suspend fun start(
        profile: ProfileId,
        minutes: Int,
    ): BlockResult =
        nextResult.also {
            calls += "start:${profile.value}:$minutes"
            if (it == BlockResult.Ok) {
                session = SessionStatus.Active(profiles.first { p -> p.id == profile }.name, minutes)
            }
        }

    override suspend fun stop(): BlockResult =
        nextResult.also {
            calls += "stop"
            if (it == BlockResult.Ok) session = SessionStatus.None
        }

    override suspend fun activeSession(): SessionStatus {
        calls += "activeSession"
        return session
    }

    override fun openSystemSettings(): Boolean {
        calls += "openSystemSettings"
        return settingsOpen
    }

    /** Whether [setEnabled] may switch the blocker; when it does, [availability] follows. */
    var canSetEnabled: Boolean = true

    override suspend fun setEnabled(on: Boolean): Boolean {
        calls += "setEnabled:$on"
        if (canSetEnabled) availability = if (on) Availability.Ready else Availability.NeedsApproval
        return canSetEnabled
    }

    override suspend fun blockableApps(): List<BlockableApp> {
        calls += "blockableApps"
        return apps
    }
}
