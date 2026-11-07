// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Nishant Mishra (Tomato: Migrations.kt, SeedDefaultTopicCallback.kt), adapted.
package app.hush.data.db

import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Row id of the seeded "Focus" tag; work sessions recorded before tags existed get it. */
const val FOCUS_TAG_ID = 1L

/** Fixed uids of the seeded tags (rows 1-3 on every install), so a sync sees them as the same tag. */
val SEED_TAG_UIDS =
    listOf(
        "00000000-0000-4000-8000-000000000001",
        "00000000-0000-4000-8000-000000000002",
        "00000000-0000-4000-8000-000000000003",
    )

// Names are seeded in English and are the user's to rename. Columns: id, name, colour, work, short
// break, long break, long break after. Frozen: MIGRATION_1_2 must keep producing version 2's rows.
private const val SEED_TAGS_V2_SQL =
    """
    INSERT OR IGNORE INTO `tag`
        (`id`, `name`, `color`, `workMinutes`, `shortBreakMinutes`, `longBreakMinutes`, `longBreakAfter`, `deleted`)
    VALUES
        ($FOCUS_TAG_ID, 'Focus', 'Indigo', 25, 5, 15, 4, 0),
        (2, 'Light', 'Teal', 20, 10, 20, 4, 0),
        (3, 'Deep', 'Plum', 50, 10, 30, 4, 0)
    """

// The current seed: the same rows with their fixed uids, never edited (updatedAt 0, no device).
private val SEED_TAGS_SQL =
    """
    INSERT OR IGNORE INTO `tag`
        (`id`, `name`, `color`, `workMinutes`, `shortBreakMinutes`, `longBreakMinutes`, `longBreakAfter`, `deleted`,
         `uid`, `updatedAtMillis`, `updatedBy`)
    VALUES
        ($FOCUS_TAG_ID, 'Focus', 'Indigo', 25, 5, 15, 4, 0, '${SEED_TAG_UIDS[0]}', 0, ''),
        (2, 'Light', 'Teal', 20, 10, 20, 4, 0, '${SEED_TAG_UIDS[1]}', 0, ''),
        (3, 'Deep', 'Plum', 50, 10, 30, 4, 0, '${SEED_TAG_UIDS[2]}', 0, '')
    """

// The two block lists, one per ProfileRole, with the same ids. They start empty.
private const val SEED_BLOCK_LISTS_SQL =
    """
    INSERT OR IGNORE INTO `block_list` (`id`, `name`) VALUES ('deep', 'Deep work'), ('detox', 'Detox')
    """

/** Seeds the default tags and the block lists into a newly created database. */
object SeedDefaultsCallback : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
        super.onCreate(connection)
        connection.execSQL(SEED_TAGS_SQL.trimIndent())
        connection.execSQL(SEED_BLOCK_LISTS_SQL.trimIndent())
    }
}

