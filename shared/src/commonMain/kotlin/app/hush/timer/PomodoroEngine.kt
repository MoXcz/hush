// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Pure Pomodoro rules. Every function takes the current instant and returns a new snapshot; nothing
 * counts down, so the result is the same whether the process lived through the phase or not.
 */
object PomodoroEngine {
    fun initial(durations: TimerDurations): TimerSnapshot = TimerSnapshot(duration = durations.work)

    fun remaining(
        snapshot: TimerSnapshot,
        now: Instant,
    ): Duration =
        when (val run = snapshot.run) {
            RunState.Ready -> snapshot.duration

            // Capped above too: the wall clock can be set back.
            is RunState.Running -> (run.endsAt - now).coerceIn(Duration.ZERO, snapshot.duration)

            is RunState.Paused -> run.remaining
        }

    fun isDue(
        snapshot: TimerSnapshot,
        now: Instant,
    ): Boolean {
        val run = snapshot.run
        return run is RunState.Running && now >= run.endsAt
    }

    fun start(
        snapshot: TimerSnapshot,
        now: Instant,
    ): TimerSnapshot =
        when (val run = snapshot.run) {
            RunState.Ready -> snapshot.copy(run = RunState.Running(now + snapshot.duration), startedAt = now)
            is RunState.Paused -> snapshot.copy(run = RunState.Running(now + run.remaining))
            is RunState.Running -> snapshot
        }

    fun pause(
        snapshot: TimerSnapshot,
        now: Instant,
    ): TimerSnapshot =
        when (snapshot.run) {
            is RunState.Running -> snapshot.copy(run = RunState.Paused(remaining(snapshot, now)))
            RunState.Ready, is RunState.Paused -> snapshot
        }

    /** The next phase, staged and waiting for Start (as Tomato does when a phase runs out). */
    fun advance(
        snapshot: TimerSnapshot,
        durations: TimerDurations,
    ): TimerSnapshot {
        val (next, completed) =
            when (snapshot.phase) {
                Phase.Work -> {
                    val done = snapshot.completedWorkPhases + 1
                    (if (done >= durations.longBreakAfter) Phase.LongBreak else Phase.ShortBreak) to done
                }

                Phase.ShortBreak -> {
                    Phase.Work to snapshot.completedWorkPhases
                }

                Phase.LongBreak -> {
                    Phase.Work to 0
                }
            }
        return TimerSnapshot(phase = next, completedWorkPhases = completed, duration = durations.of(next))
    }

    /** New durations apply to a phase that hasn't started yet. A started phase keeps its length. */
    fun withDurations(
        snapshot: TimerSnapshot,
        durations: TimerDurations,
    ): TimerSnapshot = if (snapshot.run == RunState.Ready) snapshot.copy(duration = durations.of(snapshot.phase)) else snapshot
}
