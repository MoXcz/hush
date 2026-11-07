// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

import app.hush.core.AppDispatchers
import app.hush.usage.APP_LABEL_MAX_LENGTH
import app.hush.usage.AppTime
import app.hush.usage.AppUsageReader
import app.hush.usage.PACKAGE_NAME_MAX_LENGTH
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

// AWT's X11 window class for MainKt.main (dots turned into dashes); main() starts AWT to keep it so.
const val HUSH_WINDOW_CLASS = "app-hush-desktop-MainKt"

// Nothing to grant in the system: Hush's own switch turns it on.
class HyprlandAppUsageReader(
    private val log: FocusLog,
    private val entries: DesktopEntries,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    private val switch: UsageSwitch,
) : AppUsageReader {
    override val hasSwitch: Boolean = true

    override suspend fun foregroundTime(
        start: Instant,
        end: Instant,
    ): List<AppTime>? =
        if (!switch.enabled.value) {
            null
        } else {
            read(start, end)
        }

    private suspend fun read(
        start: Instant,
        end: Instant,
    ): List<AppTime> =
        withContext(dispatchers.io) {
            val times =
                FocusTime.perTarget(
                    log.entries(),
                    start.toEpochMilliseconds(),
                    end.toEpochMilliseconds(),
                    clock.now().toEpochMilliseconds(),
                    excluded = setOf(HUSH_WINDOW_CLASS),
                )
            val labels = entries.labels(times.keys.filter { it.label == null }.map { it.key })
            times.entries
                .sortedByDescending { it.value }
                .map { (target, millis) ->
                    val label = target.label ?: labels[target.key] ?: target.key
                    AppTime(target.key.take(PACKAGE_NAME_MAX_LENGTH), label.take(APP_LABEL_MAX_LENGTH), millis.milliseconds)
                }
        }

    override fun isPermitted(): Boolean = switch.enabled.value

    override fun openSettings(): Boolean = false

    override suspend fun setEnabled(on: Boolean) = switch.set(on)
}