/** Version 2 adds tags: the tag table (seeded), and the tag of each work session. */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tag` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `color` TEXT NOT NULL,
                    `workMinutes` INTEGER NOT NULL,
                    `shortBreakMinutes` INTEGER NOT NULL,
                    `longBreakMinutes` INTEGER NOT NULL,
                    `longBreakAfter` INTEGER NOT NULL,
                    `deleted` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            connection.execSQL(SEED_TAGS_V2_SQL.trimIndent())
            connection.execSQL("ALTER TABLE `session` ADD COLUMN `tagId` INTEGER")
            connection.execSQL("UPDATE `session` SET `tagId` = $FOCUS_TAG_ID WHERE `type` = 'Work'")
        }
    }

/** Version 3 adds the built-in blocker's block lists (seeded, empty) and their apps. */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `block_list` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `block_list_app` (`listId` TEXT NOT NULL, `packageName` TEXT NOT NULL, " +
                    "PRIMARY KEY(`listId`, `packageName`))",
            )
            connection.execSQL(SEED_BLOCK_LISTS_SQL.trimIndent())
        }
    }

private const val RANDOM_UUID_SQL =
    "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))), 2) || '-' || " +
        "substr('89ab', 1 + (abs(random()) % 4), 1) || substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))"

// Seeded rows 1-3 still exactly as seeded count as never edited (updatedAt 0), so the same untouched
// default on two devices never "wins" over the other; anything else was edited before now.
private const val SEED_UNCHANGED_SQL =
    "((`id` = 1 AND `name` = 'Focus' AND `color` = 'Indigo' AND `workMinutes` = 25 AND `shortBreakMinutes` = 5 " +
        "AND `longBreakMinutes` = 15 AND `longBreakAfter` = 4 AND `deleted` = 0) OR " +
        "(`id` = 2 AND `name` = 'Light' AND `color` = 'Teal' AND `workMinutes` = 20 AND `shortBreakMinutes` = 10 " +
        "AND `longBreakMinutes` = 20 AND `longBreakAfter` = 4 AND `deleted` = 0) OR " +
        "(`id` = 3 AND `name` = 'Deep' AND `color` = 'Plum' AND `workMinutes` = 50 AND `shortBreakMinutes` = 10 " +
        "AND `longBreakMinutes` = 30 AND `longBreakAfter` = 4 AND `deleted` = 0))"

// Tables are rebuilt so the new sync columns can be NOT NULL without a default.
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE `session_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                    "`profileId` TEXT, `plannedMinutes` INTEGER NOT NULL, `startedAtMillis` INTEGER NOT NULL, " +
                    "`endedAtMillis` INTEGER, `outcome` TEXT, `tagId` INTEGER, `uid` TEXT NOT NULL)",
            )
            connection.execSQL(
                "INSERT INTO `session_new` (`id`, `type`, `profileId`, `plannedMinutes`, `startedAtMillis`, " +
                    "`endedAtMillis`, `outcome`, `tagId`, `uid`) " +
                    "SELECT `id`, `type`, `profileId`, `plannedMinutes`, `startedAtMillis`, `endedAtMillis`, " +
                    "`outcome`, `tagId`, $RANDOM_UUID_SQL FROM `session`",
            )
            connection.execSQL("DROP TABLE `session`")
            connection.execSQL("ALTER TABLE `session_new` RENAME TO `session`")
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_session_type_endedAtMillis` ON `session` (`type`, `endedAtMillis`)",
            )
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_session_uid` ON `session` (`uid`)")

            connection.execSQL(
                "CREATE TABLE `tag_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`color` TEXT NOT NULL, `workMinutes` INTEGER NOT NULL, `shortBreakMinutes` INTEGER NOT NULL, " +
                    "`longBreakMinutes` INTEGER NOT NULL, `longBreakAfter` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, " +
                    "`uid` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, `updatedBy` TEXT NOT NULL)",
            )
            connection.execSQL(
                "INSERT INTO `tag_new` (`id`, `name`, `color`, `workMinutes`, `shortBreakMinutes`, `longBreakMinutes`, " +
                    "`longBreakAfter`, `deleted`, `uid`, `updatedAtMillis`, `updatedBy`) " +
                    "SELECT `id`, `name`, `color`, `workMinutes`, `shortBreakMinutes`, `longBreakMinutes`, " +
                    "`longBreakAfter`, `deleted`, " +
                    "CASE `id` WHEN 1 THEN '${SEED_TAG_UIDS[0]}' WHEN 2 THEN '${SEED_TAG_UIDS[1]}' " +
                    "WHEN 3 THEN '${SEED_TAG_UIDS[2]}' ELSE $RANDOM_UUID_SQL END, " +
                    "CASE WHEN $SEED_UNCHANGED_SQL THEN 0 ELSE CAST(strftime('%s', 'now') AS INTEGER) * 1000 END, " +
                    "'' FROM `tag`",
            )
            connection.execSQL("DROP TABLE `tag`")
            connection.execSQL("ALTER TABLE `tag_new` RENAME TO `tag`")
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_uid` ON `tag` (`uid`)")
        }
    }

/** Version 5 adds the apps in the foreground during work phases, and activities (what a phase was spent doing). */
val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `activity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`deleted` INTEGER NOT NULL, `uid` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, `updatedBy` TEXT NOT NULL)",
            )
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_activity_uid` ON `activity` (`uid`)")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `session_app` (`sessionUid` TEXT NOT NULL, `packageName` TEXT NOT NULL, " +
                    "`label` TEXT NOT NULL, `foregroundMillis` INTEGER NOT NULL, PRIMARY KEY(`sessionUid`, `packageName`))",
            )
            connection.execSQL("ALTER TABLE `session` ADD COLUMN `activityId` INTEGER")
        }
    }

val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `tag_rule` (`target` TEXT NOT NULL, `label` TEXT NOT NULL, `tagUid` TEXT NOT NULL, " +
                    "`deleted` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, `updatedBy` TEXT NOT NULL, PRIMARY KEY(`target`))",
            )
        }
    }

// Rules sorted apps into tags (timer presets); from 1.0.0 they sort into activities. Old rules
// can't be mapped, so they go.
val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP TABLE IF EXISTS `tag_rule`")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `activity_rule` (`appKey` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                    "`activityUid` TEXT NOT NULL, `deleted` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, " +
                    "`updatedBy` TEXT NOT NULL, PRIMARY KEY(`appKey`))",
            )
        }
    }
