// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.history

import app.hush.activities.ActivityId
import app.hush.blocker.ProfileId
import app.hush.tags.TagId
import kotlin.jvm.JvmInline
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@JvmInline
value class SessionId(
    val value: Long,
)

enum class SessionType { Work, Detox }

enum class SessionOutcome {
    /** Ran to its end: a finished work phase or a passed level. */
    Completed,

    /** Ended early by the user in this app (skip, reset, or the panic flow). */
    Stopped,

    /** The blocker reported the session gone before its end (stopped inside the blocker). */
    Abandoned,
}

/** A work phase or a detox attempt. [endedAt] and [outcome] are null while a detox attempt is open. */
data class Session(
    val id: SessionId,
    val type: SessionType,
    val profile: ProfileId?,
    val plannedMinutes: Int,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val outcome: SessionOutcome? = null,
    /** The tag of a work phase; null for detox attempts. */
    val tagId: TagId? = null,
    /** What a work phase was spent doing, if the user picked an activity. */
    val activityId: ActivityId? = null,
    /** Stable across devices; app times and sync refer to the session by it. */
    val uid: String = "",
) {
    val plannedEnd: Instant get() = startedAt + plannedMinutes.minutes
}
