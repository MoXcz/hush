// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.hush.core.ApplicationScope
import app.hush.detox.DetoxController
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Detox level wakeup, and re-arming it after a reboot. The decision is always
 * DetoxController.check(): timestamps first, then the blocker's session state.
 */
open class LevelCheckReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val controller: DetoxController by inject()
    private val scope: ApplicationScope by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        scope.launch {
            try {
                controller.check()
            } finally {
                pending.finish()
            }
        }
    }
}

/** Disabled in the manifest; AlarmWakeupScheduler enables it only while an attempt is open. */
class BootReceiver : LevelCheckReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        super.onReceive(context, intent)
    }
}
