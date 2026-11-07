// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.hush.android.notification.NotificationPermission
import app.hush.core.ApplicationScope
import app.hush.timer.TimerController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Starts [TimerService] whenever the timer starts running; the service stops itself. */
class TimerServiceStarter(
    private val context: Context,
    private val controller: TimerController,
    private val scope: ApplicationScope,
    private val notificationPermission: NotificationPermission,
) {
    fun start() {
        scope.launch {
            controller.state
                .map { it.isRunning }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    ContextCompat.getMainExecutor(context).execute { notificationPermission.requestIfNeeded() }
                    try {
                        ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
                    } catch (e: IllegalStateException) {
                        // Not allowed from the background (Android 12+). The phase-end alarm still fires.
                    }
                }
        }
    }
}
