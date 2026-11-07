// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.stats

import app.hush.activities.Activity
import app.hush.activities.ActivityId
import app.hush.history.Session
import app.hush.history.SessionId
import app.hush.history.SessionOutcome
import app.hush.history.SessionType
import app.hush.rules.ActivityRule
import app.hush.tags.defaultTags
import app.hush.usage.SessionApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FocusByActivityTest {
    private val start = Instant.parse("2026-11-05T09:00:00Z")
    private val kobo = Activity(ActivityId(1), "Reading on Kobo")
    private val study = Activity(ActivityId(2), "Study")
    private val leisure = Activity(ActivityId(3), "Leisure")
    private val activities = listOf(kobo, study, leisure).associateBy { it.id }

    private fun phase(
        uid: String,
        minutes: Int = 50,
        activity: Activity? = null,
        startedAt: Instant = start,
        type: SessionType = SessionType.Work,
    ) = Session(
        SessionId(uid.hashCode().toLong()),
        type,
        null,
        minutes,
        startedAt,
        startedAt + minutes.minutes,
        SessionOutcome.Completed,
        defaultTags[0].id,
        activity?.id,
        uid,
    )

    private fun app(
        session: String,
        key: String,
        minutes: Int,
    ) = SessionApp(session, key, key, minutes.minutes, start)

    private fun rule(
        key: String,
        activity: Activity?,
    ) = ActivityRule(key, key, activity?.id)

    private fun sort(
        sessions: List<Session>,
        apps: List<SessionApp> = emptyList(),
        rules: List<ActivityRule> = emptyList(),
        since: Instant = start - 1.hours,
    ) = FocusByActivity.since(since, sessions, apps, rules, activities)

    @Test
    fun `a phase with a picked activity counts in full for it, not its apps`() {
        val lines =
            sort(
                sessions = listOf(phase("s", minutes = 50, activity = kobo)),
                apps = listOf(app("s", "dictionary", 5), app("s", "anki", 10)),
                rules = listOf(rule("anki", study)),
            )
        assertEquals(listOf(SortedTime(kobo, 50.minutes)), lines)
    }

    @Test
    fun `without an activity each app counts by its own rule and the rest of the apps are unsorted`() {
        val lines =
            sort(
                sessions = listOf(phase("s", minutes = 50)),
                apps = listOf(app("s", "anki", 20), app("s", "web:youtube", 5), app("s", "maps", 3)),
                rules = listOf(rule("anki", study), rule("web:youtube", leisure)),
            )
        assertEquals(listOf(SortedTime(study, 20.minutes), SortedTime(leisure, 5.minutes), SortedTime(null, 3.minutes)), lines)
    }

    @Test
    fun `apps from two devices are capped at the phase's focus time`() {
        val lines =
            sort(
                sessions = listOf(phase("s", minutes = 50)),
                apps = listOf(app("s", "anki", 40), app("s", "web:youtube", 40), app("s", "maps", 20)),
                rules = listOf(rule("anki", study), rule("web:youtube", leisure)),
            )
        assertEquals(listOf(SortedTime(study, 20.minutes), SortedTime(leisure, 20.minutes), SortedTime(null, 10.minutes)), lines)
    }

    @Test
    fun `a phase with no apps and no activity adds nothing`() {
        assertEquals(emptyList(), sort(sessions = listOf(phase("s"))))
    }

    @Test
    fun `a rule pointing at a deleted or unknown activity sorts nothing`() {
        val deleted = activities + (study.id to study.copy(deleted = true))
        val lines =
            FocusByActivity.since(
                start - 1.hours,
                listOf(phase("s")),
                listOf(app("s", "anki", 10), app("s", "maps", 5)),
                listOf(rule("anki", study), rule("maps", null)),
                deleted,
            )
        assertEquals(listOf(SortedTime(null, 15.minutes)), lines)
    }

    @Test
    fun `phases before the range, breaks and detox attempts are left out`() {
        val lines =
            sort(
                sessions =
                    listOf(
                        phase("old", startedAt = start - 2.hours, activity = kobo),
                        phase("detox", type = SessionType.Detox, activity = kobo),
                        phase("now", minutes = 25, activity = kobo),
                    ),
            )
        assertEquals(listOf(SortedTime(kobo, 25.minutes)), lines)
    }

    @Test
    fun `activities are largest first across phases and unsorted comes last`() {
        val lines =
            sort(
                sessions = listOf(phase("a"), phase("b"), phase("c", minutes = 30, activity = kobo)),
                apps = listOf(app("a", "anki", 10), app("b", "anki", 15), app("a", "notes", 40), app("b", "maps", 1)),
                rules = listOf(rule("anki", leisure), rule("notes", study)),
            )
        assertEquals(
            listOf(
                SortedTime(study, 40.minutes),
                SortedTime(kobo, 30.minutes),
                SortedTime(leisure, 25.minutes),
                SortedTime(null, 1.minutes),
            ),
            lines,
        )
    }

    @Test
    fun `bars are relative to the largest line`() {
        assertEquals(
            listOf(1f, 0.5f, 0.25f),
            FocusByActivity.bars(listOf(SortedTime(study, 40.minutes), SortedTime(leisure, 20.minutes), SortedTime(null, 10.minutes))),
        )
        assertEquals(listOf(0f), FocusByActivity.bars(listOf(SortedTime(null, Duration.ZERO))))
    }
}
