// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

import app.hush.timer.Phase
import app.hush.timer.RunState
import app.hush.timer.TimerSnapshot
import app.hush.timer.TimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FocusTrackingTest {
    private class FakeWatcher : FocusWatcher {
        val calls = mutableListOf<String>()

        override fun start() {
            calls += "start"
        }

        override fun stop() {
            calls += "stop"
        }
    }

    private val running = TimerState(TimerSnapshot(phase = Phase.Work, run = RunState.Running(Instant.parse("2026-11-07T10:00:00Z"))))
    private val breakRunning =
        TimerState(TimerSnapshot(phase = Phase.ShortBreak, run = RunState.Running(Instant.parse("2026-11-07T10:00:00Z"))))

    @Test
    fun `watches only while the switch is on and a work phase runs`() =
        runTest {
            val enabled = MutableStateFlow(false)
            val timer = MutableStateFlow(TimerState())
            val watcher = FakeWatcher()
            val tracking = FocusTracking(enabled, timer, watcher, backgroundScope)
            tracking.start()
            runCurrent()
            timer.value = running
            runCurrent()
            assertEquals(listOf("stop"), watcher.calls)

            enabled.value = true
            runCurrent()
            timer.value = breakRunning
            runCurrent()
            timer.value = running
            runCurrent()
            enabled.value = false
            runCurrent()
            assertEquals(listOf("stop", "start", "stop", "start", "stop"), watcher.calls)
        }
}
