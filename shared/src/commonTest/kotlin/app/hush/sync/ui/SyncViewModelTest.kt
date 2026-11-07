// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.ui

import app.hush.core.DeviceFeatures
import app.hush.sync.COMPUTER
import app.hush.sync.FakeDeviceIdentity
import app.hush.sync.FakePairing
import app.hush.sync.FakePeerStore
import app.hush.sync.FakeSyncScheduler
import app.hush.sync.FakeSyncStore
import app.hush.sync.FakeSyncTransport
import app.hush.sync.PHONE
import app.hush.sync.PairingFailure
import app.hush.sync.PairingState
import app.hush.sync.PeerState
import app.hush.sync.SyncController
import app.hush.sync.SyncOutcome
import app.hush.sync.SyncRole
import app.hush.timer.TestClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {
    private val pairing = FakePairing()
    private val transport = FakeSyncTransport()
    private val scheduler = FakeSyncScheduler()
    private lateinit var peers: FakePeerStore
    private lateinit var vm: SyncViewModel

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        role: SyncRole,
        peer: app.hush.sync.PairedPeer? = null,
    ): SyncViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        peers = FakePeerStore(peer)
        val clock = TestClock(testScheduler)
        val controller =
            SyncController(FakeSyncStore(), peers, transport, scheduler, FakeDeviceIdentity(), clock, backgroundScope)
        return SyncViewModel(controller, pairing, DeviceFeatures(syncRole = role), clock).also { vm ->
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
        }
    }

    private val state get() = vm.uiState.value

    @Test
    fun `the host says when phones can't find it, paired or not`() =
        runTest {
            vm = viewModel(SyncRole.Host)
            assertTrue(state.discoverable)
            pairing.advertising.value = false
            runCurrent()
            assertFalse(state.discoverable)
            peers.save(PHONE)
            runCurrent()
            assertTrue(state.paired)
            assertFalse(state.discoverable)
            pairing.advertising.value = true
            runCurrent()
            assertTrue(state.discoverable)
        }

    @Test
    fun `the host shows its code and counts down`() =
        runTest {
            vm = viewModel(SyncRole.Host)
            assertEquals(listOf("open"), pairing.calls)
            pairing.current.value = PairingState.ShowingCode("7K2Q-M9XD-4TRB", TestClock.START + 2.minutes)
            runCurrent()
            assertEquals("7K2Q-M9XD-4TRB", state.code)
            assertEquals(120, state.secondsLeft)
            advanceTimeBy(30.seconds)
            runCurrent()
            assertEquals(90, state.secondsLeft)
            advanceTimeBy(3.minutes)
            runCurrent()
            assertEquals(0, state.secondsLeft)
            pairing.current.value = PairingState.ShowingCode("AAAA-BBBB-CCCC", TestClock.START + 5.minutes + 30.seconds)
            runCurrent()
            assertEquals("AAAA-BBBB-CCCC", state.code)
            assertEquals(120, state.secondsLeft)
        }

    @Test
    fun `the client maps every pairing step and problem`() =
        runTest {
            vm = viewModel(SyncRole.Client)
            assertFalse(state.loading)
            assertFalse(state.paired)
            assertEquals(PairingStep.Ready, state.step)
            pairing.current.value = PairingState.Searching
            runCurrent()
            assertEquals(PairingStep.Searching, state.step)
            pairing.current.value = PairingState.Connecting
            runCurrent()
            assertEquals(PairingStep.Connecting, state.step)
            for (reason in PairingFailure.entries) {
                pairing.current.value = PairingState.Failed(reason)
                runCurrent()
                assertEquals(reason, state.problem)
                assertEquals(PairingStep.Ready, state.step)
            }
            vm.onAction(SyncAction.DismissProblem)
            assertEquals("open", pairing.calls.last())
        }

    @Test
    fun `pair sends the typed code only once it's complete`() =
        runTest {
            vm = viewModel(SyncRole.Client)
            vm.onAction(SyncAction.EditCode("7k2q-m9xd"))
            runCurrent()
            assertFalse(state.canPair)
            vm.onAction(SyncAction.Pair)
            runCurrent()
            assertEquals(listOf("open"), pairing.calls)
            vm.onAction(SyncAction.EditCode("7k2q-m9xd-4trb"))
            runCurrent()
            assertTrue(state.canPair)
            vm.onAction(SyncAction.Pair)
            runCurrent()
            assertEquals("enterCode:7k2q-m9xd-4trb", pairing.calls.last())
            pairing.current.value = PairingState.Searching
            runCurrent()
            assertFalse(state.canPair)
        }

    @Test
    fun `a paired client shows the peer, closes pairing and syncs on open`() =
        runTest {
            vm = viewModel(SyncRole.Client, COMPUTER)
            assertEquals(listOf("close"), pairing.calls)
            assertEquals(1, transport.sent.size)
            assertEquals("arch", state.peerName)
            assertEquals(SyncLine.NotFound, state.line)
        }

    @Test
    fun `a paired host shows the last sync and doesn't start one`() =
        runTest {
            vm = viewModel(SyncRole.Host, PHONE)
            peers.current.value = PeerState(PHONE, TestClock.START - 5.minutes, SyncOutcome.Synced)
            runCurrent()
            assertTrue(transport.sent.isEmpty())
            assertEquals("Pixel", state.peerName)
            assertEquals(SyncLine.Synced(5), state.line)
            assertNull(state.code)
        }

    @Test
    fun `pairing on the client syncs right away`() =
        runTest {
            vm = viewModel(SyncRole.Client)
            peers.current.value = PeerState(COMPUTER)
            runCurrent()
            assertEquals(listOf("open", "close"), pairing.calls)
            assertEquals(1, transport.sent.size)
        }

    @Test
    fun `unpair forgets the peer and opens pairing again`() =
        runTest {
            vm = viewModel(SyncRole.Host, PHONE)
            vm.onAction(SyncAction.Unpair)
            runCurrent()
            assertNull(peers.current.value.peer)
            assertEquals(listOf(false), scheduler.calls)
            assertEquals(listOf("close", "open"), pairing.calls)
            assertFalse(state.paired)
        }

    @Test
    fun `nothing happens where there's no sync`() =
        runTest {
            vm = viewModel(SyncRole.None)
            assertTrue(pairing.calls.isEmpty())
        }
}
