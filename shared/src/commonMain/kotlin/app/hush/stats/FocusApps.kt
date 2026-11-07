// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.stats

import app.hush.activities.Activity
import app.hush.activities.ActivityId
import app.hush.history.Session
import app.hush.usage.SessionApp
import kotlin.time.Duration
import kotlin.time.Instant

// A line in "Apps during focus": an app on the phone, an activity the user picked, or the rest summed.
sealed interface FocusUse {
    val time: Duration

    data class App(
        val packageName: String,
        val label: String,
        override val time: Duration,
    ) : FocusUse

    data class Doing(
        val activity: Activity,
        override val time: Duration,
    ) : FocusUse

    data class Other(
        override val time: Duration,
    ) : FocusUse
}

// Apps and activities side by side; an activity counts its phase's focus time, an app its own time in front.
object FocusApps {
    const val TOP = 8

    /** Apps and activities of work phases started at or after [since], largest first, the rest past [top] summed. */
    fun since(
        since: Instant,
        sessions: List<Session>,
        apps: List<SessionApp>,
        activities: Map<ActivityId, Activity>,
        top: Int = TOP,
    ): List<FocusUse> {
        val byApp =
            apps
                .filter { it.sessionStartedAt >= since }
                .groupBy { it.packageName }
                .map { (pkg, rows) ->
                    // The newest label, in case the app was renamed.
                    val label = rows.maxBy { it.sessionStartedAt }.label
                    FocusUse.App(pkg, label, rows.fold(Duration.ZERO) { sum, it -> sum + it.foreground })
                }
        val byActivity =
            sessions
                .filter { it.activityId != null && it.startedAt >= since }
                .groupBy { it.activityId }
                .mapNotNull { (id, rows) ->
                    val activity = activities[id] ?: return@mapNotNull null
                    FocusUse.Doing(activity, rows.fold(Duration.ZERO) { sum, it -> sum + FocusStats.focusTime(it) })
                }
        val all = (byApp + byActivity).filter { it.time > Duration.ZERO }.sortedByDescending { it.time }
        if (all.size <= top) return all
        val rest = all.drop(top).fold(Duration.ZERO) { sum, it -> sum + it.time }
        return all.take(top) + FocusUse.Other(rest)
    }

    // An activity's time overlaps the apps used in the same phase, so each kind is scaled to its own
    // largest line; the rest is mostly apps, so it is scaled with them (capped at a full bar).
    fun bars(uses: List<FocusUse>): List<Float> {
        val maxApp = uses.filterIsInstance<FocusUse.App>().maxOfOrNull { it.time } ?: Duration.ZERO
        val maxDoing = uses.filterIsInstance<FocusUse.Doing>().maxOfOrNull { it.time } ?: Duration.ZERO
        return uses.map { use ->
            val max = if (use is FocusUse.Doing) maxDoing else maxApp
            if (max > Duration.ZERO) (use.time / max).toFloat().coerceAtMost(1f) else 0f
        }
    }
}
