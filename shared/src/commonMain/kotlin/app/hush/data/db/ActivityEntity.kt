// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Synced like [TagEntity]: a stable [uid] and the last edit's time and device. */
@Entity(tableName = "activity", indices = [Index("uid", unique = true)])
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val deleted: Boolean = false,
    val uid: String,
    val updatedAtMillis: Long,
    val updatedBy: String,
)
