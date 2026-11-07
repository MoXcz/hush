// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.sync

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.hush.android.setup.ForegroundActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

class LocalNetworkPermission(
    private val context: Context,
    private val foreground: ForegroundActivity,
) {
    private val keys = AtomicInteger()

    val granted: Boolean
        get() =
            Build.VERSION.SDK_INT < API_37 ||
                ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    // Times out because an answer given after the activity is recreated never reaches this call.
    suspend fun request(): Boolean {
        if (granted) return true
        val answer =
            withContext(Dispatchers.Main.immediate) {
                val registry = foreground.activityResultRegistry ?: return@withContext false
                withTimeoutOrNull(ANSWER_TIMEOUT) {
                    suspendCancellableCoroutine { continuation ->
                        var launcher: ActivityResultLauncher<String>? = null
                        launcher =
                            registry.register(
                                "local-network-${keys.incrementAndGet()}",
                                ActivityResultContracts.RequestPermission(),
                            ) { ok ->
                                launcher?.unregister()
                                if (continuation.isActive) continuation.resume(ok)
                            }
                        // Covers the timeout and a closed screen too.
                        continuation.invokeOnCancellation { launcher.unregister() }
                        try {
                            launcher.launch(ACCESS_LOCAL_NETWORK)
                        } catch (_: ActivityNotFoundException) {
                            launcher.unregister()
                            continuation.resume(false)
                        }
                    }
                }
            }
        // The user may have answered in a recreated activity: the system knows.
        return answer ?: granted
    }

    private companion object {
        const val API_37 = 37
        val ANSWER_TIMEOUT = 60.seconds

        // Manifest.permission.ACCESS_LOCAL_NETWORK (API 37); a string so older code paths don't inline it.
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
