// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.usage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

class FakeAppUsageReader(
    var permitted: Boolean = true,
    var apps: List<AppTime> = emptyList(),
    override val hasSwitch: Boolean = false,
) : AppUsageReader {
    val reads = mutableListOf<Pair<Instant, Instant>>()
    var settingsOpened = 0
    var failure: Exception? = null

    override suspend fun foregroundTime(
        start: Instant,
        end: Instant,
    ): List<AppTime>? {
        failure?.let { throw it }
        if (!permitted) return null
        reads += start to end
        return apps
    }

    override fun isPermitted(): Boolean = permitted

    override fun openSettings(): Boolean {
        settingsOpened++
        return true
    }

    override suspend fun setEnabled(on: Boolean) {
        permitted = on
    }
}

/** Keyed by (session uid, package) and keeps the first row, like the real table. */
class FakeAppUsageRepository(
    initial: List<SessionApp> = emptyList(),
) : AppUsageRepository {
    override val usage = MutableStateFlow(initial)

    /** The session start the fake reports for recorded rows. */
    var startedAt: Instant = Instant.fromEpochMilliseconds(0)

    override suspend fun record(
        sessionUid: String,
        apps: List<AppTime>,
    ) {
        usage.update { rows ->
            val known = rows.map { it.sessionUid to it.packageName }.toSet()
            rows +
                apps
                    .filter { (sessionUid to it.packageName) !in known }
                    .map { SessionApp(sessionUid, it.packageName, it.label, it.duration, startedAt) }
        }
    }
}
