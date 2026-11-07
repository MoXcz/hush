// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocker.fake

import app.hush.blocker.Availability
import app.hush.blocker.BlockProfile
import app.hush.blocker.BlockResult
import app.hush.blocker.ProfileId
import app.hush.blocker.SessionStatus
import app.hush.blocker.UnknownReason
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeBlockerTest {
    @Test
    fun `is ready with one deep work profile by default`() =
        runTest {
            val blocker = FakeBlocker()
            assertEquals(Availability.Ready, blocker.availability())
            assertEquals(listOf(BlockProfile(ProfileId("deep"), "Deep work", true)), blocker.profiles())
            assertEquals(SessionStatus.None, blocker.activeSession())
        }

    @Test
    fun `start opens a session for the profile and records the call`() =
        runTest {
            val blocker = FakeBlocker()
            assertEquals(BlockResult.Ok, blocker.start(ProfileId("deep"), 25))
            assertEquals(SessionStatus.Active("Deep work", 25), blocker.activeSession())
            assertEquals(listOf("start:deep:25", "activeSession"), blocker.calls)
        }

    @Test
    fun `stop ends the session`() =
        runTest {
            val blocker = FakeBlocker()
            blocker.start(ProfileId("deep"), 25)
            assertEquals(BlockResult.Ok, blocker.stop())
            assertEquals(SessionStatus.None, blocker.activeSession())
        }

    @Test
    fun `failed start leaves no session`() =
        runTest {
            val blocker = FakeBlocker().apply { nextResult = BlockResult.ConnectionLost }
            assertEquals(BlockResult.ConnectionLost, blocker.start(ProfileId("deep"), 25))
            assertEquals(SessionStatus.None, blocker.activeSession())
        }

    @Test
    fun `refused stop keeps the session`() =
        runTest {
            val blocker = FakeBlocker()
            blocker.start(ProfileId("deep"), 25)
            blocker.nextResult = BlockResult.Refused
            assertEquals(BlockResult.Refused, blocker.stop())
            assertEquals(SessionStatus.Active("Deep work", 25), blocker.activeSession())
        }

    @Test
    fun `availability and profiles can be changed from outside`() =
        runTest {
            val blocker = FakeBlocker(availability = Availability.Unsupported, profiles = emptyList())
            assertEquals(Availability.Unsupported, blocker.availability())
            assertEquals(emptyList(), blocker.profiles())
            blocker.availability = Availability.NeedsApproval
            assertEquals(Availability.NeedsApproval, blocker.availability())
        }

    @Test
    fun `session can be set to unknown`() =
        runTest {
            val blocker = FakeBlocker().apply { session = SessionStatus.Unknown(UnknownReason.Unreachable) }
            assertEquals(SessionStatus.Unknown(UnknownReason.Unreachable), blocker.activeSession())
        }
}
