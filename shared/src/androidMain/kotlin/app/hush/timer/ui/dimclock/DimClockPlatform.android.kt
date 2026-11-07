// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer.ui.dimclock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Date
import kotlin.time.Instant

private const val DIM_BRIGHTNESS = 0.05f

@Composable
actual fun DimClockWindowEffect(showWhenLocked: Boolean) {
    val view = LocalView.current
    val activity = view.context.findActivity() ?: return
    DisposableEffect(activity, showWhenLocked) {
        val window = activity.window
        val insets = WindowCompat.getInsetsController(window, view)
        val previousBrightness = window.attributes.screenBrightness
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = DIM_BRIGHTNESS }
        insets.hide(WindowInsetsCompat.Type.systemBars())
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (showWhenLocked) activity.showWhenLocked(true)
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = previousBrightness }
            insets.show(WindowInsetsCompat.Type.systemBars())
            if (showWhenLocked) activity.showWhenLocked(false)
        }
    }
}

@Composable
actual fun formatTimeOfDay(instant: Instant): String =
    DateFormat.getTimeFormat(LocalContext.current).format(Date(instant.toEpochMilliseconds()))

private fun Activity.showWhenLocked(show: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setShowWhenLocked(show)
    } else {
        @Suppress("DEPRECATION")
        val flag = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        if (show) window.addFlags(flag) else window.clearFlags(flag)
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
