// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.settings

import app.hush.activities.ActivityId
import app.hush.tags.TagId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

data class Settings(
    /** The tag the timer uses; null (or a deleted tag) means the first remaining tag. */
    val selectedTagId: TagId? = null,
    /** What the next work phases are spent doing; kept until the user picks another or none. */
    val selectedActivityId: ActivityId? = null,
    /** Keep the dim clock visible above the lock screen. */
    val dimClockShowWhenLocked: Boolean = false,
    /** The longest detox level; the path climbs to it from 1 h in 30-minute steps. */
    val detoxGoal: Duration = 4.hours,
)
