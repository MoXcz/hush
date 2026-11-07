// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

import app.hush.core.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Path
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Follows the focused window through Hyprland's event socket into [log] between start and stop. */
class HyprlandWatcher(
    socketDirectory: Path,
    private val log: FocusLog,
    private val scope: CoroutineScope,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
) : FocusWatcher {
    private val events = UnixDomainSocketAddress.of(socketDirectory.resolve(".socket2.sock"))
    private val requests = UnixDomainSocketAddress.of(socketDirectory.resolve(".socket.sock"))
    private var job: Job? = null
    private val recording = Any()
    private var stopped = true

    override fun start() {
        synchronized(recording) {
            if (job?.isActive == true) return
            stopped = false
            job = scope.launch(dispatchers.io) { watch() }
        }
    }

    // The watcher thread may still be mid-read after cancel, so later records are dropped under the
    // same lock; otherwise a window could follow the closing gap and count until the next start.
    override fun stop() {
        synchronized(recording) {
            if (stopped) return
            stopped = true
            log.record(null)
            job?.cancel()
            job = null
        }
    }

    internal fun record(window: FocusedWindow?) {
        synchronized(recording) {
            if (!stopped) log.record(window)
        }
    }

    private suspend fun watch() {
        var backoff = MIN_BACKOFF
        while (true) {
            val started = clock.now()
            try {
                runInterruptible { follow() }
            } catch (_: IOException) {
                // Hyprland restarted or the socket isn't there; try again after a pause.
            }
            // Time while not watching is unknown, so it counts for nothing.
            record(null)
            if (clock.now() - started > HEALTHY) backoff = MIN_BACKOFF
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF)
        }
    }

    private fun follow() {
        SocketChannel.open(events).use { channel ->
            // Read after connecting, so no change falls between the two.
            record(activeWindow())
            Channels.newInputStream(channel).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line -> parseEvent(line)?.let { record(it.window) } }
            }
        }
    }

    // Hyprland answers one request per connection and closes it.
    private fun activeWindow(): FocusedWindow? =
        SocketChannel.open(requests).use { channel ->
            channel.write(ByteBuffer.wrap("activewindow".toByteArray()))
            parseActiveWindow(Channels.newInputStream(channel).readBytes().toString(Charsets.UTF_8))
        }

    private companion object {
        val MIN_BACKOFF = 1.seconds
        val MAX_BACKOFF = 1.minutes
        val HEALTHY = 30.seconds
    }
}
