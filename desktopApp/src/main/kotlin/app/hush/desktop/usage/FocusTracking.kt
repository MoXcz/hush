// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

import app.hush.timer.Phase
import app.hush.timer.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Follows the focused window only while the switch is on and a work phase runs: nothing is read
// outside work, and pauses and breaks are gaps.
class FocusTracking(
    private val enabled: Flow<Boolean>,
    private val timer: Flow<TimerState>,
    private val watcher: FocusWatcher,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    @Synchronized
    fun start() {
        job =
            scope.launch {
                combine(enabled, timer.map { it.phase == Phase.Work && it.isRunning }) { on, working -> on && working }
                    .distinctUntilChanged()
                    .collect { if (it) watcher.start() else watcher.stop() }
            }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        watcher.stop()
    }
}

/** Follows focus changes into the log while started. */
interface FocusWatcher {
    fun start()

    fun stop()
}
