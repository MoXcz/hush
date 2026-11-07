// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.lan

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** This device as announced to a peer. */
data class LocalDevice(
    val deviceId: String,
    val name: String,
)

/** A peer to store after pairing (one per install; persisting it is the caller's job). */
data class PeerInfo(
    val deviceId: String,
    val name: String,
    val pin: String,
)

internal enum class Mode(
    val code: Int,
) {
    PAIR(1),
    SYNC(2),
}

internal data class Hello(
    val deviceId: String,
    val deviceName: String,
)

internal fun LocalDevice.toHello() = Hello(deviceId, name.take(Protocol.MAX_FIELD))

internal class ProtocolException(
    message: String,
) : IOException(message)

// Every message is one frame. Hello and status frames are DataOutputStream fields, not JSON, and
// begin with the protocol version so an older peer can answer TooNew without parsing the rest.
internal object Protocol {
    const val VERSION = 1
    const val TIMEOUT_MILLIS = 15_000
    const val DEADLINE_MILLIS = 120_000L
    const val MAX_FIELD = 128

    // Server → client status, the first byte of its reply.
    const val OK = 0
    const val TOO_NEW = 1
    const val REJECTED = 2
    const val EXPIRED = 3
    const val WRONG_CODE = 4
    const val FAILED = 5

    // Client → server, last pairing step.
    const val CONFIRM = 0
    const val HOST_PROOF_WRONG = 1

    fun writeClientHello(
        out: DataOutputStream,
        mode: Mode,
        self: Hello,
        version: Int,
    ) = writeControl(out) {
        writeInt(version)
        writeByte(mode.code)
        writeUTF(self.deviceId)
        writeUTF(self.deviceName)
    }

    // Null when the client speaks another protocol version.
    fun readClientHello(
        input: DataInputStream,
        version: Int,
    ): Pair<Mode, Hello>? =
        readControl(input) {
            if (readInt() != version) return@readControl null
            val code = readUnsignedByte()
            val mode = Mode.entries.firstOrNull { it.code == code } ?: throw ProtocolException("mode")
            mode to readHelloFields()
        }

    fun writeServerHello(
        out: DataOutputStream,
        self: Hello,
        version: Int,
    ) = writeControl(out) {
        writeByte(OK)
        writeInt(version)
        writeUTF(self.deviceId)
        writeUTF(self.deviceName)
    }

    sealed interface ServerHello {
        data class Ok(
            val hello: Hello,
        ) : ServerHello

        data class Status(
            val status: Int,
        ) : ServerHello
    }

    fun readServerHello(
        input: DataInputStream,
        version: Int,
    ): ServerHello =
        readControl(input) {
            val status = readUnsignedByte()
            when {
                status != OK -> ServerHello.Status(status)
                readInt() != version -> ServerHello.Status(TOO_NEW)
                else -> ServerHello.Ok(readHelloFields())
            }
        }

    fun writeStatus(
        out: DataOutputStream,
        status: Int,
        extra: ByteArray = ByteArray(0),
    ) = writeControl(out) {
        writeByte(status)
        write(extra)
    }

    fun readStatus(input: DataInputStream): Pair<Int, ByteArray> {
        val frame = Frames.read(input, Frames.CONTROL_LIMIT)
        if (frame.isEmpty()) throw ProtocolException("empty status")
        return (frame[0].toInt() and 0xFF) to frame.copyOfRange(1, frame.size)
    }

    private fun DataInputStream.readHelloFields(): Hello {
        val id = readUTF()
        val name = readUTF()
        if (id.isBlank() || id.length > MAX_FIELD || name.length > MAX_FIELD) throw ProtocolException("hello")
        return Hello(id, name)
    }

    private fun writeControl(
        out: DataOutputStream,
        body: DataOutputStream.() -> Unit,
    ) {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { it.body() }
        Frames.write(out, bytes.toByteArray(), Frames.CONTROL_LIMIT)
    }

    private fun <T> readControl(
        input: DataInputStream,
        body: DataInputStream.() -> T,
    ): T = DataInputStream(Frames.read(input, Frames.CONTROL_LIMIT).inputStream()).body()
}

// Closes the socket once the deadline passes, so a peer that trickles bytes can't hold a
// connection open forever (the per-read timeout alone wouldn't stop it).
internal object Deadlines {
    private val timer =
        ScheduledThreadPoolExecutor(1) { task ->
            Executors.defaultThreadFactory().newThread(task).apply {
                name = "hush-sync-deadline"
                isDaemon = true
            }
        }.apply { removeOnCancelPolicy = true }

    fun <T> within(
        socket: Socket,
        millis: Long,
        block: () -> T,
    ): T {
        val closer: ScheduledFuture<*> = timer.schedule({ closeQuietly(socket) }, millis, TimeUnit.MILLISECONDS)
        try {
            return block()
        } finally {
            closer.cancel(false)
        }
    }
}

internal fun closeQuietly(socket: Socket) {
    try {
        socket.close()
    } catch (_: IOException) {
    }
}
