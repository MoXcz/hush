// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncCodecTest {
    private val payload =
        SyncPayload(
            device = "phone",
            tags = listOf(tag("t", name = "Läsning ✓", deleted = true, updatedAt = 42, updatedBy = "phone")),
            sessions = listOf(session("s", tag = "t", activity = "a"), session("d", type = "Detox", tag = null).copy(profile = null)),
            activities = listOf(ActivityRecord("a", "Reading on Kobo", deleted = false, updatedAt = 7, updatedBy = "phone")),
            apps = listOf(SessionAppRecord("s", "org.notes", "Notes", 120_000)),
            activityRules = listOf(ActivityRuleRecord("org.kobo", "Kobo", "a", deleted = false, updatedAt = 9, updatedBy = "phone")),
        )

    @Test
    fun `a payload survives a round trip unchanged`() {
        assertEquals(DecodeResult.Ok(payload), SyncCodec.decode(SyncCodec.encode(payload)))
    }

    @Test
    fun `a payload from a newer version is too new`() {
        val newer = SyncCodec.encode(payload).decodeToString().replaceFirst("\"v\":1", "\"v\":2")
        assertEquals(DecodeResult.TooNew, SyncCodec.decode(newer.encodeToByteArray()))
        assertEquals(DecodeResult.TooNew, SyncCodec.decode("""{"v":2,"something":"else"}""".encodeToByteArray()))
    }

    @Test
    fun `unknown keys are ignored`() {
        val json =
            """{"v":1,"device":"x","extra":[1],"tags":[],"sessions":[{"uid":"s","type":"Work","profile":null,""" +
                """"plannedMinutes":25,"startedAt":1,"endedAt":2,"outcome":"Completed","new":true}]}"""
        val decoded = SyncCodec.decode(json.encodeToByteArray())
        assertEquals(
            "s",
            (decoded as DecodeResult.Ok)
                .payload.sessions
                .single()
                .uid,
        )
    }

    @Test
    fun `a payload from before activities, apps and rules reads them as empty`() {
        val old =
            """{"v":1,"device":"x","tags":[],"sessions":[{"uid":"s","type":"Work","profile":null,""" +
                """"plannedMinutes":25,"startedAt":1,"endedAt":2,"outcome":"Completed","tag":null}]}"""
        val decoded = (SyncCodec.decode(old.encodeToByteArray()) as DecodeResult.Ok).payload
        assertEquals(emptyList(), decoded.activities)
        assertEquals(emptyList(), decoded.apps)
        assertEquals(emptyList(), decoded.activityRules)
        assertEquals(null, decoded.sessions.single().activity)
    }

    @Test
    fun `old tag rules from an earlier Hush are ignored`() {
        val old =
            """{"v":1,"device":"x","tags":[],"sessions":[],""" +
                """"rules":[{"target":"anki","label":"Anki","tag":"t","deleted":false,"updatedAt":1,"updatedBy":"x"}]}"""
        val decoded = (SyncCodec.decode(old.encodeToByteArray()) as DecodeResult.Ok).payload
        assertEquals(emptyList(), decoded.activityRules)
    }

    @Test
    fun `activities, apps and rules keep the payload at version 1`() {
        assertEquals(1, payload.v)
        assertTrue(SyncCodec.encode(payload).decodeToString().startsWith("{\"v\":1,"))
    }

    @Test
    fun `anything that isn't a payload is malformed`() {
        listOf("", "[]", "{", """{"v":"x"}""", """{"v":1}""").forEach {
            assertEquals(DecodeResult.Malformed, SyncCodec.decode(it.encodeToByteArray()), it)
        }
    }
}
