// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.lan

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.Random
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncLanTest {
    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val hostIdentity = SyncIdentity.generate()
    private val phoneIdentity = SyncIdentity.generate()
    private val hostDevice = LocalDevice("host-id", "Desktop")
    private val phoneDevice = LocalDevice("phone-id", "Phone")
    private var now = 5_000_000L
    private val codes = PairingCodes(clock = { now })
    private val handler = RecordingHandler()
    private val server = SyncServer(hostIdentity, hostDevice, codes, handler)
    private val client = SyncClient(phoneIdentity, phoneDevice)
    private val closeables = mutableListOf<Closeable>(server)

    private val port by lazy { server.start(preferredPort = 0, bindAddress = loopback) }

    @AfterTest
    fun cleanUp() {
        closeables.forEach(Closeable::close)
    }

    private class RecordingHandler : SyncServer.Handler {
        @Volatile
        var peer: PeerInfo? = null
        val paired: MutableList<PeerInfo> = Collections.synchronizedList(mutableListOf())
        val served: MutableList<ByteArray> = Collections.synchronizedList(mutableListOf())

        override fun pairedPeer() = peer

        override fun onPaired(peer: PeerInfo) {
            paired += peer
            this.peer = peer
        }

        override fun serve(
            peer: PeerInfo,
            incoming: ByteArray,
        ): ByteArray {
            served += incoming
            return incoming.reversedArray()
        }
    }

    private fun pairedHost() {
        handler.peer = PeerInfo(phoneDevice.deviceId, phoneDevice.name, phoneIdentity.pin)
    }

    @Test
    fun `sync exchanges opaque payloads with the right pins`() {
        pairedHost()
        val payload = ByteArray(200_000).also { Random(1).nextBytes(it) }
        val result = client.sync(loopback, port, hostIdentity.pin, payload)
        assertIs<SyncResult.Received>(result)
        assertContentEquals(payload.reversedArray(), result.payload)
        assertContentEquals(payload, handler.served.single())
    }

    @Test
    fun `sync with an empty payload works`() {
        pairedHost()
        val result = client.sync(loopback, port, hostIdentity.pin, ByteArray(0))
        assertIs<SyncResult.Received>(result)
        assertEquals(0, result.payload.size)
    }

    @Test
    fun `client refuses a host whose key isn't the pinned one`() {
        pairedHost()
        val result = client.sync(loopback, port, SyncIdentity.generate().pin, byteArrayOf(1))
        assertEquals(SyncResult.Rejected, result)
        assertTrue(handler.served.isEmpty())
    }

    @Test
    fun `host refuses a client whose key isn't the paired one`() {
        handler.peer = PeerInfo(phoneDevice.deviceId, phoneDevice.name, SyncIdentity.generate().pin)
        assertEquals(SyncResult.Rejected, client.sync(loopback, port, hostIdentity.pin, byteArrayOf(1)))
        assertTrue(handler.served.isEmpty())
    }

    @Test
    fun `host refuses the paired key under another device id`() {
        handler.peer = PeerInfo("someone-else", "Phone", phoneIdentity.pin)
        assertEquals(SyncResult.Rejected, client.sync(loopback, port, hostIdentity.pin, byteArrayOf(1)))
        assertTrue(handler.served.isEmpty())
    }

    @Test
    fun `unpaired host refuses sync`() {
        assertEquals(SyncResult.Rejected, client.sync(loopback, port, hostIdentity.pin, byteArrayOf(1)))
        assertTrue(handler.served.isEmpty())
    }

    @Test
    fun `pairing with the right code stores both sides and closes pairing`() {
        val shown = codes.open()
        val result = client.pair(loopback, port, shown.display.lowercase())
        assertIs<PairResult.Paired>(result)
        assertEquals(PeerInfo(hostDevice.deviceId, hostDevice.name, hostIdentity.pin), result.peer)
        assertEquals(PeerInfo(phoneDevice.deviceId, phoneDevice.name, phoneIdentity.pin), handler.paired.single())
        assertNull(codes.current())

        val synced = client.sync(loopback, port, result.peer.pin, byteArrayOf(1, 2, 3))
        assertIs<SyncResult.Received>(synced)
        assertContentEquals(byteArrayOf(3, 2, 1), synced.payload)
    }

    @Test
    fun `wrong code fails, rotates the code and stores nothing`() {
        val shown = codes.open()
        val wrong = if (shown.code.startsWith("0")) "1" + shown.code.drop(1) else "0" + shown.code.drop(1)
        assertEquals(PairResult.WrongCode, client.pair(loopback, port, wrong))
        assertTrue(handler.paired.isEmpty())
        val rotated = codes.current()!!
        assertNotEquals(shown.code, rotated.code)

        // One attempt only: the old code is gone even when typed right.
        assertEquals(PairResult.WrongCode, client.pair(loopback, port, shown.code))
        assertTrue(handler.paired.isEmpty())
        assertIs<PairResult.Paired>(client.pair(loopback, port, codes.current()!!.code))
    }

    @Test
    fun `malformed code fails without using up the host's code`() {
        val shown = codes.open()
        assertEquals(PairResult.WrongCode, client.pair(loopback, port, "ABCD"))
        assertEquals(shown, codes.current())
    }

    @Test
    fun `expired code fails and stores nothing`() {
        val shown = codes.open()
        now += PairingCodes.DEFAULT_VALID_MILLIS
        assertEquals(PairResult.Expired, client.pair(loopback, port, shown.code))
        assertTrue(handler.paired.isEmpty())
        assertNotEquals(shown.code, codes.current()!!.code)
    }

    @Test
    fun `pairing while the host shows no code fails as expired`() {
        assertEquals(PairResult.Expired, client.pair(loopback, port, "ABCD-EFGH-JKMN"))
        assertTrue(handler.paired.isEmpty())
    }

    @Test
    fun `host that fails to store the peer fails the pairing`() {
        val failing =
            object : SyncServer.Handler by handler {
                override fun onPaired(peer: PeerInfo) = throw IOException("disk full")
            }
        val other = SyncServer(hostIdentity, hostDevice, codes, failing).also { closeables += it }
        val otherPort = other.start(preferredPort = 0, bindAddress = loopback)
        assertIs<PairResult.Failed>(client.pair(loopback, otherPort, codes.open().code))
    }

    @Test
    fun `relay with its own keys on both legs can't pair either side`() {
        val shown = codes.open()
        val relay = Relay(port).also { closeables += it }
        val result = client.pair(loopback, relay.port, shown.code)
        assertTrue(relay.forwarded, "the relay should have carried the exchange")
        // The host saw a proof over another binding: Hello, then WRONG_CODE.
        assertEquals(PairResult.WrongCode, result)
        assertEquals(listOf(Protocol.OK, Protocol.WRONG_CODE), relay.hostStatuses())
        assertTrue(handler.paired.isEmpty())
        assertNull(handler.peer)
        assertNotEquals(shown.code, codes.current()!!.code)
    }

    @Test
    fun `relay can't sync with a paired host`() {
        pairedHost()
        val relay = Relay(port).also { closeables += it }
        assertEquals(SyncResult.Rejected, client.sync(loopback, relay.port, hostIdentity.pin, byteArrayOf(1)))
        assertTrue(handler.served.isEmpty())
    }

    @Test
    fun `Hellos alone don't use up the host's code`() {
        val shown = codes.open()
        repeat(3) { attempt ->
            val ssl =
                PinnedTls
                    .context(SyncIdentity.generate(), PinTrustManager(expectedPin = null))
                    .socketFactory
                    .createSocket(loopback, port) as SSLSocket
            ssl.use {
                it.soTimeout = 5_000
                val out = DataOutputStream(it.outputStream)
                Protocol.writeClientHello(out, Mode.PAIR, LocalDevice("prober-$attempt", "Prober").toHello(), Protocol.VERSION)
                val reply = Protocol.readServerHello(DataInputStream(it.inputStream), Protocol.VERSION)
                assertIs<Protocol.ServerHello.Ok>(reply)
            }
        }
        assertEquals(shown, codes.current())
        assertIs<PairResult.Paired>(client.pair(loopback, port, shown.code))
    }

    @Test
    fun `two devices pairing with the same code at once, only one is paired`() {
        val shown = codes.open()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val attempts =
                listOf(client, SyncClient(SyncIdentity.generate(), LocalDevice("tablet-id", "Tablet"))).map { device ->
                    pool.submit(
                        Callable {
                            start.await()
                            device.pair(loopback, port, shown.code)
                        },
                    )
                }
            start.countDown()
            val results = attempts.map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is PairResult.Paired }, "results: $results")
            assertTrue(results.single { it !is PairResult.Paired } in setOf(PairResult.WrongCode, PairResult.Expired))
            assertEquals(1, handler.paired.size)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `relay that knows the code pairs only its own key, never the two devices with each other`() {
        val shown = codes.open()
        val relay = CodeKnowingRelay(port, shown.code).also { closeables += it }
        val result = client.pair(loopback, relay.port, shown.code)
        // Knowing the code makes the relay a peer (that's what the code is for), but each side pins
        // the relay's key: the phone and the host never trust each other through it.
        assertIs<PairResult.Paired>(result)
        assertEquals(relay.pin, result.peer.pin)
        assertEquals(relay.pin, handler.paired.single().pin)
        relay.close()
        assertEquals(SyncResult.Rejected, client.sync(loopback, port, result.peer.pin, byteArrayOf(1)))
        assertEquals(SyncResult.Rejected, client.sync(loopback, port, hostIdentity.pin, byteArrayOf(1)))
        assertTrue(handler.served.isEmpty())
    }

    @Test
    fun `different protocol versions answer TooNew on both sides`() {
        pairedHost()
        val newer = SyncClient(phoneIdentity, phoneDevice).apply { protocolVersion = 2 }
        assertEquals(SyncResult.TooNew, newer.sync(loopback, port, hostIdentity.pin, byteArrayOf(1)))
        assertEquals(PairResult.TooNew, newer.pair(loopback, port, codes.open().code))

        server.protocolVersion = 2
        assertEquals(SyncResult.TooNew, client.sync(loopback, port, hostIdentity.pin, byteArrayOf(1)))
        assertTrue(handler.served.isEmpty())
        assertTrue(handler.paired.isEmpty())
    }

    @Test
    fun `client refuses addresses that aren't local without connecting`() {
        var sockets = 0
        val counting = SyncClient(phoneIdentity, phoneDevice) { Socket().also { sockets++ } }
        val remote = InetAddress.getByName("8.8.8.8")
        assertIs<SyncResult.Failed>(counting.sync(remote, SyncServer.DEFAULT_PORT, hostIdentity.pin, byteArrayOf(1)))
        assertIs<PairResult.Failed>(counting.pair(remote, SyncServer.DEFAULT_PORT, "ABCD-EFGH-JKMN"))
        assertEquals(0, sockets)
    }

    @Test
    fun `host closes connections from addresses it doesn't count as local before TLS`() {
        pairedHost()
        val asked = Collections.synchronizedList(mutableListOf<InetAddress>())
        server.acceptsFrom = {
            asked += it
            false
        }
        assertIs<SyncResult.Failed>(client.sync(loopback, port, hostIdentity.pin, byteArrayOf(1)))
        assertEquals(listOf(loopback), asked.toList())
        assertTrue(handler.served.isEmpty())
    }

    @Test
    fun `client uses the socket factory it was given`() {
        pairedHost()
        var sockets = 0
        val counting = SyncClient(phoneIdentity, phoneDevice) { Socket().also { sockets++ } }
        assertIs<SyncResult.Received>(counting.sync(loopback, port, hostIdentity.pin, byteArrayOf(1)))
        assertEquals(1, sockets)
    }

    @Test
    fun `closing the call handle mid-exchange fails the sync at once`() {
        pairedHost()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stalling =
            object : SyncServer.Handler by handler {
                override fun serve(
                    peer: PeerInfo,
                    incoming: ByteArray,
                ): ByteArray {
                    entered.countDown()
                    release.await(30, TimeUnit.SECONDS)
                    return incoming
                }
            }
        val other = SyncServer(hostIdentity, hostDevice, codes, stalling).also { closeables += it }
        closeables += Closeable { release.countDown() }
        val otherPort = other.start(preferredPort = 0, bindAddress = loopback)
        val handle = CallHandle()
        val pool = Executors.newSingleThreadExecutor()
        try {
            val call = pool.submit(Callable { client.sync(loopback, otherPort, hostIdentity.pin, byteArrayOf(1), handle) })
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val closedAt = System.nanoTime()
            handle.close()
            assertIs<SyncResult.Failed>(call.get(5, TimeUnit.SECONDS))
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - closedAt) < 5_000)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `a call whose handle is already closed fails without connecting`() {
        pairedHost()
        val handle = CallHandle().apply { close() }
        assertIs<SyncResult.Failed>(client.sync(loopback, port, hostIdentity.pin, byteArrayOf(1), handle))
        assertIs<PairResult.Failed>(client.pair(loopback, port, codes.open().code, handle))
        assertTrue(handler.served.isEmpty())
        assertTrue(handler.paired.isEmpty())
    }

    @Test
    fun `a handler that throws refuses the sync and the client fails`() {
        pairedHost()
        val refusing =
            object : SyncServer.Handler by handler {
                override fun serve(
                    peer: PeerInfo,
                    incoming: ByteArray,
                ): ByteArray = throw IOException("refused")
            }
        val errors = LinkedBlockingQueue<Throwable>()
        val other = SyncServer(hostIdentity, hostDevice, codes, refusing, onError = { errors += it }).also { closeables += it }
        val otherPort = other.start(preferredPort = 0, bindAddress = loopback)
        assertIs<SyncResult.Failed>(client.sync(loopback, otherPort, hostIdentity.pin, byteArrayOf(1)))
        // The server reports after the client has its answer, so wait for it.
        assertIs<IOException>(errors.poll(10, TimeUnit.SECONDS))
        assertNull(errors.poll(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `server falls back to another port when the preferred one is taken`() {
        ServerSocket(0, 1, loopback).use { taken ->
            val other = SyncServer(hostIdentity, hostDevice, codes, handler).also { closeables += it }
            val bound = other.start(preferredPort = taken.localPort, bindAddress = loopback)
            assertNotEquals(taken.localPort, bound)
            assertEquals(bound, other.port)
        }
    }

    @Test
    fun `host refuses a client without a certificate`() {
        // A TLS client without a certificate never gets past the handshake.
        val trust = PinTrustManager(expectedPin = null)
        val context =
            javax.net.ssl.SSLContext
                .getInstance("TLS")
                .apply { init(null, arrayOf(trust), null) }
        val socket = context.socketFactory.createSocket(loopback, port) as SSLSocket
        socket.soTimeout = 5_000
        val failed =
            try {
                socket.startHandshake()
                socket.outputStream.write(ByteArray(8))
                socket.inputStream.read() == -1
            } catch (_: IOException) {
                true
            } finally {
                socket.close()
            }
        assertTrue(failed)
    }

    // Terminates TLS with its own key towards the client and towards the host, and forwards the
    // plaintext in both directions: the position of a man in the middle who doesn't know the code.
    private inner class Relay(
        targetPort: Int,
    ) : Closeable {
        private val listener = ServerSocket(0, 1, loopback)
        private val sockets = Collections.synchronizedList(mutableListOf<Socket>())
        val port: Int = listener.localPort

        private val fromHost = ByteArrayOutputStream()

        @Volatile
        var forwarded = false
            private set

        // The first byte of each frame the host sent.
        fun hostStatuses(): List<Int> {
            val input = DataInputStream(synchronized(fromHost) { fromHost.toByteArray() }.inputStream())
            return generateSequence { if (input.available() > 0) Frames.read(input, Frames.CONTROL_LIMIT)[0].toInt() and 0xFF else null }
                .toList()
        }

        init {
            val relayIdentity = SyncIdentity.generate()
            thread(isDaemon = true) {
                try {
                    val inbound = listener.accept().also { sockets += it }
                    val towardsClient =
                        PinnedTls
                            .context(relayIdentity, PinTrustManager(expectedPin = null))
                            .socketFactory
                            .createSocket(inbound, null, inbound.port, true) as SSLSocket
                    towardsClient.useClientMode = false
                    towardsClient.needClientAuth = true
                    towardsClient.startHandshake()
                    val towardsHost =
                        PinnedTls
                            .context(relayIdentity, PinTrustManager(expectedPin = null))
                            .socketFactory
                            .createSocket(loopback, targetPort) as SSLSocket
                    sockets += towardsHost
                    towardsHost.startHandshake()
                    forwarded = true
                    pipe(towardsClient.inputStream, towardsHost.outputStream)
                    pipe(towardsHost.inputStream, TeeStream(towardsClient.outputStream, fromHost))
                } catch (_: IOException) {
                }
            }
        }

        private fun pipe(
            from: InputStream,
            to: OutputStream,
        ) = thread(isDaemon = true) {
            try {
                from.copyTo(to)
            } catch (_: IOException) {
            } finally {
                close()
            }
        }

        override fun close() {
            listener.close()
            synchronized(sockets) { sockets.toList() }.forEach(::closeQuietly)
        }
    }

    // Terminates TLS on both legs like Relay, but knows the code and rewrites both proofs for the
    // leg it sends them on: the strongest relay short of holding the devices' keys.
    private inner class CodeKnowingRelay(
        targetPort: Int,
        code: String,
    ) : Closeable {
        private val listener = ServerSocket(0, 1, loopback)
        private val sockets = Collections.synchronizedList(mutableListOf<Socket>())
        private val relayIdentity = SyncIdentity.generate()
        val port: Int = listener.localPort
        val pin: String = relayIdentity.pin

        init {
            thread(isDaemon = true) {
                try {
                    val inbound = listener.accept().also { sockets += it }
                    val towardsClient =
                        PinnedTls
                            .context(relayIdentity, PinTrustManager(expectedPin = null))
                            .socketFactory
                            .createSocket(inbound, null, inbound.port, true) as SSLSocket
                    towardsClient.useClientMode = false
                    towardsClient.needClientAuth = true
                    towardsClient.startHandshake()
                    val towardsHost =
                        PinnedTls
                            .context(relayIdentity, PinTrustManager(expectedPin = null))
                            .socketFactory
                            .createSocket(loopback, targetPort) as SSLSocket
                    sockets += towardsHost
                    towardsHost.startHandshake()
                    val fromClient = DataInputStream(towardsClient.inputStream)
                    val toClient = DataOutputStream(towardsClient.outputStream)
                    val fromHost = DataInputStream(towardsHost.inputStream)
                    val toHost = DataOutputStream(towardsHost.outputStream)
                    Frames.write(toHost, Frames.read(fromClient, Frames.CONTROL_LIMIT), Frames.CONTROL_LIMIT)
                    Frames.write(toClient, Frames.read(fromHost, Frames.CONTROL_LIMIT), Frames.CONTROL_LIMIT)
                    val hostHello = hostDevice.toHello()
                    val phoneHello = phoneDevice.toHello()
                    val hostLeg = PairingProof.transcript(hostIdentity.spki, relayIdentity.spki, hostHello, phoneHello)
                    val clientLeg = PairingProof.transcript(relayIdentity.spki, phoneIdentity.spki, hostHello, phoneHello)
                    Frames.read(fromClient, Frames.CONTROL_LIMIT)
                    Frames.write(toHost, PairingProof.client(code, hostLeg), Frames.CONTROL_LIMIT)
                    val (status, _) = Protocol.readStatus(fromHost)
                    Protocol.writeStatus(toClient, status, if (status == Protocol.OK) PairingProof.host(code, clientLeg) else ByteArray(0))
                    Frames.write(toHost, Frames.read(fromClient, Frames.CONTROL_LIMIT), Frames.CONTROL_LIMIT)
                    Frames.write(toClient, Frames.read(fromHost, Frames.CONTROL_LIMIT), Frames.CONTROL_LIMIT)
                } catch (_: IOException) {
                } finally {
                    close()
                }
            }
        }

        override fun close() {
            listener.close()
            synchronized(sockets) { sockets.toList() }.forEach(::closeQuietly)
        }
    }

    private class TeeStream(
        private val target: OutputStream,
        private val copy: ByteArrayOutputStream,
    ) : OutputStream() {
        override fun write(b: Int) {
            synchronized(copy) { copy.write(b) }
            target.write(b)
        }

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) {
            synchronized(copy) { copy.write(b, off, len) }
            target.write(b, off, len)
        }

        override fun flush() = target.flush()
    }
}
