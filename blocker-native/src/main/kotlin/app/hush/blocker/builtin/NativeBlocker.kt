// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocker.builtin

import app.hush.blocker.Availability
import app.hush.blocker.BlockProfile
import app.hush.blocker.BlockResult
import app.hush.blocker.Blocker
import app.hush.blocker.ProfileId
import app.hush.blocker.SessionStatus
import app.hush.blocker.UnknownReason
import app.hush.blocklists.BlockListRepository
import kotlinx.coroutines.flow.first
import java.io.IOException
import kotlin.math.ceil
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** A session is only a stored end time and list id, which [AppBlockerService] enforces. */
class NativeBlocker(
    private val store: BlockSessionStore,
    private val blockLists: BlockListRepository,
    private val clock: Clock,
    private val serviceEnabled: () -> Boolean,
) : Blocker {
    override suspend fun availability(): Availability = if (serviceEnabled()) Availability.Ready else Availability.NeedsApproval

    override suspend fun profiles(): List<BlockProfile> = blockLists.lists.first().map { BlockProfile(it.id, it.name, canStopEarly = true) }

    override suspend fun start(
        profile: ProfileId,
        minutes: Int,
    ): BlockResult {
        if (!serviceEnabled()) return BlockResult.Denied
        val list = blockLists.lists.first().firstOrNull { it.id == profile } ?: return BlockResult.Refused
        return write { store.save(BlockSession(profile, list.name, clock.now() + minutes.minutes)) }
    }

    override suspend fun stop(): BlockResult = write { store.clear() }

    override suspend fun activeSession(): SessionStatus {
        val session =
            try {
                store.current()
            } catch (_: IOException) {
                return SessionStatus.Unknown(UnknownReason.Unreadable)
            }
        val remaining = session?.endsAt?.minus(clock.now())
        if (session == null || remaining == null || !remaining.isPositive()) return SessionStatus.None
        // With the service off nothing is blocked, so the session counts as ended (a detox level fails).
        if (!serviceEnabled()) return SessionStatus.None
        return SessionStatus.Active(session.profileName, ceil(remaining / 1.minutes).toInt())
    }

    // A failed write may or may not have landed: the caller checks activeSession(), as the contract says.
    private suspend fun write(block: suspend () -> Unit): BlockResult =
        try {
            block()
            BlockResult.Ok
        } catch (_: IOException) {
            BlockResult.ConnectionLost
        }
}
