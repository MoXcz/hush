// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockListDao {
    @Query("SELECT * FROM block_list")
    fun lists(): Flow<List<BlockListEntity>>

    @Query("SELECT * FROM block_list_app ORDER BY packageName")
    fun apps(): Flow<List<BlockListAppEntity>>

    @Query("DELETE FROM block_list_app WHERE listId = :listId")
    suspend fun clearApps(listId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertApps(apps: List<BlockListAppEntity>)

    @Transaction
    suspend fun replaceApps(
        listId: String,
        apps: List<BlockListAppEntity>,
    ) {
        clearApps(listId)
        insertApps(apps)
    }
}
