// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.hush.core.AppDispatchers

const val DATABASE_FILE_NAME = "hush.db"

@Database(
    entities = [
        SessionEntity::class,
        TagEntity::class,
        BlockListEntity::class,
        BlockListAppEntity::class,
        ActivityEntity::class,
        SessionAppEntity::class,
        ActivityRuleEntity::class,
    ],
    version = 7,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun tagDao(): TagDao

    abstract fun blockListDao(): BlockListDao

    abstract fun syncDao(): SyncDao

    abstract fun activityDao(): ActivityDao

    abstract fun sessionAppDao(): SessionAppDao

    abstract fun activityRuleDao(): ActivityRuleDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/**
 * Same driver on every target; only the file location ([builder]) is platform code. Every schema
 * change gets a migration (never destructive), and new databases are seeded with the default tags
 * and the two block lists.
 */
fun buildAppDatabase(
    builder: RoomDatabase.Builder<AppDatabase>,
    dispatchers: AppDispatchers,
): AppDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(dispatchers.io)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
        .addCallback(SeedDefaultsCallback)
        .build()
