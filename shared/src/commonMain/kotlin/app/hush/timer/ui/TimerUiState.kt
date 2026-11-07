// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer.ui

import androidx.compose.runtime.Immutable
import app.hush.activities.Activity
import app.hush.activities.ActivityId
import app.hush.tags.Tag
import app.hush.tags.TagId
import app.hush.timer.Phase
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

enum class TimerStatus { Ready, Running, Paused }

/** Something to tell the user after a command. NeedsSetup is handled by opening blocker setup. */
enum class TimerNotice { NeedsSetup, Refused, CantStopEarly, Unreachable, Unconfirmed }

@Immutable
data class TimerUiState(
    val phase: Phase = Phase.Work,
    val remaining: Duration = 25.minutes,
    /** Elapsed share of the phase, 0 to 1. */
    val progress: Float = 0f,
    val status: TimerStatus = TimerStatus.Ready,
    /** The work phase in progress, or the one just finished during a break. */
    val workPhase: Int = 1,
    val workPhasesPerSet: Int = 4,
    /** False while a blocked work phase can't stop early: pause, skip and reset are hidden. */
    val canStopEarly: Boolean = true,
    val canReset: Boolean = false,
    /** No deep-work profile is configured, so work phases don't block anything. */
    val blockingOff: Boolean = false,
    /** This device can't block (desktop): work phases run unblocked, said in one quiet line. */
    val blockingPhoneOnly: Boolean = false,
    val notice: TimerNotice? = null,
    /** A command is talking to the blocker. */
    val busy: Boolean = false,
    /** The phase staged when this one ends, and its length. */
    val nextPhase: Phase = Phase.ShortBreak,
    val nextDuration: Duration = 5.minutes,
    val tag: Tag? = null,
    /** Tags to switch to, in the chip's sheet. */
    val tags: List<Tag> = emptyList(),
    /** False while a work phase is in progress: its tag is fixed until it ends. */
    val canSwitchTag: Boolean = true,
    /** What the work phases are spent doing; null when nothing is picked. Can change at any time. */
    val activity: Activity? = null,
    /** Activities to pick from, in the "Doing" sheet. */
    val activities: List<Activity> = emptyList(),
)

sealed interface TimerAction {
    data object Start : TimerAction

    data object Pause : TimerAction

    data object Skip : TimerAction

    data object Reset : TimerAction

    data object DismissNotice : TimerAction

    data class SelectTag(
        val id: TagId,
    ) : TimerAction

    /** Null picks nothing. */
    data class SelectActivity(
        val id: ActivityId?,
    ) : TimerAction

    /** Adds an activity by name and picks it. */
    data class AddActivity(
        val name: String,
    ) : TimerAction
}
