// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer

import app.hush.activities.Activity
import app.hush.tags.Tag
import app.hush.tags.TagId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

enum class Phase { Work, ShortBreak, LongBreak }

data class TimerDurations(
    val work: Duration = 25.minutes,
    val shortBreak: Duration = 5.minutes,
    val longBreak: Duration = 15.minutes,
    /** Work phases per set: the long break comes after this many. */
    val longBreakAfter: Int = 4,
) {
    fun of(phase: Phase): Duration =
        when (phase) {
            Phase.Work -> work
            Phase.ShortBreak -> shortBreak
            Phase.LongBreak -> longBreak
        }
}

sealed interface RunState {
    /** Staged at full length, waiting for Start. */
    data object Ready : RunState

    data class Running(
        val endsAt: Instant,
    ) : RunState

    data class Paused(
        val remaining: Duration,
    ) : RunState
}

/** Everything that survives process death. Remaining time is derived from it and the clock. */
data class TimerSnapshot(
    val phase: Phase = Phase.Work,
    /** Work phases finished since the last long break. */
    val completedWorkPhases: Int = 0,
    /** Fixed when the phase is staged, so a settings change never moves a running phase. */
    val duration: Duration = TimerDurations().work,
    val run: RunState = RunState.Ready,
    /** A blocker session was started for this work phase and not stopped by us. */
    val blocking: Boolean = false,
    val canStopEarly: Boolean = true,
    /** When this phase was first started; kept through pauses, for history. */
    val startedAt: Instant? = null,
    /** The tag of a started work phase, fixed at Start so history is right even if the tag changes. */
    val tagId: TagId? = null,
) {
    /** A work phase has started (running or paused): its tag and length can't change any more. */
    val workInProgress: Boolean get() = phase == Phase.Work && run != RunState.Ready
}

data class TimerState(
    val snapshot: TimerSnapshot = TimerSnapshot(),
    val remaining: Duration = snapshot.duration,
    val workPhasesPerSet: Int = TimerDurations().longBreakAfter,
    /** The selected tag, whose durations the timer uses. */
    val tag: Tag? = null,
    /** The selected activity, written into each work phase as it's recorded. */
    val activity: Activity? = null,
) {
    val phase: Phase get() = snapshot.phase
    val isRunning: Boolean get() = snapshot.run is RunState.Running
}
