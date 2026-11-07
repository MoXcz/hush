// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer

import kotlin.time.Instant

/** Persists the timer so a running phase survives process death. */
interface TimerStateStore {
    suspend fun load(): TimerSnapshot?

    suspend fun save(snapshot: TimerSnapshot)
}

/**
 * Wakes the app even while it sleeps or is dead, for two purposes with one alarm each: the end of
 * a Pomodoro phase ([TimerController.expireIfDue]) and a detox level check
 * ([app.hush.detox.DetoxController.check]). A wakeup never decides anything itself.
 */
interface WakeupScheduler {
    fun schedulePhaseEnd(at: Instant)

    fun cancelPhaseEnd()

    /** Replaces any earlier level check. Must survive a reboot while an attempt is open. */
    fun scheduleLevelCheck(at: Instant)

    fun cancelLevelCheck()
}

/** Tells the user a phase ran out (notification, sound, vibration). */
interface PhaseAlerts {
    fun phaseEnded(
        ended: Phase,
        next: Phase,
    )

    /** Removes a phase-end alert that is still showing. */
    fun clear()
}
