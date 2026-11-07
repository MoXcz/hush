// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.usage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Instant

/** Stores which apps were in the foreground during a finished work phase. */
class WorkUsageRecorder(
    private val reader: AppUsageReader,
    private val repository: AppUsageRepository,
    private val scope: CoroutineScope,
) {
    // Launched, so reading usage records never holds the timer's lock.
    fun record(
        sessionUid: String,
        start: Instant,
        end: Instant,
    ): Job = scope.launch { capture(sessionUid, start, end) }

    suspend fun capture(
        sessionUid: String,
        start: Instant,
        end: Instant,
    ) {
        try {
            val apps = reader.foregroundTime(start, end) ?: return
            if (apps.isNotEmpty()) repository.record(sessionUid, apps)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A failed read or write loses this phase's apps; it must never crash the app scope.
        }
    }
}
