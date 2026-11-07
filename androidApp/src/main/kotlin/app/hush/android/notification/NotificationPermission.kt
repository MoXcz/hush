// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.notification

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import app.hush.android.setup.ForegroundActivity
import java.util.concurrent.atomic.AtomicInteger

/** Asks for POST_NOTIFICATIONS the first time a timer starts (not at launch), once per process. */
class NotificationPermission(
    private val context: Context,
    private val foreground: ForegroundActivity,
) {
    private var asked = false
    private val keys = AtomicInteger()

    /** Main thread only. */
    fun requestIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || asked || TimerNotifications.canPost(context)) return
        val registry = foreground.activityResultRegistry ?: return
        asked = true
        var launcher: ActivityResultLauncher<String>? = null
        launcher =
            registry.register("notification-permission-${keys.incrementAndGet()}", ActivityResultContracts.RequestPermission()) {
                launcher?.unregister()
            }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
