// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocker

import kotlin.jvm.JvmInline

/**
 * Blocks distracting apps on behalf of the app. Domain code depends only on this interface, so the
 * engine behind it can be swapped without touching timer, detox or UI code.
 *
 * Implementations are main-safe: they move blocking work off the caller's thread themselves.
 */
interface Blocker {
    /** Whether the blocker can be used right now, or what setup step is missing. */
    suspend fun availability(): Availability

    /** Blocking profiles (block lists) that can be started. Empty if none or not available. */
    suspend fun profiles(): List<BlockProfile>

    /** Starts blocking with [profile] for [minutes]. The session expires on its own. */
    suspend fun start(
        profile: ProfileId,
        minutes: Int,
    ): BlockResult

    /** Ends the running session early. */
    suspend fun stop(): BlockResult

    /**
     * Whether a session is running. [SessionStatus.Unknown] means it couldn't be read, which is not
     * the same as [SessionStatus.None]: never start a new session on Unknown.
     */
    suspend fun activeSession(): SessionStatus
}

sealed interface Availability {
    /** The blocker is turned off: the user must turn it on in the system settings. */
    data object NeedsApproval : Availability

    /** This device can't block apps (desktop). */
    data object Unsupported : Availability

    data object Ready : Availability
}

sealed interface BlockResult {
    data object Ok : BlockResult

    /** The blocker is turned off, or setup is incomplete. availability() says which step. */
    data object Denied : BlockResult

    /** The blocker said no, e.g. the profile can't be stopped early. */
    data object Refused : BlockResult

    /** Call may or may not have run. Check activeSession() before trying again. */
    data object ConnectionLost : BlockResult
}

@JvmInline
value class ProfileId(
    val value: String,
)

data class BlockProfile(
    val id: ProfileId,
    val name: String,
    val canStopEarly: Boolean,
)

sealed interface SessionStatus {
    data class Active(
        val profileName: String,
        val remainingMinutes: Int,
    ) : SessionStatus

    data object None : SessionStatus

    data class Unknown(
        val reason: UnknownReason,
    ) : SessionStatus
}

enum class UnknownReason {
    /** Not set up or not supported. availability() says which. */
    Setup,

    /** The blocker couldn't be reached. */
    Unreachable,

    /** The blocker's stored state couldn't be read. */
    Unreadable,
}
