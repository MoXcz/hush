// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SyncMergeTest {
    private val now = Instant.fromEpochMilliseconds(1_000_000)

    @Test
    fun `sessions are a union by uid and an existing uid is never overwritten`() {
        val local = LocalSyncState(emptyList(), setOf("s1"))
        val plan = SyncMerge.plan(local, payload(sessions = listOf(session("s1", plannedMinutes = 90), session("s2"))), "a", now)
        assertEquals(listOf("s2"), plan.sessionInserts.map { it.uid })
    }

    @Test
    fun `an incoming session without a valid end is dropped`() {
        val plan = SyncMerge.plan(state(), payload(sessions = listOf(session("open", endedAt = null), session("ok"))), "a", now)
        assertEquals(listOf("ok"), plan.sessionInserts.map { it.uid })
        assertEquals(1, plan.dropped)
    }

    @Test
    fun `tags are last writer wins by updatedAt then device`() {
        val local = state(tag("t", name = "Local", updatedAt = 10, updatedBy = "b"))
        val newer = SyncMerge.plan(local, payload(tag("t", name = "Remote", updatedAt = 11, updatedBy = "a")), "a", now)
        assertEquals(listOf("Remote"), newer.tagUpdates.map { it.record.name })
        val tieBrokenByDevice = SyncMerge.plan(local, payload(tag("t", name = "Remote", updatedAt = 10, updatedBy = "c")), "a", now)
        assertEquals(listOf("Remote"), tieBrokenByDevice.tagUpdates.map { it.record.name })
        val older = SyncMerge.plan(local, payload(tag("t", name = "Remote", updatedAt = 10, updatedBy = "a")), "a", now)
        assertTrue(older.tagUpdates.isEmpty())
        val equal = SyncMerge.plan(local, payload(tag("t", name = "Remote", updatedAt = 10, updatedBy = "b")), "a", now)
        assertTrue(equal.tagUpdates.isEmpty())
    }

    @Test
    fun `a newer delete hides the tag and a later edit elsewhere revives it`() {
        val local = state(tag("t", updatedAt = 10), tag("other"))
        val deleted = SyncMerge.plan(local, payload(tag("t", deleted = true, updatedAt = 20)), "a", now)
        assertEquals(listOf(true), deleted.tagUpdates.map { it.record.deleted })

        val afterDelete = state(tag("t", deleted = true, updatedAt = 20), tag("other"))
        val revived = SyncMerge.plan(afterDelete, payload(tag("t", name = "Edited", updatedAt = 30)), "a", now)
        assertEquals(listOf(false), revived.tagUpdates.map { it.record.deleted })
    }

    @Test
    fun `tags come in before sessions and an unknown tag uid becomes no tag`() {
        val plan =
            SyncMerge.plan(
                state(tag("known")),
                payload(
                    tag("new"),
                    sessions = listOf(session("s1", tag = "known"), session("s2", tag = "new"), session("s3", tag = "gone")),
                ),
                "a",
                now,
            )
        assertEquals(listOf("new"), plan.tagInserts.map { it.uid })
        assertEquals(listOf("known", "new", null), plan.sessionInserts.map { it.tag })
    }

    @Test
    fun `a merge never leaves zero active tags`() {
        val local = state(tag("a", updatedAt = 5), tag("b", deleted = true, updatedAt = 1))
        val plan = SyncMerge.plan(local, payload(tag("a", deleted = true, updatedAt = 9, updatedBy = "remote")), "self", now)
        val revived = plan.tagUpdates.single()
        assertEquals("a", revived.record.uid)
        assertEquals(false, revived.record.deleted)
        assertEquals(now.toEpochMilliseconds(), revived.record.updatedAt)
        assertEquals("self", revived.record.updatedBy)
    }

    @Test
    fun `the selected tag keeps its local id when a sync changes it`() {
        val local = LocalSyncState(listOf(LocalTag(7, tag("t", updatedAt = 1)), LocalTag(8, tag("other"))), emptySet())
        val plan = SyncMerge.plan(local, payload(tag("t", name = "Renamed", deleted = true, updatedAt = 2)), "a", now)
        assertEquals(listOf(7L), plan.tagUpdates.map { it.localId })
    }

    @Test
    fun `invalid records are dropped without failing the sync`() {
        val badTags =
            listOf(
                tag("blank", name = " "),
                tag("long", name = "x".repeat(25)),
                tag("work", work = 0),
                tag("short", shortBreak = 31),
                tag("longBreak", longBreak = 61),
                tag("after", longBreakAfter = 1),
            )
        val badSessions =
            listOf(
                session("type", type = "Nap"),
                session("outcome", outcome = "Maybe"),
                session("zero", plannedMinutes = 0),
                session("day", plannedMinutes = 1441),
                session("backwards", startedAt = 2_000, endedAt = 1_000),
            )
        val plan =
            SyncMerge.plan(
                state(),
                payload(*(badTags + tag("ok", color = "Violet")).toTypedArray(), sessions = badSessions + session("fine")),
                "a",
                now,
            )
        assertEquals(listOf("ok"), plan.tagInserts.map { it.uid })
        assertEquals(listOf("fine"), plan.sessionInserts.map { it.uid })
        assertEquals(badTags.size + badSessions.size, plan.dropped)
    }

    @Test
    fun `records with oversized strings are dropped`() {
        val long = "x".repeat(65)
        val badTags =
            listOf(
                tag(long),
                tag("color", color = "c".repeat(33)),
                tag("by", updatedBy = long),
            )
        val badSessions =
            listOf(
                session(long),
                session("profile", profile = long),
                session("tag", tag = long),
            )
        val okTags = listOf(tag("x".repeat(64), color = "c".repeat(32), updatedBy = "y".repeat(64)))
        val okSessions = listOf(session("y".repeat(64), profile = "p".repeat(64), tag = "x".repeat(64)))
        val plan = SyncMerge.plan(state(), payload(*(badTags + okTags).toTypedArray(), sessions = badSessions + okSessions), "a", now)
        assertEquals(okTags.map { it.uid }, plan.tagInserts.map { it.uid })
        assertEquals(okSessions.map { it.uid }, plan.sessionInserts.map { it.uid })
        assertEquals("x".repeat(64), plan.sessionInserts.single().tag)
        assertEquals(badTags.size + badSessions.size, plan.dropped)
    }

    @Test
    fun `applying the same payload twice changes nothing the second time`() {
        val device = Device("a", tag("seed"))
        val remote = payload(tag("seed", name = "Renamed", updatedAt = 5), tag("x"), sessions = listOf(session("s1", tag = "x")))
        device.apply(remote, now)
        val once = device.export()
        val second = device.apply(remote, now)
        assertEquals(MergeSummary(0, 0, 0), second)
        assertEquals(once, device.export())
    }

    @Test
    fun `syncing both ways leaves equal exports`() {
        val a = Device("a", tag("seed"), tag("onlyA", updatedAt = 3, updatedBy = "a"), sessions = listOf(session("sa", tag = "onlyA")))
        val b =
            Device(
                "b",
                tag("seed", name = "Edited on B", updatedAt = 7, updatedBy = "b"),
                tag("onlyB", deleted = true, updatedAt = 4, updatedBy = "b"),
                sessions = listOf(session("sb", tag = "seed")),
            )
        a.apply(b.export(), now)
        b.apply(a.export(), now)
        assertEquals(a.export().copy(device = ""), b.export().copy(device = ""))
        assertEquals(
            "Edited on B",
            a
                .export()
                .tags
                .first { it.uid == "seed" }
                .name,
        )
        assertNull(a.export().sessions.firstOrNull { it.uid == "missing" })
    }

    /** One device's synced state in memory, applying plans the way the Room store does. */
    private class Device(
        val id: String,
        vararg tags: TagRecord,
        sessions: List<SessionRecord> = emptyList(),
    ) {
        private val tags = tags.mapIndexed { i, t -> LocalTag(i + 1L, t) }.toMutableList()
        private val sessions = sessions.toMutableList()

        fun apply(
            remote: SyncPayload,
            now: Instant,
        ): MergeSummary {
            val plan = SyncMerge.plan(LocalSyncState(tags.toList(), sessions.map { it.uid }.toSet()), remote, id, now)
            plan.tagInserts.forEach { tags += LocalTag(tags.size + 1L, it) }
            plan.tagUpdates.forEach { update -> tags.replaceAll { if (it.localId == update.localId) update else it } }
            sessions += plan.sessionInserts
            return plan.summary
        }

        fun export() = SyncPayload(device = id, tags = tags.map { it.record }.sortedBy { it.uid }, sessions = sessions.sortedBy { it.uid })
    }

    @Test
    fun `activities are last writer wins and a delete travels as a tombstone`() {
        val local =
            LocalSyncState(emptyList(), emptySet(), activities = listOf(LocalActivity(5, activity("a", updatedAt = 10, updatedBy = "b"))))
        val renamed = SyncMerge.plan(local, payloadWith(activities = listOf(activity("a", name = "Kobo", updatedAt = 11))), "x", now)
        assertEquals(listOf(LocalActivity(5, activity("a", name = "Kobo", updatedAt = 11))), renamed.activityUpdates)
        val older = SyncMerge.plan(local, payloadWith(activities = listOf(activity("a", name = "Old", updatedAt = 9))), "x", now)
        assertTrue(older.activityUpdates.isEmpty())
        val deleted = SyncMerge.plan(local, payloadWith(activities = listOf(activity("a", deleted = true, updatedAt = 12))), "x", now)
        assertTrue(
            deleted.activityUpdates
                .single()
                .record.deleted,
        )
        val added = SyncMerge.plan(local, payloadWith(activities = listOf(activity("new"))), "x", now)
        assertEquals(listOf("new"), added.activityInserts.map { it.uid })
        assertEquals(1, added.summary.activitiesChanged)
    }

    @Test
    fun `a session keeps a known activity uid and drops an unknown one`() {
        val plan =
            SyncMerge.plan(
                state(),
                payloadWith(
                    sessions = listOf(session("s1", activity = "a"), session("s2", activity = "missing")),
                    activities = listOf(activity("a")),
                ),
                "x",
                now,
            )
        assertEquals(listOf("a", null), plan.sessionInserts.map { it.activity })
    }

    @Test
    fun `app rows are a union by session and package, only for sessions this database has`() {
        val local = LocalSyncState(emptyList(), setOf("old"), appKeys = setOf(AppKey("old", "org.notes")))
        val plan =
            SyncMerge.plan(
                local,
                payloadWith(
                    sessions = listOf(session("new")),
                    apps =
                        listOf(
                            app("old", "org.notes", millis = 999),
                            app("old", "org.maps"),
                            app("new"),
                            app("new"),
                            app("elsewhere"),
                        ),
                ),
                "x",
                now,
            )
        assertEquals(
            listOf(AppKey("old", "org.maps"), AppKey("new", "org.notes")),
            plan.appInserts.map { AppKey(it.session, it.packageName) },
        )
        assertEquals(2, plan.summary.appsAdded)
    }

    @Test
    fun `app rows and activities that fail validation are dropped`() {
        val plan =
            SyncMerge.plan(
                LocalSyncState(emptyList(), setOf("s")),
                payloadWith(
                    activities = listOf(activity(""), activity("long", name = "x".repeat(25)), activity("ok")),
                    apps =
                        listOf(
                            app("s", packageName = "p".repeat(256)),
                            app("s", packageName = "a", label = "l".repeat(101)),
                            app("s", packageName = "b", millis = 0),
                            app("s", packageName = "c", millis = 24 * 60 * 60 * 1000L + 1),
                            app("s", packageName = "ok", millis = 24 * 60 * 60 * 1000L),
                        ),
                ),
                "x",
                now,
            )
        assertEquals(listOf("ok"), plan.activityInserts.map { it.uid })
        assertEquals(listOf("ok"), plan.appInserts.map { it.packageName })
        assertEquals(6, plan.dropped)
    }

    @Test
    fun `a blank app label falls back to the package name`() {
        val plan =
            SyncMerge.plan(
                LocalSyncState(emptyList(), setOf("s")),
                payloadWith(apps = listOf(app("s", "org.notes", label = " "), app("s", "org.maps", label = "Maps"))),
                "x",
                now,
            )
        assertEquals(listOf("org.notes", "Maps"), plan.appInserts.map { it.label })
        assertEquals(0, plan.dropped)
    }

    @Test
    fun `rules are last writer wins by app and a cleared rule travels as a tombstone`() {
        val local =
            LocalSyncState(
                emptyList(),
                emptySet(),
                rules = listOf(rule("anki", tag = "study", updatedAt = 10), rule("web:youtube", tag = "rest", updatedAt = 10)),
            )
        val plan =
            SyncMerge.plan(
                local,
                payloadWith(
                    rules =
                        listOf(
                            rule("anki", tag = "work", deleted = true, updatedAt = 20),
                            rule("web:youtube", tag = "work", updatedAt = 5),
                            rule("org.kobo", tag = "study", updatedAt = 1),
                        ),
                ),
                "x",
                now,
            )
        assertEquals(
            listOf(rule("anki", tag = "work", deleted = true, updatedAt = 20), rule("org.kobo", tag = "study", updatedAt = 1)),
            plan.ruleUpserts,
        )
        assertEquals(2, plan.summary.rulesChanged)
    }

    @Test
    fun `rules that fail validation are dropped and a blank label becomes the app key`() {
        val plan =
            SyncMerge.plan(
                state(),
                payloadWith(
                    rules =
                        listOf(
                            rule(""),
                            rule("p".repeat(256)),
                            rule("a", label = "l".repeat(101)),
                            rule("b", tag = ""),
                            rule("c", tag = "t".repeat(65)),
                            rule("d", updatedBy = "d".repeat(65)),
                            rule("org.anki", label = " "),
                        ),
                ),
                "x",
                now,
            )
        assertEquals(listOf("org.anki"), plan.ruleUpserts.map { it.label })
        assertEquals(6, plan.dropped)
    }

    @Test
    fun `a rule for an activity this database doesn't have is kept`() {
        val plan = SyncMerge.plan(state(), payloadWith(rules = listOf(rule("anki", tag = "unknown"))), "x", now)
        assertEquals(listOf("unknown"), plan.ruleUpserts.map { it.activity })
    }

    private fun payloadWith(
        sessions: List<SessionRecord> = emptyList(),
        activities: List<ActivityRecord> = emptyList(),
        apps: List<SessionAppRecord> = emptyList(),
        rules: List<ActivityRuleRecord> = emptyList(),
    ) = SyncPayload(device = "remote", tags = emptyList(), sessions = sessions, activities = activities, apps = apps, activityRules = rules)

    private fun state(vararg tags: TagRecord) = LocalSyncState(tags.mapIndexed { i, t -> LocalTag(i + 1L, t) }, emptySet())

    private fun payload(
        vararg tags: TagRecord,
        sessions: List<SessionRecord> = emptyList(),
    ) = SyncPayload(device = "remote", tags = tags.toList(), sessions = sessions)
}

