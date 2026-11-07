// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.detox.ui

import androidx.compose.runtime.Immutable
import app.hush.detox.LevelOutcome
import kotlin.time.Duration

/** [Current] is the next level to pass (none once the path is complete). */
enum class LevelStatus { Passed, Current, Locked }

@Immutable
data class LevelItem(
    val minutes: Int,
    val status: LevelStatus,
    /** The level Start attempts, or the running attempt's level. */
    val selected: Boolean = false,
    /** Can be tapped to attempt it instead (passed or next, before Start). */
    val selectable: Boolean = false,
)

@Immutable
data class ActiveAttemptUi(
    val levelMinutes: Int,
    val remaining: Duration,
    /** Elapsed share of the level, 0 to 1. */
    val progress: Float,
    val canStopEarly: Boolean,
)

/** Something to tell the user after a command. NeedsSetup is handled by opening blocker setup. */
enum class PathNotice { NeedsSetup, Refused, Unreachable, Unconfirmed }

@Immutable
data class PathUiState(
    val levels: List<LevelItem> = emptyList(),
    /** The level Start attempts (the next one unless the user chose another), or the running one. */
    val currentLevelMinutes: Int = 60,
    /** The user chose a passed level shorter than the next one; null means Start attempts the next level. */
    val chosenLevelMinutes: Int? = null,
    /** Every level up to the goal is passed; the last level can still be repeated. */
    val complete: Boolean = false,
    val active: ActiveAttemptUi? = null,
    val outcome: LevelOutcome? = null,
    /** No detox profile is chosen, so an attempt can't start. */
    val noProfile: Boolean = false,
    val notice: PathNotice? = null,
    val busy: Boolean = false,
    /** This device can't block (desktop): the path is shown read-only, attempts start on the phone. */
    val phoneOnly: Boolean = false,
)

sealed interface PathAction {
    data object StartAttempt : PathAction

    /** Tap on a level row: attempt that level instead. The next level goes back to the default. */
    data class SelectLevel(
        val minutes: Int,
    ) : PathAction

    /** App resumed: check the running attempt against the clock and the blocker. */
    data object Check : PathAction

    data object DismissOutcome : PathAction

    data object DismissNotice : PathAction
}
