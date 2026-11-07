// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import app.hush.android.notification.TimerNotifications
import app.hush.core.AppDispatchers
import app.hush.timer.Phase
import app.hush.timer.RunState
import app.hush.timer.TimerController
import app.hush.timer.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.time.Instant

/**
 * Keeps the process alive while a Pomodoro phase runs and shows it as one ongoing notification.
 * It only renders [TimerController.state] and stops itself once the timer isn't running.
 */
class TimerService : Service() {
    private val controller: TimerController by inject()
    private val dispatchers: AppDispatchers by inject()
    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchers.default) }
    private var rendering: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Must be foreground within seconds of startForegroundService(), even if about to stop.
        val content = controller.state.value.content()
        ServiceCompat.startForeground(
            this,
            TimerNotifications.ONGOING_ID,
            TimerNotifications.ongoing(this, content.phase, content.endsAt),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (rendering == null) {
            rendering =
                scope.launch {
                    controller.state
                        .map { it.content() }
                        .distinctUntilChanged()
                        .collect { render(it) }
                }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(content: Content) {
        if (content.endsAt == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (TimerNotifications.canPost(this)) {
            try {
                NotificationManagerCompat
                    .from(this)
                    .notify(TimerNotifications.ONGOING_ID, TimerNotifications.ongoing(this, content.phase, content.endsAt))
            } catch (e: SecurityException) {
                // Permission revoked between the check and the call; the foreground notice stays as it was.
            }
        }
    }

    private data class Content(
        val phase: Phase,
        val endsAt: Instant?,
    )

    private fun TimerState.content() = Content(phase, (snapshot.run as? RunState.Running)?.endsAt)
}
