// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityRuleDao {
    @Query(
        "SELECT r.appKey, r.label, a.id AS activityId FROM activity_rule r " +
            "LEFT JOIN activity a ON a.uid = r.activityUid WHERE r.deleted = 0 ORDER BY r.appKey",
    )
    fun rules(): Flow<List<ActivityRuleRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ActivityRuleEntity)

    @Query(
        "UPDATE activity_rule SET deleted = 1, updatedAtMillis = :updatedAtMillis, updatedBy = :updatedBy " +
            "WHERE appKey = :appKey AND deleted = 0",
    )
    suspend fun softDelete(
        appKey: String,
        updatedAtMillis: Long,
        updatedBy: String,
    )

    @Query("SELECT uid FROM activity WHERE id = :id")
    suspend fun activityUid(id: Long): String?
}
