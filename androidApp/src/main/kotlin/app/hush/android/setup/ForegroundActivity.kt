// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.setup

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import java.lang.ref.WeakReference

/**
 * Tracks the resumed Activity, so requests that must be launched for result from an Activity
 * (the notification permission) can be started from outside one.
 */
class ForegroundActivity : Application.ActivityLifecycleCallbacks {
    private var current: WeakReference<ComponentActivity>? = null

    val activityResultRegistry: ActivityResultRegistry?
        get() = current?.get()?.activityResultRegistry

    override fun onActivityResumed(activity: Activity) {
        if (activity is ComponentActivity) current = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
