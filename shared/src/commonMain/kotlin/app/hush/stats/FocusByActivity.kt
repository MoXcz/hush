// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.stats

import app.hush.activities.Activity
import app.hush.activities.ActivityId
import app.hush.history.Session
import app.hush.history.SessionType
import app.hush.rules.ActivityRule
import app.hush.usage.SessionApp
import kotlin.time.Duration
import kotlin.time.Instant

// activity null: unsorted time.
data class SortedTime(
    val activity: Activity?,
    val time: Duration,
)

// App time and a picked activity overlap inside a work phase, so each phase is sorted once: a phase
// with an activity counts in full for it (the user said what they were doing); otherwise each of its
// apps counts by its own rule and the rest of its app time is unsorted. A rule whose activity is
// deleted or missing sorts nothing.
object FocusByActivity {
    fun since(
        since: Instant,
        sessions: List<Session>,
        apps: List<SessionApp>,
        rules: List<ActivityRule>,
        activities: Map<ActivityId, Activity>,
    ): List<SortedTime> {
        val appRules =
            rules
                .mapNotNull { rule ->
                    rule.activityId
                        ?.let(activities::get)
                        ?.takeIf { !it.deleted }
                        ?.let { rule.appKey to it }
                }.toMap()
        val appsBySession = apps.groupBy { it.sessionUid }
        val totals = mutableMapOf<ActivityId?, Duration>()

        fun add(
            activity: Activity?,
            time: Duration,
        ) {
            if (time > Duration.ZERO) totals[activity?.id] = (totals[activity?.id] ?: Duration.ZERO) + time
        }

        for (session in sessions) {
            if (session.type != SessionType.Work || session.startedAt < since) continue
            val focus = FocusStats.focusTime(session)
            val picked = session.activityId?.let(activities::get)
            if (picked != null) {
                add(picked, focus)
                continue
            }
            val phaseApps = if (session.uid.isEmpty()) emptyList() else appsBySession[session.uid].orEmpty()
            val appTime = phaseApps.fold(Duration.ZERO) { sum, it -> sum + it.foreground }
            // Phone and desktop rows of a synced phase can add up to more than the phase itself.
            val scale = if (appTime > focus) focus / appTime else 1.0
            phaseApps.forEach { add(appRules[it.packageName], it.foreground * scale) }
        }
        val sorted =
            totals
                .filterKeys { it != null }
                .entries
                .sortedByDescending { it.value }
                .map { (id, time) -> SortedTime(activities.getValue(id!!), time) }
        return sorted + listOfNotNull(totals[null]?.let { SortedTime(null, it) })
    }

    fun bars(lines: List<SortedTime>): List<Float> {
        val max = lines.maxOfOrNull { it.time } ?: Duration.ZERO
        return lines.map { if (max > Duration.ZERO) (it.time / max).toFloat().coerceAtMost(1f) else 0f }
    }
}
