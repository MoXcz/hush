// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.lan

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadFactory
import javax.net.ssl.SSLSocket

/**
 * The host side (desktop): listens for local clients over mutual TLS and answers PAIR and SYNC
 * requests. Blocking; every connection runs on its own daemon thread, at most four at a time.
 * Connections from addresses that aren't local or on this host's own links are closed before the TLS
 * handshake.
 */
class SyncServer(
    private val identity: SyncIdentity,
    self: LocalDevice,
    private val pairing: PairingCodes,
    private val handler: Handler,
    private val onError: (Throwable) -> Unit = {},
) : Closeable {
    /** What the host does with requests. Called on connection threads. */
    interface Handler {
        /** The stored peer, or null when unpaired. Only this peer's key may SYNC. */
        fun pairedPeer(): PeerInfo?

        /** Pairing finished on both sides: store [peer], replacing any previous one. Throw if that fails. */
        fun onPaired(peer: PeerInfo)

        /**
         * A SYNC from the paired [peer]: apply [incoming] and return the bytes to send back. Throw to
         * refuse it: the connection is closed without an answer (the client gets
         * [SyncResult.Failed]) and the exception goes to `onError`.
         */
        fun serve(
            peer: PeerInfo,
            incoming: ByteArray,
        ): ByteArray
    }

    private val hello = self.toHello()
    private val context = PinnedTls.context(identity, PinTrustManager(expectedPin = null))
    private val slots = Semaphore(MAX_CONNECTIONS)
    private val open = Collections.synchronizedSet(HashSet<Socket>())
    private var workers: ExecutorService? = null

    @Volatile
    private var listener: ServerSocket? = null

    internal var protocolVersion = Protocol.VERSION

    internal var acceptsFrom: (InetAddress) -> Boolean = LocalAddress::isLocalHere

    /** The port actually bound, or -1 before [start]. */
    val port: Int get() = listener?.localPort ?: -1

    /**
     * Binds [preferredPort] (any port if it is taken) on [bindAddress] (all interfaces when null),
     * starts accepting and returns the bound port.
     */
    @Synchronized
    @Throws(IOException::class)
    fun start(
        preferredPort: Int = DEFAULT_PORT,
        bindAddress: InetAddress? = null,
    ): Int {
        check(listener == null) { "already started" }
        val socket =
            try {
                ServerSocket(preferredPort, BACKLOG, bindAddress)
            } catch (_: BindException) {
                ServerSocket(0, BACKLOG, bindAddress)
            }
        val pool = Executors.newCachedThreadPool(daemonThreads("hush-sync-connection"))
        workers = pool
        listener = socket
        daemonThreads("hush-sync-accept").newThread { acceptLoop(socket, pool) }.start()
        return socket.localPort
    }

    /** Stops listening and drops open connections. */
    @Synchronized
    override fun close() {
        listener?.close()
        workers?.shutdownNow()
        synchronized(open) { open.toList() }.forEach(::closeQuietly)
    }

    private fun acceptLoop(
        server: ServerSocket,
        pool: ExecutorService,
    ) {
        while (!server.isClosed) {
            val socket =
                try {
                    server.accept()
                } catch (e: IOException) {
                    if (server.isClosed) break
                    onError(e)
                    // e.g. out of file descriptors: don't spin.
                    try {
                        Thread.sleep(ACCEPT_BACKOFF_MILLIS)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }
            if (!acceptsFrom(socket.inetAddress) || !slots.tryAcquire()) {
                closeQuietly(socket)
                continue
            }
            open += socket
            try {
                pool.execute {
                    try {
                        handle(socket)
                    } catch (e: Exception) {
                        onError(e)
                    } finally {
                        closeQuietly(socket)
                        open -= socket
                        slots.release()
                    }
                }
            } catch (_: RejectedExecutionException) {
                closeQuietly(socket)
                open -= socket
                slots.release()
            }
        }
    }

    private fun handle(raw: Socket) =
        Deadlines.within(raw, Protocol.DEADLINE_MILLIS) {
            raw.soTimeout = Protocol.TIMEOUT_MILLIS
            val ssl = context.socketFactory.createSocket(raw, raw.inetAddress.hostAddress, raw.port, true) as SSLSocket
            try {
                ssl.useClientMode = false
                ssl.needClientAuth = true
                PinnedTls.configure(ssl)
                ssl.startHandshake()
                val peerSpki = (ssl.session.peerCertificates[0] as X509Certificate).spki()
                val input = DataInputStream(BufferedInputStream(ssl.inputStream))
                val out = DataOutputStream(BufferedOutputStream(ssl.outputStream))
                val request = Protocol.readClientHello(input, protocolVersion)
                when {
                    request == null -> Protocol.writeStatus(out, Protocol.TOO_NEW)
                    request.first == Mode.SYNC -> sync(input, out, request.second, peerSpki)
                    else -> pair(input, out, request.second, peerSpki)
                }
            } finally {
                // Frees the TLS engine's state too (native in Conscrypt), not just the socket.
                closeQuietly(ssl)
            }
        }

    private fun sync(
        input: DataInputStream,
        out: DataOutputStream,
        client: Hello,
        clientSpki: ByteArray,
    ) {
        val peer = handler.pairedPeer()
        if (peer == null || !pinsEqual(pinOf(clientSpki), peer.pin) || peer.deviceId != client.deviceId) {
            Protocol.writeStatus(out, Protocol.REJECTED)
            return
        }
        Protocol.writeServerHello(out, hello, protocolVersion)
        val incoming = Frames.read(input, Frames.PAYLOAD_LIMIT)
        Frames.write(out, handler.serve(peer, incoming), Frames.PAYLOAD_LIMIT)
        Protocol.readStatus(input)
    }

    private fun pair(
        input: DataInputStream,
        out: DataOutputStream,
        client: Hello,
        clientSpki: ByteArray,
    ) {
        when (pairing.state()) {
            PairingCodes.State.Open -> {
                Unit
            }

            PairingCodes.State.Expired, PairingCodes.State.Closed -> {
                Protocol.writeStatus(out, Protocol.EXPIRED)
                return
            }
        }
        Protocol.writeServerHello(out, hello, protocolVersion)
        val transcript = PairingProof.transcript(identity.spki, clientSpki, hello, client)
        val clientProof = Frames.read(input, Frames.CONTROL_LIMIT)
        // The code is used up only now, when a proof arrives, so a bare Hello can't rotate it.
        val code =
            when (val attempt = pairing.attempt { MessageDigest.isEqual(clientProof, PairingProof.client(it, transcript)) }) {
                is PairingCodes.Attempt.Accepted -> {
                    attempt.code
                }

                PairingCodes.Attempt.WrongCode -> {
                    Protocol.writeStatus(out, Protocol.WRONG_CODE)
                    return
                }

                PairingCodes.Attempt.Expired, PairingCodes.Attempt.Closed -> {
                    Protocol.writeStatus(out, Protocol.EXPIRED)
                    return
                }
            }
        Protocol.writeStatus(out, Protocol.OK, PairingProof.host(code, transcript))
        if (Protocol.readStatus(input).first != Protocol.CONFIRM) return
        try {
            handler.onPaired(PeerInfo(client.deviceId, client.deviceName, pinOf(clientSpki)))
        } catch (e: Exception) {
            Protocol.writeStatus(out, Protocol.FAILED)
            throw e
        }
        pairing.close()
        Protocol.writeStatus(out, Protocol.OK)
    }

    companion object {
        /** The port the host tries first. */
        const val DEFAULT_PORT = 47447
        private const val BACKLOG = 8
        private const val MAX_CONNECTIONS = 4
        private const val ACCEPT_BACKOFF_MILLIS = 100L

        private fun daemonThreads(name: String) =
            ThreadFactory { task ->
                Thread(task, name).apply { isDaemon = true }
            }
    }
}
