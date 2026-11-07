// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocker.builtin

import app.hush.blocker.Availability
import app.hush.blocker.BlockProfile
import app.hush.blocker.BlockResult
import app.hush.blocker.ProfileId
import app.hush.blocker.SessionStatus
import app.hush.blocker.UnknownReason
import app.hush.blocklists.BlockList
import app.hush.blocklists.BlockListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class NativeBlockerTest {
    private val deep = ProfileId("deep")
    private val detox = ProfileId("detox")
    private val store = FakeStore()
    private val clock = MutableClock(Instant.parse("2026-11-06T09:00:00Z"))
    private var enabled = true
    private val lists =
        FakeLists(
            listOf(BlockList(deep, "Deep work", setOf("com.example.video")), BlockList(detox, "Detox", emptySet())),
        )
    private val blocker = NativeBlocker(store, lists, clock, serviceEnabled = { enabled })

    @Test
    fun `ready only while the service is turned on`() =
        runTest {
            assertEquals(Availability.Ready, blocker.availability())
            enabled = false
            assertEquals(Availability.NeedsApproval, blocker.availability())
        }

    @Test
    fun `every block list is a profile that can stop early`() =
        runTest {
            assertEquals(
                listOf(BlockProfile(deep, "Deep work", true), BlockProfile(detox, "Detox", true)),
                blocker.profiles(),
            )
        }

    @Test
    fun `start stores the end time, and the session is active until then`() =
        runTest {
            assertEquals(BlockResult.Ok, blocker.start(deep, 25))
            assertEquals(BlockSession(deep, "Deep work", clock.now + 25.minutes), store.stored)
            assertEquals(SessionStatus.Active("Deep work", 25), blocker.activeSession())
            clock.advance(10.minutes + 30.seconds)
            assertEquals(SessionStatus.Active("Deep work", 15), blocker.activeSession())
            clock.advance(15.minutes)
            assertEquals(SessionStatus.None, blocker.activeSession())
        }

    @Test
    fun `turning the service off ends a running session`() =
        runTest {
            blocker.start(deep, 25)
            enabled = false
            assertEquals(SessionStatus.None, blocker.activeSession())
        }

    @Test
    fun `stop clears the session`() =
        runTest {
            blocker.start(deep, 25)
            assertEquals(BlockResult.Ok, blocker.stop())
            assertNull(store.stored)
            assertEquals(SessionStatus.None, blocker.activeSession())
        }

    @Test
    fun `start is denied while the service is off, and nothing is stored`() =
        runTest {
            enabled = false
            assertEquals(BlockResult.Denied, blocker.start(deep, 25))
            assertNull(store.stored)
        }

    @Test
    fun `an unknown list is refused`() =
        runTest {
            assertEquals(BlockResult.Refused, blocker.start(ProfileId("gone"), 25))
        }

    @Test
    fun `a failed write is a lost connection, and an unreadable store is unknown, not none`() =
        runTest {
            store.failing = true
            assertEquals(BlockResult.ConnectionLost, blocker.start(deep, 25))
            assertEquals(BlockResult.ConnectionLost, blocker.stop())
            assertEquals(SessionStatus.Unknown(UnknownReason.Unreadable), blocker.activeSession())
        }

    @Test
    fun `only listed apps are blocked, only before the end, never the always-allowed ones`() {
        val end = clock.now + 25.minutes
        val block = ActiveBlock(deep, end, setOf("com.example.video", "com.android.settings"))
        val allowed = setOf("com.android.settings")
        assertEquals(true, BlockRules.shouldBlock("com.example.video", block, clock.now, allowed))
        assertEquals(false, BlockRules.shouldBlock("com.example.mail", block, clock.now, allowed))
        assertEquals(false, BlockRules.shouldBlock("com.android.settings", block, clock.now, allowed))
        assertEquals(false, BlockRules.shouldBlock("com.example.video", block, end, allowed))
        assertEquals(false, BlockRules.shouldBlock("com.example.video", null, clock.now, allowed))
    }
}

private class FakeStore : BlockSessionStore {
    private val state = MutableStateFlow<BlockSession?>(null)
    var failing = false
    val stored: BlockSession? get() = state.value

    override val session: Flow<BlockSession?>
        get() = if (failing) throw IOException("disk") else state

    override suspend fun save(session: BlockSession) {
        if (failing) throw IOException("disk")
        state.value = session
    }

    override suspend fun clear() {
        if (failing) throw IOException("disk")
        state.value = null
    }
}

private class FakeLists(
    initial: List<BlockList>,
) : BlockListRepository {
    override val lists = MutableStateFlow(initial)

    override suspend fun setApps(
        id: ProfileId,
        packages: Set<String>,
    ) = lists.update { all -> all.map { if (it.id == id) it.copy(packages = packages) else it } }
}

private class MutableClock(
    var now: Instant,
) : Clock {
    override fun now(): Instant = now

    fun advance(by: Duration) {
        now += by
    }
}
