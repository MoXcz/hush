// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PomodoroEngineTest {
    private val now = Instant.parse("2026-11-05T09:00:00Z")
    private val durations = TimerDurations()

    @Test
    fun `defaults are 25, 5 and 15 minutes with a long break after 4`() {
        assertEquals(TimerDurations(25.minutes, 5.minutes, 15.minutes, 4), durations)
        assertEquals(TimerSnapshot(Phase.Work, 0, 25.minutes, RunState.Ready), PomodoroEngine.initial(durations))
    }

    @Test
    fun `start sets the end time from the full duration`() {
        val started = PomodoroEngine.start(PomodoroEngine.initial(durations), now)
        assertEquals(RunState.Running(now + 25.minutes), started.run)
        assertEquals(25.minutes, PomodoroEngine.remaining(started, now))
        assertEquals(10.minutes, PomodoroEngine.remaining(started, now + 15.minutes))
    }

    @Test
    fun `pause keeps the remaining time and resume ends that much later`() {
        val started = PomodoroEngine.start(PomodoroEngine.initial(durations), now)
        val paused = PomodoroEngine.pause(started, now + 10.minutes)
        assertEquals(RunState.Paused(15.minutes), paused.run)
        assertEquals(15.minutes, PomodoroEngine.remaining(paused, now + 2.hours))
        val resumed = PomodoroEngine.start(paused, now + 1.hours)
        assertEquals(RunState.Running(now + 60.minutes + 15.minutes), resumed.run)
    }

    @Test
    fun `the first start sets the start time and pauses keep it`() {
        val started = PomodoroEngine.start(PomodoroEngine.initial(durations), now)
        assertEquals(now, started.startedAt)
        val resumed = PomodoroEngine.start(PomodoroEngine.pause(started, now + 1.minutes), now + 1.hours)
        assertEquals(now, resumed.startedAt)
        assertEquals(null, PomodoroEngine.advance(resumed, durations).startedAt)
    }

    @Test
    fun `due exactly at the end time and not before`() {
        val started = PomodoroEngine.start(PomodoroEngine.initial(durations), now)
        assertFalse(PomodoroEngine.isDue(started, now + 25.minutes - 1.seconds))
        assertTrue(PomodoroEngine.isDue(started, now + 25.minutes))
        assertFalse(PomodoroEngine.isDue(PomodoroEngine.pause(started, now), now + 1.hours))
    }

    @Test
    fun `remaining never goes below zero or above the duration`() {
        val started = PomodoroEngine.start(PomodoroEngine.initial(durations), now)
        assertEquals(0.minutes, PomodoroEngine.remaining(started, now + 2.hours))
        // Wall clock set back an hour.
        assertEquals(25.minutes, PomodoroEngine.remaining(started, now - 60.minutes))
    }

    @Test
    fun `phases cycle work and short breaks, with a long break after the fourth work phase`() {
        var snapshot = PomodoroEngine.initial(durations)
        val phases = mutableListOf(snapshot.phase to snapshot.completedWorkPhases)
        repeat(9) {
            snapshot = PomodoroEngine.advance(snapshot, durations)
            phases += snapshot.phase to snapshot.completedWorkPhases
        }
        assertEquals(
            listOf(
                Phase.Work to 0,
                Phase.ShortBreak to 1,
                Phase.Work to 1,
                Phase.ShortBreak to 2,
                Phase.Work to 2,
                Phase.ShortBreak to 3,
                Phase.Work to 3,
                Phase.LongBreak to 4,
                Phase.Work to 0,
                Phase.ShortBreak to 1,
            ),
            phases,
        )
    }

    @Test
    fun `the next phase is staged at its own duration and waits for start`() {
        val running = PomodoroEngine.start(PomodoroEngine.initial(durations), now).copy(blocking = true, canStopEarly = false)
        val next = PomodoroEngine.advance(running, durations)
        assertEquals(TimerSnapshot(Phase.ShortBreak, 1, 5.minutes, RunState.Ready, blocking = false, canStopEarly = true), next)
        val fourth = TimerSnapshot(Phase.Work, completedWorkPhases = 3)
        assertEquals(15.minutes, PomodoroEngine.advance(fourth, durations).duration)
    }

    @Test
    fun `new durations change a phase that has not started, never a started one`() {
        val longer = durations.copy(work = 50.minutes)
        val ready = PomodoroEngine.initial(durations)
        assertEquals(50.minutes, PomodoroEngine.withDurations(ready, longer).duration)
        val running = PomodoroEngine.start(ready, now)
        assertEquals(running, PomodoroEngine.withDurations(running, longer))
        val paused = PomodoroEngine.pause(running, now + 1.minutes)
        assertEquals(paused, PomodoroEngine.withDurations(paused, longer))
    }

    @Test
    fun `lowering long break after mid-set gives the long break next`() {
        val third = TimerSnapshot(Phase.Work, completedWorkPhases = 2)
        assertEquals(Phase.LongBreak, PomodoroEngine.advance(third, durations.copy(longBreakAfter = 2)).phase)
    }

    @Test
    fun `blocker minutes round up and are at least one`() {
        assertEquals(1, 0.seconds.wholeMinutesUp())
        assertEquals(1, 59.seconds.wholeMinutesUp())
        assertEquals(1, 60.seconds.wholeMinutesUp())
        assertEquals(2, 61.seconds.wholeMinutesUp())
        assertEquals(25, 25.minutes.wholeMinutesUp())
        assertEquals(15, (14.minutes + 30.seconds).wholeMinutesUp())
    }
}
