// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Enums are stored by name and instants as epoch millis (AGENTS.md §6). */
@Entity(tableName = "session", indices = [Index("type", "endedAtMillis"), Index("uid", unique = true)])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val profileId: String?,
    val plannedMinutes: Int,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val outcome: String? = null,
    /** The tag of a work phase; null for detox attempts. Tags are soft-deleted, so it stays valid. */
    val tagId: Long? = null,
    /** Stable across devices (a random UUID); the local [id] is only used inside this database. */
    val uid: String,
    /** What the work phase was spent doing (an activity's local id), if the user picked one. */
    val activityId: Long? = null,
)
