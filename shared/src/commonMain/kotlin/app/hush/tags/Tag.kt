// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.tags

import app.hush.timer.TimerDurations
import kotlin.jvm.JvmInline

@JvmInline
value class TagId(
    val value: Long,
)

/** The fixed set of tag colours. Stored by name; the shades live in the theme. */
enum class TagColor { Indigo, Teal, Sage, Sand, Rose, Plum }

/** What a work phase is for, with its own Pomodoro durations. */
data class Tag(
    val id: TagId,
    val name: String,
    val color: TagColor,
    val durations: TimerDurations,
    /** Hidden from choosing and editing, but kept so past sessions keep their name and colour. */
    val deleted: Boolean = false,
)

const val TAG_NAME_MAX_LENGTH = 24
