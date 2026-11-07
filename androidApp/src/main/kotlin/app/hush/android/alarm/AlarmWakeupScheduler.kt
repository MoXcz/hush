// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.getSystemService
import app.hush.timer.WakeupScheduler
import kotlin.time.Instant

/**
 * Exact when allowed (the user waits for the phase-end sound), inexact otherwise. One
 * PendingIntent per purpose, so scheduling again replaces the earlier alarm.
 */
class AlarmWakeupScheduler(
    private val context: Context,
) : WakeupScheduler {
    private val alarms: AlarmManager? get() = context.getSystemService()

    override fun schedulePhaseEnd(at: Instant) = scheduleExact(at, phaseEndIntent())

    override fun cancelPhaseEnd() {
        alarms?.cancel(phaseEndIntent())
    }

    override fun scheduleLevelCheck(at: Instant) {
        setBootReceiverEnabled(true)
        // Inexact is enough: pass or fail is decided by timestamps, a late alarm only delays the notice.
        alarms?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilliseconds(), levelCheckIntent())
    }

    override fun cancelLevelCheck() {
        alarms?.cancel(levelCheckIntent())
        setBootReceiverEnabled(false)
    }

    private fun scheduleExact(
        at: Instant,
        intent: PendingIntent,
    ) {
        val alarms = alarms ?: return
        val millis = at.toEpochMilliseconds()
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    // Alarms don't survive a reboot; the boot receiver re-arms the level check, and only runs
    // while an attempt is open (AGENTS.md §10).
    private fun setBootReceiverEnabled(enabled: Boolean) {
        val state =
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, BootReceiver::class.java),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun phaseEndIntent(): PendingIntent = broadcast(PHASE_END_REQUEST, PhaseEndReceiver::class.java)

    private fun levelCheckIntent(): PendingIntent = broadcast(LEVEL_CHECK_REQUEST, LevelCheckReceiver::class.java)

    private fun broadcast(
        requestCode: Int,
        receiver: Class<*>,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, receiver),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val PHASE_END_REQUEST = 0
        const val LEVEL_CHECK_REQUEST = 1
    }
}
