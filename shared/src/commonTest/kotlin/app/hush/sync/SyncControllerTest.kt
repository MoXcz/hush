// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

import app.hush.timer.TestClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SyncControllerTest {
    private val store = FakeSyncStore(exported = payloadFrom("self-id"))
    private val transport = FakeSyncTransport()
    private val scheduler = FakeSyncScheduler()
    private val identity = FakeDeviceIdentity("self-id")

    private fun TestScope.controller(peers: FakePeerStore): SyncController =
        SyncController(store, peers, transport, scheduler, identity, TestClock(testScheduler), backgroundScope)

    private fun received(payload: SyncPayload) = ExchangeResult.Received(SyncCodec.encode(payload))

    @Test
    fun `without a paired device nothing is sent`() =
        runTest {
            val controller = controller(FakePeerStore())
            assertNull(controller.syncNow(SyncTrigger.Open))
            assertTrue(transport.sent.isEmpty())
            assertTrue(store.calls.isEmpty())
        }

    @Test
    fun `a sync on open within a minute of the last attempt is skipped`() =
        runTest {
            val controller = controller(FakePeerStore(COMPUTER))
            assertEquals(SyncOutcome.NotFound, controller.syncNow(SyncTrigger.Open))
            advanceTimeBy(59.seconds)
            assertNull(controller.syncNow(SyncTrigger.Open))
            assertEquals(1, transport.sent.size)
            advanceTimeBy(1.seconds)
            assertEquals(SyncOutcome.NotFound, controller.syncNow(SyncTrigger.Open))
            assertEquals(2, transport.sent.size)
        }

    @Test
    fun `background syncs aren't throttled`() =
        runTest {
            val controller = controller(FakePeerStore(COMPUTER))
            controller.syncNow(SyncTrigger.Open)
            assertEquals(SyncOutcome.NotFound, controller.syncNow(SyncTrigger.Background))
            assertEquals(2, transport.sent.size)
        }

    @Test
    fun `the host's payload is merged and the sync recorded`() =
        runTest {
            val peers = FakePeerStore(COMPUTER)
            val controller = controller(peers)
            transport.next = received(payloadFrom(COMPUTER.deviceId))
            assertEquals(SyncOutcome.Synced, controller.syncNow(SyncTrigger.Open))
            assertEquals(payloadFrom("self-id"), (SyncCodec.decode(transport.sent.single()) as DecodeResult.Ok).payload)
            val (payload, self, now) = store.applied.single()
            assertEquals(COMPUTER.deviceId, payload.device)
            assertEquals("self-id", self)
            assertEquals(TestClock.START, now)
            assertEquals(PeerState(COMPUTER, TestClock.START, SyncOutcome.Synced), peers.current.value)
        }

    @Test
    fun `not found keeps the last successful sync time`() =
        runTest {
            val peers = FakePeerStore(COMPUTER)
            val controller = controller(peers)
            transport.next = received(payloadFrom(COMPUTER.deviceId))
            controller.syncNow(SyncTrigger.Open)
            advanceTimeBy(2.minutes)
            transport.next = ExchangeResult.NotFound
            controller.syncNow(SyncTrigger.Open)
            assertEquals(TestClock.START, peers.current.value.lastSyncedAt)
            assertEquals(SyncOutcome.NotFound, peers.current.value.lastResult)
        }

    @Test
    fun `rejected, too new and broken exchanges are recorded without merging`() =
        runTest {
            val peers = FakePeerStore(COMPUTER)
            val controller = controller(peers)
            val cases =
                listOf(
                    ExchangeResult.Rejected to SyncOutcome.Rejected,
                    ExchangeResult.TooNew to SyncOutcome.TooNew,
                    ExchangeResult.Failed to SyncOutcome.Failed,
                    received(payloadFrom(COMPUTER.deviceId).copy(v = 2)) to SyncOutcome.TooNew,
                    ExchangeResult.Received("not json".encodeToByteArray()) to SyncOutcome.Failed,
                    received(payloadFrom("someone-else")) to SyncOutcome.Rejected,
                )
            for ((result, outcome) in cases) {
                transport.next = result
                assertEquals(outcome, controller.syncNow(SyncTrigger.Background))
                assertEquals(outcome, peers.current.value.lastResult)
            }
            assertTrue(store.applied.isEmpty())
            assertNull(peers.current.value.lastSyncedAt)
        }

    @Test
    fun `serve exports before applying and answers with the export`() =
        runTest {
            val peers = FakePeerStore(PHONE)
            val controller = controller(peers)
            val answer = controller.serve(PHONE.deviceId, SyncCodec.encode(payloadFrom(PHONE.deviceId)))
            assertEquals(listOf("export", "apply"), store.calls)
            assertEquals(payloadFrom("self-id"), (SyncCodec.decode(assertNotNull(answer)) as DecodeResult.Ok).payload)
            assertEquals(PeerState(PHONE, TestClock.START, SyncOutcome.Synced), peers.current.value)
        }

    @Test
    fun `serve refuses a payload it can't read, from another device or too new, without exporting`() =
        runTest {
            val peers = FakePeerStore(PHONE)
            val controller = controller(peers)
            val cases =
                listOf(
                    "{".encodeToByteArray() to SyncOutcome.Failed,
                    SyncCodec.encode(payloadFrom("someone-else")) to SyncOutcome.Rejected,
                    SyncCodec.encode(payloadFrom(PHONE.deviceId).copy(v = 2)) to SyncOutcome.TooNew,
                )
            for ((incoming, outcome) in cases) {
                assertNull(controller.serve(PHONE.deviceId, incoming))
                assertEquals(outcome, peers.current.value.lastResult)
            }
            assertTrue(store.calls.isEmpty())
        }

    @Test
    fun `a failing database fails serve instead of throwing`() =
        runTest {
            val peers = FakePeerStore(PHONE)
            val controller = controller(peers)
            store.failure = IllegalStateException("database is locked")
            assertNull(controller.serve(PHONE.deviceId, SyncCodec.encode(payloadFrom(PHONE.deviceId))))
            assertEquals(SyncOutcome.Failed, peers.current.value.lastResult)
        }

    @Test
    fun `a failing database fails the sync instead of throwing`() =
        runTest {
            val peers = FakePeerStore(COMPUTER)
            val controller = controller(peers)
            store.failure = IllegalStateException("database is locked")
            assertEquals(SyncOutcome.Failed, controller.syncNow(SyncTrigger.Background))
            assertEquals(SyncOutcome.Failed, peers.current.value.lastResult)
            assertTrue(transport.sent.isEmpty())
            runCurrent()
            assertEquals(false, (controller.status.value as SyncStatus.Paired).syncing)
        }

    @Test
    fun `launchSync survives a failing database`() =
        runTest {
            val peers = FakePeerStore(COMPUTER)
            val controller = controller(peers)
            store.failure = IllegalStateException("database is locked")
            controller.launchSync(SyncTrigger.Open)
            runCurrent()
            assertEquals(SyncOutcome.Failed, peers.current.value.lastResult)
            assertEquals(false, (controller.status.value as SyncStatus.Paired).syncing)
        }

    @Test
    fun `unpair forgets the peer and stops background syncs`() =
        runTest {
            val peers = FakePeerStore(COMPUTER)
            val controller = controller(peers)
            runCurrent()
            assertIs<SyncStatus.Paired>(controller.status.value)
            controller.unpair()
            runCurrent()
            assertNull(peers.current.value.peer)
            assertEquals(listOf(false), scheduler.calls)
            assertEquals(SyncStatus.Unpaired, controller.status.value)
        }

    @Test
    fun `pairing stores the peer, starts background syncs and lifts the throttle`() =
        runTest {
            val peers = FakePeerStore(COMPUTER)
            val controller = controller(peers)
            controller.syncNow(SyncTrigger.Open)
            val other = COMPUTER.copy(deviceId = "other", name = "laptop")
            controller.paired(other)
            assertEquals(PeerState(other), peers.current.value)
            assertEquals(listOf(true), scheduler.calls)
            assertEquals(SyncOutcome.NotFound, controller.syncNow(SyncTrigger.Open))
        }

    @Test
    fun `status shows the peer, its last sync and whether a sync is running`() =
        runTest {
            val controller = controller(FakePeerStore())
            assertEquals(SyncStatus.Loading, controller.status.value)
            runCurrent()
            assertEquals(SyncStatus.Unpaired, controller.status.value)
            controller.paired(COMPUTER)
            transport.next = received(payloadFrom(COMPUTER.deviceId))
            val gate = CompletableDeferred<Unit>()
            transport.gate = gate
            controller.launchSync(SyncTrigger.Open)
            runCurrent()
            assertEquals(SyncStatus.Paired(COMPUTER, null, null, syncing = true), controller.status.value)
            gate.complete(Unit)
            runCurrent()
            assertEquals(SyncStatus.Paired(COMPUTER, TestClock.START, SyncOutcome.Synced, syncing = false), controller.status.value)
        }
}
