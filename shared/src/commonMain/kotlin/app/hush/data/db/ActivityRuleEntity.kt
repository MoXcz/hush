// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

// appKey: as stored in session_app; synced like ActivityEntity (last writer wins, tombstones).
@Entity(tableName = "activity_rule")
data class ActivityRuleEntity(
    @PrimaryKey val appKey: String,
    val label: String,
    val activityUid: String,
    val deleted: Boolean,
    val updatedAtMillis: Long,
    val updatedBy: String,
)

/** A rule in force with its local activity id (null when the activity isn't in this database). */
data class ActivityRuleRow(
    val appKey: String,
    val label: String,
    val activityId: Long?,
)
