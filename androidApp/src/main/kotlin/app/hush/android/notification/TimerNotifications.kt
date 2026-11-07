// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.notification

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.hush.android.MainActivity
import app.hush.android.R
import app.hush.detox.LevelAlerts
import app.hush.timer.Phase
import app.hush.timer.PhaseAlerts
import kotlin.time.Instant

/** The two channels the guide allows: the ongoing timer (low) and the phase/level end (high). */
object TimerNotifications {
    const val TIMER_CHANNEL = "timer"
    const val PHASE_END_CHANNEL = "phase_end"
    const val ONGOING_ID = 1
    const val PHASE_END_ID = 2
    const val LEVEL_END_ID = 3

    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat
                    .Builder(TIMER_CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.channel_timer))
                    .setDescription(context.getString(R.string.channel_timer_description))
                    .setShowBadge(false)
                    .build(),
                // Default sound, plus vibration: the phase end has to be noticed with the screen off.
                NotificationChannelCompat
                    .Builder(PHASE_END_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.channel_phase_end))
                    .setDescription(context.getString(R.string.channel_phase_end_description))
                    .setVibrationEnabled(true)
                    .build(),
            ),
        )
    }

    /** The countdown is a system chronometer, so the notification only changes with the phase. */
    fun ongoing(
        context: Context,
        phase: Phase,
        endsAt: Instant?,
    ): Notification =
        NotificationCompat
            .Builder(context, TIMER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(phase.label()))
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (endsAt != null) {
                    setWhen(endsAt.toEpochMilliseconds())
                    setShowWhen(true)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                }
            }.build()

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    internal fun phaseEnd(
        context: Context,
        ended: Phase,
        next: Phase,
    ): Notification =
        NotificationCompat
            .Builder(context, PHASE_END_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(if (ended == Phase.Work) R.string.phase_end_work else R.string.phase_end_break))
            .setContentText(context.getString(R.string.phase_end_next, context.getString(next.label())))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

    internal fun levelPassed(
        context: Context,
        advanced: Boolean,
    ): Notification =
        NotificationCompat
            .Builder(context, PHASE_END_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.level_passed))
            .setContentText(context.getString(if (advanced) R.string.level_passed_text else R.string.level_passed_again_text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

    internal fun notify(
        context: Context,
        id: Int,
        notification: Notification,
    ) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    private fun Phase.label(): Int =
        when (this) {
            Phase.Work -> R.string.phase_work
            Phase.ShortBreak -> R.string.phase_short_break
            Phase.LongBreak -> R.string.phase_long_break
        }
}

class NotificationPhaseAlerts(
    private val context: Context,
) : PhaseAlerts {
    override fun phaseEnded(
        ended: Phase,
        next: Phase,
    ) {
        TimerNotifications.notify(context, TimerNotifications.PHASE_END_ID, TimerNotifications.phaseEnd(context, ended, next))
    }

    override fun clear() {
        NotificationManagerCompat.from(context).cancel(TimerNotifications.PHASE_END_ID)
    }
}

class NotificationLevelAlerts(
    private val context: Context,
) : LevelAlerts {
    override fun levelPassed(
        levelMinutes: Int,
        advanced: Boolean,
    ) {
        TimerNotifications.notify(context, TimerNotifications.LEVEL_END_ID, TimerNotifications.levelPassed(context, advanced))
    }

    override fun clear() {
        NotificationManagerCompat.from(context).cancel(TimerNotifications.LEVEL_END_ID)
    }
}
