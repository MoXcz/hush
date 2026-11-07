// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionAppDao {
    // A row already stored for the same session and app is kept, so recording again is harmless.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(apps: List<SessionAppEntity>)

    @Query(
        "SELECT a.sessionUid, a.packageName, a.label, a.foregroundMillis, s.startedAtMillis AS sessionStartedAtMillis " +
            "FROM session_app a JOIN session s ON s.uid = a.sessionUid",
    )
    fun usage(): Flow<List<SessionAppRow>>
}
