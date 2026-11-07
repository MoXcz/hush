// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity ORDER BY id")
    fun all(): Flow<List<ActivityEntity>>

    @Insert
    suspend fun insert(activity: ActivityEntity): Long

    @Query("UPDATE activity SET name = :name, updatedAtMillis = :updatedAtMillis, updatedBy = :updatedBy WHERE id = :id")
    suspend fun rename(
        id: Long,
        name: String,
        updatedAtMillis: Long,
        updatedBy: String,
    )

    @Query("UPDATE activity SET deleted = 1, updatedAtMillis = :updatedAtMillis, updatedBy = :updatedBy WHERE id = :id")
    suspend fun softDelete(
        id: Long,
        updatedAtMillis: Long,
        updatedBy: String,
    )
}
