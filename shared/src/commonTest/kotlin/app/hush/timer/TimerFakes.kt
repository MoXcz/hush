// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** Wall clock tied to virtual time, so advanceTimeBy moves both delays and "now". */
@OptIn(ExperimentalCoroutinesApi::class)
class TestClock(
    private val scheduler: TestCoroutineScheduler,
    private val start: Instant = START,
) : Clock {
    override fun now(): Instant = start + scheduler.currentTime.milliseconds

    companion object {
        val START: Instant = Instant.parse("2026-11-05T09:00:00Z")
    }
}

class FakeTimerStateStore(
    var stored: TimerSnapshot? = null,
) : TimerStateStore {
    var saves = 0

    override suspend fun load(): TimerSnapshot? = stored

    override suspend fun save(snapshot: TimerSnapshot) {
        stored = snapshot
        saves++
    }
}

class FakeWakeupScheduler : WakeupScheduler {
    var scheduled: Instant? = null

    override fun schedulePhaseEnd(at: Instant) {
        scheduled = at
    }

    override fun cancelPhaseEnd() {
        scheduled = null
    }

    var levelCheck: Instant? = null

    override fun scheduleLevelCheck(at: Instant) {
        levelCheck = at
    }

    override fun cancelLevelCheck() {
        levelCheck = null
    }
}

class FakePhaseAlerts : PhaseAlerts {
    val ended = mutableListOf<Pair<Phase, Phase>>()
    var clears = 0

    override fun phaseEnded(
        ended: Phase,
        next: Phase,
    ) {
        this.ended += ended to next
    }

    override fun clear() {
        clears++
    }
}
