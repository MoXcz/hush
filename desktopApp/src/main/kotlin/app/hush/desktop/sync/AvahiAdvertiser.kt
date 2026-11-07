// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.sync

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal const val SERVICE_TYPE = "_hush._tcp"

// mDNS instance names are at most 63 bytes.
private const val MAX_SERVICE_NAME = 63

/** The `avahi-publish` command line that announces the host; `pair=1` while a code is shown. */
internal fun avahiArguments(
    hostName: String,
    port: Int,
    deviceId: String,
    pairing: Boolean,
    executable: String = "avahi-publish",
): List<String> =
    buildList {
        add(executable)
        add("-s")
        add(serviceName(hostName))
        add(SERVICE_TYPE)
        add(port.toString())
        add("id=$deviceId")
        if (pairing) add("pair=1")
    }

internal fun serviceName(hostName: String): String {
    val name = "Hush on $hostName"
    if (name.toByteArray(Charsets.UTF_8).size <= MAX_SERVICE_NAME) return name
    var cut = name
    while (cut.toByteArray(Charsets.UTF_8).size > MAX_SERVICE_NAME) cut = cut.dropLast(1)
    return cut
}

// Runs `avahi-publish` as a child process rather than adding a JVM mDNS library.
internal class AvahiAdvertiser(
    private val hostName: String,
    private val port: Int,
    private val deviceId: String,
    private val onAdvertising: (Boolean) -> Unit,
    private val executable: String = "avahi-publish",
) : Closeable {
    private val lock = ReentrantLock()
    private val wake = lock.newCondition()
    private var process: Process? = null
    private var pairing = false
    private var restart = false
    private var closed = false
    private var thread: Thread? = null

    // A crash or a kill of the app must not leave an orphan announcing a dead port.
    private val shutdownHook = Thread { lock.withLock { process }?.destroy() }

    fun start() {
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        thread =
            Thread(::run, "hush-avahi").apply {
                isDaemon = true
                start()
            }
    }

    /** Announces `pair=1` (or stops announcing it) by starting avahi-publish again. */
    fun setPairing(value: Boolean) {
        lock.withLock {
            if (pairing == value || closed) return
            pairing = value
            restart = true
            process?.destroy()
            wake.signalAll()
        }
    }

    override fun close() {
        lock.withLock {
            if (closed) return
            closed = true
            process?.destroy()
            wake.signalAll()
        }
        thread?.join(STOP_WAIT_MILLIS)
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        } catch (_: IllegalStateException) {
            // Already shutting down.
        }
    }

    private fun run() {
        var backoff = MIN_BACKOFF_MILLIS
        while (true) {
            val arguments =
                lock.withLock {
                    if (closed) return
                    restart = false
                    avahiArguments(hostName, port, deviceId, pairing, executable)
                }
            val started = System.currentTimeMillis()
            val established = publish(arguments)
            lock.withLock {
                if (closed) return
                if (restart) return@withLock
                onAdvertising(false)
                // A long healthy run starts the pauses over; repeated failures wait longer each time.
                backoff = if (established && System.currentTimeMillis() - started > HEALTHY_MILLIS) MIN_BACKOFF_MILLIS else backoff
                wake.await(backoff, TimeUnit.MILLISECONDS)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
            }
        }
    }

    // Runs one avahi-publish until it exits. True if it reported the service as established.
    private fun publish(arguments: List<String>): Boolean {
        val child =
            lock.withLock {
                if (closed || restart) return false
                try {
                    ProcessBuilder(arguments).redirectErrorStream(true).start().also { process = it }
                } catch (_: IOException) {
                    return false
                }
            }
        var established = false
        try {
            child.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (!established && line.startsWith("Established")) {
                        established = true
                        onAdvertising(true)
                    }
                }
            }
        } catch (_: IOException) {
            // The stream closes when the process is destroyed.
        }
        child.waitFor()
        lock.withLock { if (process === child) process = null }
        return established
    }

    private companion object {
        const val MIN_BACKOFF_MILLIS = 1_000L
        const val MAX_BACKOFF_MILLIS = 60_000L
        const val HEALTHY_MILLIS = 30_000L
        const val STOP_WAIT_MILLIS = 2_000L
    }
}
