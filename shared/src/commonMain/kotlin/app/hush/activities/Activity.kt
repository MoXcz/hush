// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.activities

import kotlin.jvm.JvmInline

@JvmInline
value class ActivityId(
    val value: Long,
)

/** What a work phase was spent doing, when it's not on this device (e.g. "Reading on Kobo"). Just a name. */
data class Activity(
    val id: ActivityId,
    val name: String,
    /** Hidden from choosing and editing, but kept so past sessions keep their name. */
    val deleted: Boolean = false,
)

const val ACTIVITY_NAME_MAX_LENGTH = 24
