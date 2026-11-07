// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.usage

import app.hush.data.db.SessionAppDao
import app.hush.data.db.SessionAppEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

// Android caps package names at 255; labels are cut so a long app name can't bloat a sync.
const val PACKAGE_NAME_MAX_LENGTH = 255
const val APP_LABEL_MAX_LENGTH = 100

/** An app's time in the foreground during one recorded work phase. */
data class SessionApp(
    val sessionUid: String,
    val packageName: String,
    val label: String,
    val foreground: Duration,
    /** When the work phase started, so Stats can place it in a day or week. */
    val sessionStartedAt: Instant,
)

/** The apps in the foreground during recorded work phases (this device's and synced ones). */
interface AppUsageRepository {
    /** Every stored app time whose work phase is in history. */
    val usage: Flow<List<SessionApp>>

    /** Stores [apps] for the work phase [sessionUid]. Idempotent: an app already stored for it is kept. */
    suspend fun record(
        sessionUid: String,
        apps: List<AppTime>,
    )
}

class RoomAppUsageRepository(
    private val dao: SessionAppDao,
) : AppUsageRepository {
    override val usage: Flow<List<SessionApp>> =
        dao.usage().map { rows ->
            rows.map {
                SessionApp(
                    sessionUid = it.sessionUid,
                    packageName = it.packageName,
                    label = it.label,
                    foreground = it.foregroundMillis.milliseconds,
                    sessionStartedAt = Instant.fromEpochMilliseconds(it.sessionStartedAtMillis),
                )
            }
        }

    override suspend fun record(
        sessionUid: String,
        apps: List<AppTime>,
    ) {
        dao.insertAll(
            apps.map {
                SessionAppEntity(
                    sessionUid = sessionUid,
                    packageName = it.packageName,
                    label = it.label.take(APP_LABEL_MAX_LENGTH),
                    foregroundMillis = it.duration.inWholeMilliseconds,
                )
            },
        )
    }
}
