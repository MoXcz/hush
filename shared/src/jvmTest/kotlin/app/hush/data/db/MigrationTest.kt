// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Nishant Mishra (Tomato: jvmTest/.../data/MigrationTest.kt), adapted.
package app.hush.data.db

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.hush.sync.UUID_PATTERN
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationTest {
    private val directory: Path = Files.createTempDirectory("migration")

    private val helper =
        MigrationTestHelper(
            schemaDirectoryPath = Path.of(System.getProperty("room.schemaDir")),
            databasePath = directory.resolve("migration-test.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun deleteDatabase() {
        directory.deleteRecursively()
    }

    @Test
    fun `migrating 1 to 2 matches the exported schema`(): Unit =
        runBlocking {
            helper.createDatabase(1).close()
            helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2)).close()
        }

    @Test
    fun `migrating 1 to 2 keeps every session and gives work sessions the Focus tag`(): Unit =
        runBlocking {
            helper.createDatabase(1).use { db ->
                db.execSQL(
                    """
                    INSERT INTO session (id, type, profileId, plannedMinutes, startedAtMillis, endedAtMillis, outcome) VALUES
                        (1, 'Work', 'deep', 25, 1000, 2000, 'Completed'),
                        (2, 'Detox', 'detox', 60, 3000, 4000, 'Stopped'),
                        (3, 'Detox', 'detox', 90, 5000, NULL, NULL)
                    """.trimIndent(),
                )
            }
            helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2)).use { db ->
                assertEquals(
                    listOf(
                        listOf(1L, "Work", "deep", 25L, 1000L, 2000L, "Completed", FOCUS_TAG_ID),
                        listOf(2L, "Detox", "detox", 60L, 3000L, 4000L, "Stopped", null),
                        listOf(3L, "Detox", "detox", 90L, 5000L, null, null, null),
                    ),
                    db.selectRows(
                        "SELECT id, type, profileId, plannedMinutes, startedAtMillis, endedAtMillis, outcome, tagId FROM session ORDER BY id",
                    ),
                )
            }
        }

    @Test
    fun `migrating 1 to 2 seeds the default tags`(): Unit =
        runBlocking {
            helper.createDatabase(1).close()
            helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2)).use { db ->
                assertEquals(
                    listOf(
                        listOf(1L, "Focus", "Indigo", 25L, 5L, 15L, 4L, 0L),
                        listOf(2L, "Light", "Teal", 20L, 10L, 20L, 4L, 0L),
                        listOf(3L, "Deep", "Plum", 50L, 10L, 30L, 4L, 0L),
                    ),
                    db.selectRows(
                        "SELECT id, name, color, workMinutes, shortBreakMinutes, longBreakMinutes, longBreakAfter, deleted FROM tag ORDER BY id",
                    ),
                )
                // New tags continue after the seeded ids.
                db.execSQL(
                    "INSERT INTO tag (name, color, workMinutes, shortBreakMinutes, longBreakMinutes, longBreakAfter, deleted) " +
                        "VALUES ('Reading', 'Sand', 30, 5, 15, 4, 0)",
                )
                assertEquals(listOf(listOf(4L)), db.selectRows("SELECT id FROM tag WHERE name = 'Reading'"))
            }
        }

    @Test
    fun `migrating 2 to 3 matches the exported schema`(): Unit =
        runBlocking {
            helper.createDatabase(2).close()
            helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3)).close()
        }

    @Test
    fun `migrating 2 to 3 seeds the two empty block lists and keeps sessions and tags`(): Unit =
        runBlocking {
            helper.createDatabase(2).use { db ->
                db.execSQL(
                    "INSERT INTO session (id, type, profileId, plannedMinutes, startedAtMillis, endedAtMillis, outcome, tagId) " +
                        "VALUES (1, 'Work', 'deep', 25, 1000, 2000, 'Completed', 1)",
                )
                db.execSQL(
                    "INSERT INTO tag (id, name, color, workMinutes, shortBreakMinutes, longBreakMinutes, longBreakAfter, deleted) " +
                        "VALUES (1, 'Focus', 'Indigo', 25, 5, 15, 4, 0)",
                )
            }
            helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3)).use { db ->
                assertEquals(
                    listOf(listOf("deep", "Deep work"), listOf("detox", "Detox")),
                    db.selectRows("SELECT id, name FROM block_list ORDER BY id"),
                )
                assertEquals(listOf(listOf(0L)), db.selectRows("SELECT COUNT(*) FROM block_list_app"))
                assertEquals(listOf(listOf(1L, "deep", 1L)), db.selectRows("SELECT id, profileId, tagId FROM session"))
                assertEquals(listOf(listOf(1L, "Focus")), db.selectRows("SELECT id, name FROM tag"))
            }
        }

    @Test
    fun `migrating 1 to 3 runs both steps`(): Unit =
        runBlocking {
            helper.createDatabase(1).close()
            helper.runMigrationsAndValidate(3, listOf(MIGRATION_1_2, MIGRATION_2_3)).use { db ->
                assertEquals(listOf(listOf(2L)), db.selectRows("SELECT COUNT(*) FROM block_list"))
            }
        }

    @Test
    fun `migrating 3 to 4 matches the exported schema`(): Unit =
        runBlocking {
            helper.createDatabase(3).close()
            helper.runMigrationsAndValidate(4, listOf(MIGRATION_3_4)).close()
        }

    @Test
    fun `migrating 1 to 4 runs every step`(): Unit =
        runBlocking {
            helper.createDatabase(1).close()
            helper.runMigrationsAndValidate(4, listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)).use { db ->
                assertEquals(SEED_TAG_UIDS.map { listOf(it, 0L) }, db.selectRows("SELECT uid, updatedAtMillis FROM tag ORDER BY id"))
            }
        }

    @Test
    fun `migrating 3 to 4 keeps every row and gives each a unique uid, fixed for the seeded tags`(): Unit =
        runBlocking {
            helper.createDatabase(3).use { db ->
                db.execSQL(
                    """
                    INSERT INTO session (id, type, profileId, plannedMinutes, startedAtMillis, endedAtMillis, outcome, tagId) VALUES
                        (1, 'Work', 'deep', 25, 1000, 2000, 'Completed', 1),
                        (2, 'Detox', 'detox', 60, 3000, 4000, 'Stopped', NULL),
                        (3, 'Detox', 'detox', 90, 5000, NULL, NULL, NULL)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO tag (id, name, color, workMinutes, shortBreakMinutes, longBreakMinutes, longBreakAfter, deleted) VALUES
                        (1, 'Focus', 'Indigo', 25, 5, 15, 4, 0),
                        (2, 'Light', 'Teal', 20, 10, 20, 4, 1),
                        (3, 'Reading', 'Plum', 50, 10, 30, 4, 0),
                        (4, 'Writing', 'Sand', 30, 5, 15, 4, 0)
                    """.trimIndent(),
                )
            }
            helper.runMigrationsAndValidate(4, listOf(MIGRATION_3_4)).use { db ->
                assertEquals(
                    listOf(
                        listOf(1L, "Work", "deep", 25L, 1000L, 2000L, "Completed", 1L),
                        listOf(2L, "Detox", "detox", 60L, 3000L, 4000L, "Stopped", null),
                        listOf(3L, "Detox", "detox", 90L, 5000L, null, null, null),
                    ),
                    db.selectRows(
                        "SELECT id, type, profileId, plannedMinutes, startedAtMillis, endedAtMillis, outcome, tagId FROM session ORDER BY id",
                    ),
                )
                val sessionUids = db.selectRows("SELECT uid FROM session").map { it.single() as String }
                assertEquals(3, sessionUids.toSet().size)
                assertTrue(sessionUids.all(UUID_PATTERN::matches), "$sessionUids")

                val tags = db.selectRows("SELECT id, name, deleted, uid, updatedAtMillis, updatedBy FROM tag ORDER BY id")
                assertEquals(listOf("Focus", "Light", "Reading", "Writing"), tags.map { it[1] })
                assertEquals(listOf(0L, 1L, 0L, 0L), tags.map { it[2] })
                assertEquals(SEED_TAG_UIDS, tags.take(3).map { it[3] })
                assertTrue(UUID_PATTERN.matches(tags[3][3] as String))
                assertEquals(listOf("", "", "", ""), tags.map { it[5] })
                // Only the seeded tag still as seeded counts as never edited.
                assertEquals(0L, tags[0][4])
                assertTrue(tags.drop(1).all { (it[4] as Long) > 0L })

                // New rows continue after the kept ids.
                db.execSQL(
                    "INSERT INTO tag (name, color, workMinutes, shortBreakMinutes, longBreakMinutes, longBreakAfter, deleted, " +
                        "uid, updatedAtMillis, updatedBy) VALUES ('New', 'Sand', 30, 5, 15, 4, 0, 'x', 1, 'd')",
                )
                assertEquals(listOf(listOf(5L)), db.selectRows("SELECT id FROM tag WHERE name = 'New'"))
            }
        }

    @Test
    fun `migrating 4 to 5 matches the exported schema`(): Unit =
        runBlocking {
            helper.createDatabase(4).close()
            helper.runMigrationsAndValidate(5, listOf(MIGRATION_4_5)).close()
        }

    @Test
    fun `migrating 1 to 5 runs every step`(): Unit =
        runBlocking {
            helper.createDatabase(1).close()
            helper.runMigrationsAndValidate(5, listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)).close()
        }

    @Test
    fun `migrating 4 to 5 keeps every session, with no activity and no apps yet`(): Unit =
        runBlocking {
            helper.createDatabase(4).use { db ->
                db.execSQL(
                    """
                    INSERT INTO session (id, type, profileId, plannedMinutes, startedAtMillis, endedAtMillis, outcome, tagId, uid) VALUES
                        (1, 'Work', 'deep', 25, 1000, 2000, 'Completed', 1, 'u1'),
                        (2, 'Detox', 'detox', 90, 5000, NULL, NULL, NULL, 'u2')
                    """.trimIndent(),
                )
            }
            helper.runMigrationsAndValidate(5, listOf(MIGRATION_4_5)).use { db ->
                assertEquals(
                    listOf(
                        listOf(1L, "Work", "deep", 25L, 1000L, 2000L, "Completed", 1L, "u1", null),
                        listOf(2L, "Detox", "detox", 90L, 5000L, null, null, null, "u2", null),
                    ),
                    db.selectRows(
                        "SELECT id, type, profileId, plannedMinutes, startedAtMillis, endedAtMillis, outcome, tagId, uid, activityId " +
                            "FROM session ORDER BY id",
                    ),
                )
                assertEquals(listOf(listOf(0L)), db.selectRows("SELECT COUNT(*) FROM activity"))
                assertEquals(listOf(listOf(0L)), db.selectRows("SELECT COUNT(*) FROM session_app"))
            }
        }

    @Test
    fun `migrating 5 to 6 matches the exported schema`(): Unit =
        runBlocking {
            helper.createDatabase(5).close()
            helper.runMigrationsAndValidate(6, listOf(MIGRATION_5_6)).close()
        }

    @Test
    fun `migrating 1 to 7 runs every step`(): Unit =
        runBlocking {
            helper.createDatabase(1).close()
            helper
                .runMigrationsAndValidate(
                    7,
                    listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7),
                ).close()
        }

    @Test
    fun `migrating 6 to 7 drops the tag rules and keeps sessions, apps and activities`(): Unit =
        runBlocking {
            helper.createDatabase(6).use { db ->
                db.execSQL(
                    "INSERT INTO session (id, type, profileId, plannedMinutes, startedAtMillis, endedAtMillis, outcome, tagId, uid) " +
                        "VALUES (1, 'Work', 'deep', 25, 1000, 2000, 'Completed', 1, 'u1')",
                )
                db.execSQL(
                    "INSERT INTO session_app (sessionUid, packageName, label, foregroundMillis) VALUES ('u1', 'org.anki', 'Anki', 60000)",
                )
                db.execSQL(
                    "INSERT INTO activity (id, name, deleted, uid, updatedAtMillis, updatedBy) VALUES (1, 'Study', 0, 'a1', 1, 'x')",
                )
                db.execSQL(
                    "INSERT INTO tag_rule (target, label, tagUid, deleted, updatedAtMillis, updatedBy) " +
                        "VALUES ('org.anki', 'Anki', 't', 0, 1, 'x')",
                )
            }
            helper.runMigrationsAndValidate(7, listOf(MIGRATION_6_7)).use { db ->
                assertEquals(listOf(listOf(1L)), db.selectRows("SELECT COUNT(*) FROM session"))
                assertEquals(listOf(listOf(1L)), db.selectRows("SELECT COUNT(*) FROM session_app"))
                assertEquals(listOf(listOf("Study")), db.selectRows("SELECT name FROM activity"))
                assertEquals(listOf(listOf(0L)), db.selectRows("SELECT COUNT(*) FROM activity_rule"))
                assertEquals(
                    listOf(listOf(0L)),
                    db.selectRows("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'tag_rule'"),
                )
            }
        }

    @Test
    fun `migrating 5 to 6 keeps sessions and apps, with no rules yet`(): Unit =
        runBlocking {
            helper.createDatabase(5).use { db ->
                db.execSQL(
                    "INSERT INTO session (id, type, profileId, plannedMinutes, startedAtMillis, endedAtMillis, outcome, tagId, uid) " +
                        "VALUES (1, 'Work', 'deep', 25, 1000, 2000, 'Completed', 1, 'u1')",
                )
                db.execSQL(
                    "INSERT INTO session_app (sessionUid, packageName, label, foregroundMillis) VALUES ('u1', 'org.anki', 'Anki', 60000)",
                )
            }
            helper.runMigrationsAndValidate(6, listOf(MIGRATION_5_6)).use { db ->
                assertEquals(
                    listOf(listOf("u1", "org.anki", 60000L)),
                    db.selectRows("SELECT sessionUid, packageName, foregroundMillis FROM session_app"),
                )
                assertEquals(listOf(listOf(1L)), db.selectRows("SELECT COUNT(*) FROM session"))
                assertEquals(listOf(listOf(0L)), db.selectRows("SELECT COUNT(*) FROM tag_rule"))
            }
        }
}

private fun SQLiteConnection.selectRows(sql: String): List<List<Any?>> =
    prepare(sql).use { statement ->
        buildList {
            while (statement.step()) {
                add(
                    List(statement.getColumnCount()) { column ->
                        when (statement.getColumnType(column)) {
                            SQLITE_DATA_TEXT -> statement.getText(column)
                            SQLITE_DATA_NULL -> null
                            else -> statement.getLong(column)
                        }
                    },
                )
            }
        }.also { assertTrue(it.isNotEmpty(), "expected rows for: $sql") }
    }
