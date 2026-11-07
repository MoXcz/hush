// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.hush.core.ApplicationScope
import app.hush.timer.TimerController
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** The alarm only wakes us; whether the phase ended is decided by expireIfDue() from timestamps. */
class PhaseEndReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val controller: TimerController by inject()
    private val scope: ApplicationScope by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        scope.launch {
            try {
                controller.expireIfDue()
            } finally {
                pending.finish()
            }
        }
    }
}
