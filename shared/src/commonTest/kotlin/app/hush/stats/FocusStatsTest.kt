// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.stats

import app.hush.blocker.ProfileId
import app.hush.history.Session
import app.hush.history.SessionId
import app.hush.history.SessionOutcome
import app.hush.history.SessionType
import app.hush.tags.TagId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FocusStatsTest {
    private val focus = TagId(1)
    private val deep = TagId(3)
    private val utc = TimeZone.UTC
    private val mexicoCity = TimeZone.of("America/Mexico_City") // UTC-6, no DST since 2022

    // Thursday 2026-11-05.
    private val now = Instant.parse("2026-11-05T20:00:00Z")
    private var nextId = 1L

    private fun work(
        startedAt: String,
        minutes: Int,
        tag: TagId? = focus,
        outcome: SessionOutcome = SessionOutcome.Completed,
        ranFor: Duration = minutes.minutes,
    ): Session {
        val start = Instant.parse(startedAt)
        return Session(SessionId(nextId++), SessionType.Work, null, minutes, start, start + ranFor, outcome, tag)
    }

    private fun detox(
        minutes: Int,
        outcome: SessionOutcome?,
    ): Session {
        val start = Instant.parse("2026-11-01T10:00:00Z")
        val end = if (outcome == null) null else start + minutes.minutes
        return Session(SessionId(nextId++), SessionType.Detox, ProfileId("detox"), minutes, start, end, outcome)
    }

    @Test
    fun `focus time per day is split by tag over the last 7 days`() {
        val sessions =
            listOf(
                work("2026-11-05T09:00:00Z", 25),
                work("2026-11-05T10:00:00Z", 50, tag = deep),
                work("2026-11-05T11:00:00Z", 25),
                work("2026-11-03T09:00:00Z", 25),
                work("2026-10-29T09:00:00Z", 25), // 8 days ago: outside the window
            )
        val days = FocusStats.days(sessions, now, utc)
        assertEquals(7, days.size)
        assertEquals(LocalDate(2026, 10, 30), days.first().start)
        assertEquals(LocalDate(2026, 11, 5), days.last().start)
        assertEquals(mapOf<TagId?, Duration>(focus to 50.minutes, deep to 50.minutes), days.last().byTag)
        assertEquals(100.minutes, days.last().total)
        assertEquals(25.minutes, days[4].total)
        assertEquals(125.minutes, days.fold(Duration.ZERO) { sum, it -> sum + it.total })
    }

    @Test
    fun `a work phase ended early counts the time it ran, capped at its length`() {
        val stopped = work("2026-11-05T09:00:00Z", 25, outcome = SessionOutcome.Stopped, ranFor = 10.minutes)
        // Paused for an hour before being skipped: the wall time is longer than the phase.
        val paused = work("2026-11-05T10:00:00Z", 25, outcome = SessionOutcome.Stopped, ranFor = 2.hours)
        assertEquals(10.minutes, FocusStats.focusTime(stopped))
        assertEquals(25.minutes, FocusStats.focusTime(paused))
        assertEquals(35.minutes, FocusStats.days(listOf(stopped, paused), now, utc).last().total)
    }

    @Test
    fun `detox attempts never count as focus time`() {
        assertEquals(Duration.ZERO, FocusStats.focusTime(detox(60, SessionOutcome.Completed)))
    }

    @Test
    fun `weeks start on Monday and there are 8 of them, oldest first`() {
        val sessions =
            listOf(
                work("2026-11-02T08:00:00Z", 25), // Monday this week
                work("2026-11-01T08:00:00Z", 50, tag = deep), // Sunday last week
                work("2026-09-14T08:00:00Z", 25), // Monday 7 weeks ago: the oldest week shown
                work("2026-09-13T08:00:00Z", 25), // just before it
            )
        val weeks = FocusStats.weeks(sessions, now, utc)
        assertEquals(8, weeks.size)
        assertEquals(LocalDate(2026, 9, 14), weeks.first().start)
        assertEquals(LocalDate(2026, 11, 2), weeks.last().start)
        assertEquals(mapOf<TagId?, Duration>(focus to 25.minutes), weeks.last().byTag)
        assertEquals(mapOf<TagId?, Duration>(deep to 50.minutes), weeks[6].byTag)
        assertEquals(25.minutes, weeks.first().total)
    }

    @Test
    fun `days and weeks follow the local time zone, not UTC`() {
        // 03:00 UTC on Monday is still Sunday evening in Mexico City.
        val lateSunday = work("2026-11-02T03:00:00Z", 25)
        val utcWeeks = FocusStats.weeks(listOf(lateSunday), now, utc)
        val localWeeks = FocusStats.weeks(listOf(lateSunday), now, mexicoCity)
        assertEquals(25.minutes, utcWeeks.last().total)
        assertEquals(Duration.ZERO, localWeeks.last().total)
        assertEquals(25.minutes, localWeeks[6].total)
        assertEquals(LocalDate(2026, 11, 1), FocusStats.days(listOf(lateSunday), now, mexicoCity).single { it.total > Duration.ZERO }.start)
    }

    @Test
    fun `untagged work is its own group, and totals are sorted largest first`() {
        val days =
            FocusStats.days(
                listOf(work("2026-11-05T09:00:00Z", 25, tag = null), work("2026-11-04T09:00:00Z", 50, tag = deep)),
                now,
                utc,
            )
        assertEquals(listOf(deep to 50.minutes, null to 25.minutes), days.totalsByTag())
    }

    @Test
    fun `detox progress is the highest passed level and the finished attempts`() {
        val stats =
            FocusStats.detox(
                listOf(
                    detox(60, SessionOutcome.Completed),
                    detox(90, SessionOutcome.Completed),
                    detox(120, SessionOutcome.Abandoned),
                    detox(120, SessionOutcome.Stopped),
                    detox(120, null), // still running
                    work("2026-11-05T09:00:00Z", 25),
                ),
            )
        assertEquals(DetoxStats(longestPassedMinutes = 90, attempts = 4, passed = 2), stats)
        assertEquals(DetoxStats(), FocusStats.detox(emptyList()))
    }
}
