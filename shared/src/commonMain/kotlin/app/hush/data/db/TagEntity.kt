// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "tag", indices = [Index("uid", unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String,
    val workMinutes: Int,
    val shortBreakMinutes: Int,
    val longBreakMinutes: Int,
    val longBreakAfter: Int,
    val deleted: Boolean = false,
    /** Stable across devices; the seeded tags have fixed ones ([SEED_TAG_UIDS]). */
    val uid: String,
    /** Last edit (epoch millis) and the device that made it: the last writer wins in a sync. */
    val updatedAtMillis: Long,
    /** Device id of the last writer, empty for edits made before sync existed. */
    val updatedBy: String,
)
