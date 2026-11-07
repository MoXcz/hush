// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

import kotlinx.serialization.Serializable

/** Current payload version. A newer one is refused ([DecodeResult.TooNew]), never half-read. */
const val SYNC_PAYLOAD_VERSION = 1

/** Older Hush ignores `activities`, `apps` and `activityRules`; this one ignores the old tag `rules`. */
@Serializable
data class SyncPayload(
    val v: Int = SYNC_PAYLOAD_VERSION,
    val device: String,
    val tags: List<TagRecord>,
    val sessions: List<SessionRecord>,
    val activities: List<ActivityRecord> = emptyList(),
    val apps: List<SessionAppRecord> = emptyList(),
    val activityRules: List<ActivityRuleRecord> = emptyList(),
)

/** A tag as it travels: identified by [uid], last edit stamped with [updatedAt] (epoch ms) and [updatedBy]. */
@Serializable
data class TagRecord(
    val uid: String,
    val name: String,
    val color: String,
    val work: Int,
    val shortBreak: Int,
    val longBreak: Int,
    val longBreakAfter: Int,
    val deleted: Boolean,
    val updatedAt: Long,
    val updatedBy: String,
)

/** A finished session as it travels. Instants are epoch ms; [tag] is the tag's uid, not a local id. */
@Serializable
data class SessionRecord(
    val uid: String,
    val type: String,
    val profile: String?,
    val plannedMinutes: Int,
    val startedAt: Long,
    // Nullable so a record without an end is dropped alone instead of failing the whole payload.
    val endedAt: Long? = null,
    val outcome: String,
    val tag: String? = null,
    /** The activity's uid, not a local id. */
    val activity: String? = null,
)

/** An activity as it travels; merged like a tag (last writer wins, deletes are tombstones). */
@Serializable
data class ActivityRecord(
    val uid: String,
    val name: String,
    val deleted: Boolean,
    val updatedAt: Long,
    val updatedBy: String,
)

/** An app's foreground time during the work phase [session] (its uid), in milliseconds. */
@Serializable
data class SessionAppRecord(
    val session: String,
    val packageName: String,
    val label: String,
    val millis: Long,
)

/** A rule as it travels: the app stored under [app] sorted into the activity with uid [activity]; merged like an activity. */
@Serializable
data class ActivityRuleRecord(
    val app: String,
    val label: String,
    val activity: String,
    val deleted: Boolean,
    val updatedAt: Long,
    val updatedBy: String,
)
