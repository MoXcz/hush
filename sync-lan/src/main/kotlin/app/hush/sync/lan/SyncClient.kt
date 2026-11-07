// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.lan

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/** How a pairing attempt ended. */
sealed interface PairResult {
    /** Both sides confirmed the code and the host stored this device; store [peer]. */
    data class Paired(
        val peer: PeerInfo,
    ) : PairResult

    /** The code was mistyped or wrong (the host has shown a new one). */
    data object WrongCode : PairResult

    /** The code had expired, or the host isn't showing one any more. */
    data object Expired : PairResult

    /** The host speaks another protocol version. */
    data object TooNew : PairResult

    /** Not a local address, no connection, a broken exchange, or the host failed to store. */
    data class Failed(
        val message: String,
    ) : PairResult
}

/** How a sync exchange ended. */
sealed interface SyncResult {
    /** The host's payload. The host has received ours too. */
    class Received(
        val payload: ByteArray,
    ) : SyncResult

    /**
     * The host's key isn't the pinned one, or the host doesn't know this device. Show it and offer to
     * pair again, but don't delete the stored peer because of it: anyone on the network can answer
     * with a wrong key and force this result.
     */
    data object Rejected : SyncResult

    /** The host speaks another protocol version. */
    data object TooNew : SyncResult

    /** Not a local address, no connection or a broken exchange. Payloads are opaque here. */
    data class Failed(
        val message: String,
    ) : SyncResult
}

/** Aborts one [SyncClient] call from another thread by closing its socket (blocking sockets ignore interrupts). */
class CallHandle : Closeable {
    private var socket: Socket? = null
    private var closed = false

    @Synchronized
    internal fun attach(socket: Socket): Boolean {
        if (closed) return false
        this.socket = socket
        return true
    }

    @Synchronized
    override fun close() {
        closed = true
        socket?.let(::closeQuietly)
    }
}

/**
 * Mutual-TLS client for a host on the local network. Blocking: call it off the main thread; every
 * call ends within two minutes, or once its [CallHandle] is closed. [newSocket] may bind the socket
 * to a specific network.
 */
class SyncClient(
    private val identity: SyncIdentity,
    self: LocalDevice,
    private val newSocket: () -> Socket = { Socket() },
) {
    private val hello = self.toHello()

    internal var protocolVersion = Protocol.VERSION

    /** Pairs with the host at [host]:[port] using the [code] it shows (any case, dashes allowed). */
    fun pair(
        host: InetAddress,
        port: Int,
        code: String,
        handle: CallHandle? = null,
    ): PairResult {
        val normalized = PairingCodes.normalize(code) ?: return PairResult.WrongCode
        val trust = PinTrustManager(expectedPin = null)
        return connect(host, port, trust, handle, onFailure = { PairResult.Failed(it) }) { ssl, input, out ->
            Protocol.writeClientHello(out, Mode.PAIR, hello, protocolVersion)
            val server =
                when (val reply = Protocol.readServerHello(input, protocolVersion)) {
                    is Protocol.ServerHello.Ok -> reply.hello
                    is Protocol.ServerHello.Status -> return@connect pairStatus(reply.status)
                }
            val serverSpki = (ssl.session.peerCertificates[0] as X509Certificate).spki()
            val transcript = PairingProof.transcript(serverSpki, identity.spki, server, hello)
            Frames.write(out, PairingProof.client(normalized, transcript), Frames.CONTROL_LIMIT)
            val (status, hostProof) = Protocol.readStatus(input)
            if (status != Protocol.OK) return@connect pairStatus(status)
            if (!MessageDigest.isEqual(hostProof, PairingProof.host(normalized, transcript))) {
                Protocol.writeStatus(out, Protocol.HOST_PROOF_WRONG)
                return@connect PairResult.Failed("the host could not prove the code")
            }
            Protocol.writeStatus(out, Protocol.CONFIRM)
            val (stored, _) = Protocol.readStatus(input)
            if (stored != Protocol.OK) return@connect PairResult.Failed("the host could not store the pairing")
            PairResult.Paired(PeerInfo(server.deviceId, server.deviceName, pinOf(serverSpki)))
        }
    }

    /** Sends [payload] to the paired host whose key has [peerPin] and returns the host's payload. */
    fun sync(
        host: InetAddress,
        port: Int,
        peerPin: String,
        payload: ByteArray,
        handle: CallHandle? = null,
    ): SyncResult {
        if (payload.size > Frames.PAYLOAD_LIMIT) return SyncResult.Failed("payload too large")
        val trust = PinTrustManager(expectedPin = peerPin)
        return connect(host, port, trust, handle, onFailure = {
            if (trust.pinMismatch) SyncResult.Rejected else SyncResult.Failed(it)
        }) { _, input, out ->
            Protocol.writeClientHello(out, Mode.SYNC, hello, protocolVersion)
            when (val reply = Protocol.readServerHello(input, protocolVersion)) {
                is Protocol.ServerHello.Ok -> {
                    Unit
                }

                is Protocol.ServerHello.Status -> {
                    return@connect when (reply.status) {
                        Protocol.TOO_NEW -> SyncResult.TooNew
                        Protocol.REJECTED -> SyncResult.Rejected
                        else -> SyncResult.Failed("status ${reply.status}")
                    }
                }
            }
            Frames.write(out, payload, Frames.PAYLOAD_LIMIT)
            val received = Frames.read(input, Frames.PAYLOAD_LIMIT)
            Protocol.writeStatus(out, Protocol.OK)
            SyncResult.Received(received)
        }
    }

    private fun pairStatus(status: Int): PairResult =
        when (status) {
            Protocol.TOO_NEW -> PairResult.TooNew
            Protocol.EXPIRED -> PairResult.Expired
            Protocol.WRONG_CODE -> PairResult.WrongCode
            else -> PairResult.Failed("status $status")
        }

    private fun <T> connect(
        host: InetAddress,
        port: Int,
        trust: PinTrustManager,
        handle: CallHandle?,
        onFailure: (String) -> T,
        exchange: (SSLSocket, DataInputStream, DataOutputStream) -> T,
    ): T {
        if (!LocalAddress.isLocalHere(host)) return onFailure("not a local address: ${host.hostAddress}")
        // A fresh context per call: no session cache, so nothing is resumed across pins.
        val context = PinnedTls.context(identity, trust)
        val raw = newSocket()
        if (handle != null && !handle.attach(raw)) {
            closeQuietly(raw)
            return onFailure("cancelled")
        }
        return try {
            Deadlines.within(raw, Protocol.DEADLINE_MILLIS) {
                raw.soTimeout = Protocol.TIMEOUT_MILLIS
                raw.connect(InetSocketAddress(host, port), Protocol.TIMEOUT_MILLIS)
                val ssl = context.socketFactory.createSocket(raw, host.hostAddress, port, true) as SSLSocket
                try {
                    ssl.useClientMode = true
                    PinnedTls.configure(ssl)
                    ssl.startHandshake()
                    exchange(
                        ssl,
                        DataInputStream(BufferedInputStream(ssl.inputStream)),
                        DataOutputStream(BufferedOutputStream(ssl.outputStream)),
                    )
                } finally {
                    // Frees the TLS engine's state too (native in Conscrypt), not just the socket.
                    closeQuietly(ssl)
                }
            }
        } catch (e: SSLException) {
            onFailure("TLS: ${e.message}")
        } catch (e: IOException) {
            onFailure(e.toString())
        } finally {
            closeQuietly(raw)
        }
    }
}
