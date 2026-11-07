// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.sync

import app.hush.core.AppDispatchers
import app.hush.sync.DeviceIdentity
import app.hush.sync.PairedPeer
import app.hush.sync.PairingState
import app.hush.sync.PeerStore
import app.hush.sync.SyncController
import app.hush.sync.lan.LocalDevice
import app.hush.sync.lan.PeerInfo
import app.hush.sync.lan.SyncIdentity
import app.hush.sync.lan.SyncServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.net.InetAddress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class DesktopSyncHost(
    private val directory: File,
    private val pairing: DesktopPairing,
    private val controller: SyncController,
    private val peers: PeerStore,
    private val deviceIdentity: DeviceIdentity,
    private val scope: CoroutineScope,
    private val dispatchers: AppDispatchers,
    private val hostName: () -> String = ::localHostName,
    private val closeGrace: Duration = 2.seconds,
) {
    private var server: SyncServer? = null
    private var advertiser: AvahiAdvertiser? = null
    private var pairingWatch: Job? = null
    private var watch: Job? = null
    private var stopped = false
    private val listening = MutableStateFlow<Int?>(null)

    /** The bound port while the host listens, null otherwise. */
    val port: StateFlow<Int?> = listening.asStateFlow()

    /**
     * Listens (and advertises unless [advertise] is false) only while a phone is paired or a
     * pairing code shows, so an unpaired computer opens no port and announces nothing.
     */
    @Synchronized
    fun start(
        preferredPort: Int = SyncServer.DEFAULT_PORT,
        bindAddress: InetAddress? = null,
        advertise: Boolean = true,
    ) {
        check(watch == null && !stopped) { "already started" }
        watch =
            scope.launch(dispatchers.io) {
                needed().collect { on ->
                    if (on) listen(preferredPort, bindAddress, advertise, deviceIdentity.deviceId()) else closeListener()
                }
            }
    }

    @Synchronized
    fun stop() {
        stopped = true
        watch?.cancel()
        watch = null
        closeListener()
    }

    // Closing waits a moment, so the hand-over from "showing a code" to "paired" can't drop the
    // connection that is finishing the pairing.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun needed(): Flow<Boolean> =
        combine(
            peers.state.map { it.peer != null },
            pairing.state.map { it is PairingState.ShowingCode },
        ) { paired, showingCode -> paired || showingCode }
            .distinctUntilChanged()
            .transformLatest { on ->
                if (!on) delay(closeGrace)
                emit(on)
            }.distinctUntilChanged()

    @Synchronized
    private fun listen(
        preferredPort: Int,
        bindAddress: InetAddress?,
        advertise: Boolean,
        deviceId: String,
    ) {
        if (stopped || server != null) return
        try {
            val identity = SyncIdentity.loadOrCreate(directory)
            val name = hostName()
            val started = SyncServer(identity, LocalDevice(deviceId, name), pairing.codes, Handler(), ::report)
            val port = started.start(preferredPort, bindAddress)
            server = started
            if (advertise) {
                val avahi = AvahiAdvertiser(name, port, deviceId, onAdvertising = pairing::setAdvertising)
                advertiser = avahi
                avahi.start()
                pairingWatch =
                    scope.launch {
                        pairing.state
                            .map { it is PairingState.ShowingCode }
                            .distinctUntilChanged()
                            .collect(avahi::setPairing)
                    }
            }
            listening.value = port
        } catch (e: IOException) {
            // Rare (any free port is taken as a fallback); the rest of the app works without sync.
            report(e)
        }
    }

    @Synchronized
    private fun closeListener() {
        pairingWatch?.cancel()
        advertiser?.close()
        server?.close()
        pairingWatch = null
        advertiser = null
        server = null
        listening.value = null
        pairing.setAdvertising(true)
    }

    // Connection threads block on the shared suspend APIs; each call is one short database step.
    private inner class Handler : SyncServer.Handler {
        override fun pairedPeer(): PeerInfo? = runBlocking { peers.peer() }?.let { PeerInfo(it.deviceId, it.name, it.pin) }

        override fun onPaired(peer: PeerInfo) {
            val paired = PairedPeer(peer.deviceId, peer.name, peer.pin, host = null, port = null)
            runBlocking { controller.paired(paired) }
            pairing.paired(paired)
        }

        override fun serve(
            peer: PeerInfo,
            incoming: ByteArray,
        ): ByteArray =
            runBlocking { controller.serve(peer.deviceId, incoming) }
                ?: throw IOException("sync from ${peer.name} refused")
    }

    private fun report(error: Throwable) {
        System.err.println("Hush sync: $error")
    }
}

/** This computer's name for the phone: `/etc/hostname`, else what the resolver says. */
internal fun localHostName(
    etcHostname: () -> String? = { readEtcHostname() },
    fallback: () -> String = { InetAddress.getLocalHost().hostName },
): String =
    etcHostname()?.trim()?.takeIf { it.isNotEmpty() }
        ?: try {
            fallback().takeIf { it.isNotBlank() }
        } catch (_: IOException) {
            null
        } ?: "Computer"

private fun readEtcHostname(): String? =
    try {
        File("/etc/hostname")
            .takeIf { it.isFile }
            ?.readText()
            ?.lineSequence()
            ?.firstOrNull()
    } catch (_: IOException) {
        null
    }
