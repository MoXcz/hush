// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.detox

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Where the user stands on the path. Derived from passed attempts; never stored. */
data class PathProgress(
    val levels: List<Duration>,
    /** Index into [levels] of the level to attempt next (the last one once the path is complete). */
    val currentIndex: Int,
    val complete: Boolean,
) {
    val currentLevel: Duration get() = levels[currentIndex]

    /** Levels the user may attempt: every passed level and the next one (all of them once complete). */
    val choosable: List<Duration> get() = levels.subList(0, currentIndex + 1)
}

/**
 * Pure level rules: levels go 1 h, 1.5 h, 2 h ... up to the goal. Passing a
 * level unlocks the next; failing means retrying the same level, with no other penalty.
 */
object DetoxPath {
    val FIRST_LEVEL: Duration = 1.hours
    val STEP: Duration = 30.minutes

    /** A goal under 1 h is treated as 1 h. A goal off the 30-minute grid becomes the last level. */
    fun levels(goal: Duration): List<Duration> {
        val top = goal.coerceAtLeast(FIRST_LEVEL)
        val levels = generateSequence(FIRST_LEVEL) { it + STEP }.takeWhile { it <= top }.toList()
        return if (levels.last() == top) levels else levels + top
    }

    /**
     * Progress follows the longest level ever passed, so changing the goal never loses progress:
     * the next level is the first one longer than that.
     */
    fun progress(
        goal: Duration,
        longestPassed: Duration?,
    ): PathProgress {
        val levels = levels(goal)
        val next = if (longestPassed == null) 0 else levels.indexOfFirst { it > longestPassed }
        return if (next == -1) {
            PathProgress(levels, levels.lastIndex, complete = true)
        } else {
            PathProgress(levels, next, complete = false)
        }
    }
}

/** How a detox attempt ended. Only [Passed] unlocks the next level. */
enum class LevelOutcome {
    Passed,

    /** Passed a level that was already passed (an earlier one, or the goal again): progress stays. */
    PassedAgain,

    /** Ended through the panic flow. */
    EndedEarly,

    /** The blocker said the session was gone before the end: blocking was turned off or stopped. */
    StoppedInBlocker,
}
