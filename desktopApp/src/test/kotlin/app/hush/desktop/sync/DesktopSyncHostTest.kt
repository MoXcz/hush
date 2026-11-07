// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.sync

import app.hush.data.prefs.SETTINGS_FILE_NAME
import app.hush.desktop.di.desktopModule
import app.hush.di.sharedModules
import app.hush.history.SessionOutcome
import app.hush.history.SessionRepository
import app.hush.history.SessionType
import app.hush.sync.DecodeResult
import app.hush.sync.DeviceIdentity
import app.hush.sync.Pairing
import app.hush.sync.PairingState
import app.hush.sync.PeerStore
import app.hush.sync.SessionRecord
import app.hush.sync.SyncCodec
import app.hush.sync.SyncController
import app.hush.sync.SyncPayload
import app.hush.sync.lan.LocalDevice
import app.hush.sync.lan.PairResult
import app.hush.sync.lan.SyncClient
import app.hush.sync.lan.SyncIdentity
import app.hush.sync.lan.SyncResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.dsl.koinApplication
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// The desktop wiring end to end: a phone's SyncClient pairs with the real host (Koin graph, Room,
// DataStore in a temp directory, no avahi) and syncs a session into the desktop's database.
class DesktopSyncHostTest {
    private val directory: File = Files.createTempDirectory("hush-host").toFile()
    private val koin = koinApplication { modules(sharedModules + desktopModule(directory, directory.resolve(SETTINGS_FILE_NAME))) }.koin
    private val host = koin.get<DesktopSyncHost>()
    private val loopback = InetAddress.getLoopbackAddress()
    private val phone = SyncClient(SyncIdentity.generate(), LocalDevice(PHONE_ID, "Pixel"))

    @AfterTest
    fun cleanUp() {
        host.stop()
        koin.close()
        directory.deleteRecursively()
    }

    @Test
    fun `a phone pairs with the desktop and syncs a session into its database`() =
        runBlocking {
            host.start(preferredPort = 0, bindAddress = loopback, advertise = false)
            val pairing = koin.get<Pairing>()
            pairing.open()
            val port = awaitPort()
            val shown = assertIs<PairingState.ShowingCode>(pairing.state.value)

            val paired = assertIs<PairResult.Paired>(phone.pair(loopback, port, shown.code))
            assertEquals(koin.get<DeviceIdentity>().deviceId(), paired.peer.deviceId)
            assertEquals(SyncIdentity.loadOrCreate(directory.resolve("sync")).pin, paired.peer.pin)
            assertTrue(directory.resolve("sync/sync-key.p8").isFile)
            val stored = koin.get<PeerStore>().peer()
            assertEquals(PHONE_ID, stored?.deviceId)
            assertEquals("Pixel", stored?.name)
            assertNull(stored?.host)
            assertIs<PairingState.Paired>(pairing.state.value)

            val start = Instant.parse("2026-11-05T09:00:00Z").toEpochMilliseconds()
            val session =
                SessionRecord(
                    uid = "6f1c2a7e-3b9d-4c51-8e2a-0d4b5c6e7f80",
                    type = SessionType.Detox.name,
                    profile = "detox",
                    plannedMinutes = 90,
                    startedAt = start,
                    endedAt = start + 90 * 60_000,
                    outcome = SessionOutcome.Completed.name,
                )
            val outgoing = SyncCodec.encode(SyncPayload(device = PHONE_ID, tags = emptyList(), sessions = listOf(session)))
            val result = assertIs<SyncResult.Received>(phone.sync(loopback, port, paired.peer.pin, outgoing))

            val answer = assertIs<DecodeResult.Ok>(SyncCodec.decode(result.payload))
            assertEquals(paired.peer.deviceId, answer.payload.device)
            assertEquals(3, answer.payload.tags.size)
            // Exported before the merge: the phone's own session doesn't come back.
            assertTrue(answer.payload.sessions.isEmpty())
            val sessions = koin.get<SessionRepository>()
            assertEquals(listOf(90), sessions.history.first().map { it.plannedMinutes })
            assertEquals(90, sessions.longestPassedLevelMinutes.first())
        }

    @Test
    fun `an unpaired host listens only while a code shows`() =
        runBlocking {
            host.start(preferredPort = 0, bindAddress = loopback, advertise = false)
            delay(300.milliseconds)
            assertNull(host.port.value)

            val pairing = koin.get<Pairing>()
            pairing.open()
            awaitPort()
            pairing.close()
            awaitClosed()
        }

    @Test
    fun `a paired host keeps listening after the code closes and stops on unpair`() =
        runBlocking {
            host.start(preferredPort = 0, bindAddress = loopback, advertise = false)
            val pairing = koin.get<Pairing>()
            pairing.open()
            val port = awaitPort()
            val shown = assertIs<PairingState.ShowingCode>(pairing.state.value)
            assertIs<PairResult.Paired>(phone.pair(loopback, port, shown.code))

            pairing.close()
            delay(2500.milliseconds)
            assertEquals(port, host.port.value)

            koin.get<SyncController>().unpair()
            awaitClosed()
        }

    @Test
    fun `an unpaired host rejects a sync`() =
        runBlocking {
            host.start(preferredPort = 0, bindAddress = loopback, advertise = false)
            koin.get<Pairing>().open()
            val port = awaitPort()
            val hostPin = SyncIdentity.loadOrCreate(directory.resolve("sync")).pin
            assertEquals(SyncResult.Rejected, phone.sync(loopback, port, hostPin, ByteArray(0)))
        }

    private suspend fun awaitPort(): Int = withTimeout(5.seconds) { host.port.filterNotNull().first() }

    private suspend fun awaitClosed() {
        withTimeout(5.seconds) { host.port.first { it == null } }
    }

    private companion object {
        const val PHONE_ID = "a1b2c3d4-0000-4000-8000-00000000beef"
    }
}