fun tag(
    uid: String,
    name: String = "Focus",
    color: String = "Indigo",
    work: Int = 25,
    shortBreak: Int = 5,
    longBreak: Int = 15,
    longBreakAfter: Int = 4,
    deleted: Boolean = false,
    updatedAt: Long = 0,
    updatedBy: String = "",
) = TagRecord(uid, name, color, work, shortBreak, longBreak, longBreakAfter, deleted, updatedAt, updatedBy)

fun session(
    uid: String,
    type: String = "Work",
    plannedMinutes: Int = 25,
    startedAt: Long = 1_000,
    endedAt: Long? = 2_000,
    outcome: String = "Completed",
    tag: String? = null,
    profile: String? = "deep",
    activity: String? = null,
) = SessionRecord(uid, type, profile, plannedMinutes, startedAt, endedAt, outcome, tag, activity)

fun activity(
    uid: String,
    name: String = "Reading on Kobo",
    deleted: Boolean = false,
    updatedAt: Long = 0,
    updatedBy: String = "",
) = ActivityRecord(uid, name, deleted, updatedAt, updatedBy)

fun app(
    session: String,
    packageName: String = "org.notes",
    label: String = "Notes",
    millis: Long = 60_000,
) = SessionAppRecord(session, packageName, label, millis)

fun rule(
    app: String,
    label: String = "Label",
    tag: String = "t",
    deleted: Boolean = false,
    updatedAt: Long = 0,
    updatedBy: String = "",
) = ActivityRuleRecord(app, label, tag, deleted, updatedAt, updatedBy)
