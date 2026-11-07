// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop

import app.hush.timer.WakeupScheduler
import kotlin.time.Instant

/**
 * The desktop app can't be woken once it's closed. While it runs, the timer's once-a-second
 * ticker ends phases; a phase that ran out while it was closed is expired on the next start.
 */
class NoWakeups : WakeupScheduler {
    override fun schedulePhaseEnd(at: Instant) = Unit

    override fun cancelPhaseEnd() = Unit

    override fun scheduleLevelCheck(at: Instant) = Unit

    override fun cancelLevelCheck() = Unit
}
