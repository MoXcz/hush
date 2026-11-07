// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.stats

import app.hush.history.Session
import app.hush.history.SessionOutcome
import app.hush.history.SessionType
import app.hush.tags.TagId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Focus time in one day or week, from [start] (a local date), split by tag (null: no tag). */
data class FocusBucket(
    val start: LocalDate,
    val byTag: Map<TagId?, Duration> = emptyMap(),
) {
    val total: Duration get() = byTag.values.fold(Duration.ZERO, Duration::plus)
}

data class DetoxStats(
    /** Planned minutes of the longest passed level, or null if none passed yet. */
    val longestPassedMinutes: Int? = null,
    val attempts: Int = 0,
    val passed: Int = 0,
)

/** Pure aggregation of finished sessions. Days and weeks are local to [TimeZone]; weeks start on Monday. */
object FocusStats {
    /**
     * Time a finished work phase counts for: its planned length if it ran out, or the time it ran
     * (start to end, capped at the planned length) if it was ended early.
     */
    fun focusTime(session: Session): Duration {
        if (session.type != SessionType.Work) return Duration.ZERO
        val planned = session.plannedMinutes.minutes
        val endedAt = session.endedAt ?: return Duration.ZERO
        return when (session.outcome) {
            SessionOutcome.Completed -> planned
            SessionOutcome.Stopped, SessionOutcome.Abandoned -> (endedAt - session.startedAt).coerceIn(Duration.ZERO, planned)
            null -> Duration.ZERO
        }
    }

    /** The last [count] days up to and including today, oldest first. Sessions count on the day they started. */
    fun days(
        sessions: List<Session>,
        now: Instant,
        zone: TimeZone,
        count: Int = 7,
    ): List<FocusBucket> {
        val today = now.localDate(zone)
        val starts = (count - 1 downTo 0).map { today.minus(it, DateTimeUnit.DAY) }
        return buckets(sessions, starts, zone) { it }
    }

    /** The last [count] weeks up to and including this one, oldest first, each starting on Monday. */
    fun weeks(
        sessions: List<Session>,
        now: Instant,
        zone: TimeZone,
        count: Int = 8,
    ): List<FocusBucket> {
        val thisWeek = weekStart(now.localDate(zone))
        val starts = (count - 1 downTo 0).map { thisWeek.minus(it * 7, DateTimeUnit.DAY) }
        return buckets(sessions, starts, zone, ::weekStart)
    }

    fun detox(sessions: List<Session>): DetoxStats {
        val attempts = sessions.filter { it.type == SessionType.Detox && it.outcome != null }
        val passed = attempts.filter { it.outcome == SessionOutcome.Completed }
        return DetoxStats(passed.maxOfOrNull { it.plannedMinutes }, attempts.size, passed.size)
    }

    fun weekStart(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

    private fun buckets(
        sessions: List<Session>,
        starts: List<LocalDate>,
        zone: TimeZone,
        bucketOf: (LocalDate) -> LocalDate,
    ): List<FocusBucket> {
        val totals = starts.associateWith { mutableMapOf<TagId?, Duration>() }
        for (session in sessions) {
            val time = focusTime(session)
            if (time <= Duration.ZERO) continue
            val bucket = totals[bucketOf(session.startedAt.localDate(zone))] ?: continue
            bucket[session.tagId] = (bucket[session.tagId] ?: Duration.ZERO) + time
        }
        return starts.map { FocusBucket(it, totals.getValue(it).toMap()) }
    }

    private fun Instant.localDate(zone: TimeZone): LocalDate = toLocalDateTime(zone).date
}

/** Total time per tag over [buckets], largest first. */
fun List<FocusBucket>.totalsByTag(): List<Pair<TagId?, Duration>> =
    flatMap { it.byTag.entries }
        .groupBy({ it.key }, { it.value })
        .mapValues { (_, times) -> times.fold(Duration.ZERO, Duration::plus) }
        .entries
        .sortedByDescending { it.value }
        .map { it.key to it.value }
