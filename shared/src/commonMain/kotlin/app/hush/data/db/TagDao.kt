// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tag ORDER BY id")
    fun all(): Flow<List<TagEntity>>

    @Insert
    suspend fun insert(tag: TagEntity): Long

    // Column by column, so the uid is never touched by an edit.
    @Query(
        "UPDATE tag SET name = :name, color = :color, workMinutes = :workMinutes, shortBreakMinutes = :shortBreakMinutes, " +
            "longBreakMinutes = :longBreakMinutes, longBreakAfter = :longBreakAfter, deleted = :deleted, " +
            "updatedAtMillis = :updatedAtMillis, updatedBy = :updatedBy WHERE id = :id",
    )
    suspend fun updateFields(
        id: Long,
        name: String,
        color: String,
        workMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        longBreakAfter: Int,
        deleted: Boolean,
        updatedAtMillis: Long,
        updatedBy: String,
    )

    @Query("SELECT COUNT(*) FROM tag WHERE deleted = 0")
    suspend fun activeCount(): Int

    @Query("UPDATE tag SET deleted = 1, updatedAtMillis = :updatedAtMillis, updatedBy = :updatedBy WHERE id = :id")
    suspend fun softDelete(
        id: Long,
        updatedAtMillis: Long,
        updatedBy: String,
    )

    @Transaction
    suspend fun softDeleteUnlessLast(
        id: Long,
        updatedAtMillis: Long,
        updatedBy: String,
    ): Boolean {
        if (activeCount() <= 1) return false
        softDelete(id, updatedAtMillis, updatedBy)
        return true
    }
}
