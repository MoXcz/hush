// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("UPDATE session SET endedAtMillis = :endedAtMillis, outcome = :outcome WHERE id = :id AND endedAtMillis IS NULL")
    suspend fun finish(
        id: Long,
        endedAtMillis: Long,
        outcome: String,
    )

    @Query("SELECT * FROM session WHERE type = 'Detox' AND endedAtMillis IS NULL ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun openDetoxAttempt(): SessionEntity?

    @Query("SELECT * FROM session WHERE endedAtMillis IS NOT NULL ORDER BY startedAtMillis DESC")
    fun finished(): Flow<List<SessionEntity>>

    @Query("SELECT MAX(plannedMinutes) FROM session WHERE type = 'Detox' AND outcome = 'Completed'")
    fun longestPassedDetoxMinutes(): Flow<Int?>
}
