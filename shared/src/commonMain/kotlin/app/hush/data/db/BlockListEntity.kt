// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** A block list. Ids are fixed strings ("deep", "detox"), seeded with the database. */
@Entity(tableName = "block_list")
data class BlockListEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(tableName = "block_list_app", primaryKeys = ["listId", "packageName"])
data class BlockListAppEntity(
    val listId: String,
    val packageName: String,
)
