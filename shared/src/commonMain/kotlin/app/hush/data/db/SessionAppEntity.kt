// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Entity

/**
 * One app's foreground time during a work phase, keyed by the session's uid (not its local id),
 * so rows from a sync attach to the same session on both devices. Append-only, like sessions.
 */
@Entity(tableName = "session_app", primaryKeys = ["sessionUid", "packageName"])
data class SessionAppEntity(
    val sessionUid: String,
    val packageName: String,
    val label: String,
    val foregroundMillis: Long,
)

/** A [SessionAppEntity] with its session's start. */
data class SessionAppRow(
    val sessionUid: String,
    val packageName: String,
    val label: String,
    val foregroundMillis: Long,
    val sessionStartedAtMillis: Long,
)
