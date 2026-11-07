// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.sync

import app.hush.sync.lan.LocalDevice
import app.hush.sync.lan.SyncClient
import app.hush.sync.lan.SyncIdentity
import app.hush.sync.lan.SyncResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CancellableCallTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val client = SyncClient(SyncIdentity.generate(), LocalDevice("phone-id", "Phone"))

    @Test
    fun `cancelling closes the socket instead of waiting for the timeout`() =
        runBlocking<Unit> {
            // Accepts and never answers, so the TLS handshake blocks until the socket is closed.
            ServerSocket(0, 1, loopback).use { silent ->
                val started = CompletableDeferred<Unit>()
                val call =
                    async(Dispatchers.IO) {
                        cancellableCall { handle ->
                            started.complete(Unit)
                            client.sync(loopback, silent.localPort, SyncIdentity.generate().pin, byteArrayOf(1), handle)
                        }
                    }
                started.await()
                delay(200.milliseconds)
                withTimeout(5.seconds) { call.cancelAndJoin() }
                assertTrue(call.isCancelled)
            }
        }

    @Test
    fun `a call that isn't cancelled returns its result`() =
        runBlocking<Unit> {
            val result =
                withContext(Dispatchers.IO) {
                    cancellableCall { handle -> client.sync(InetAddress.getByName("8.8.8.8"), 1, "pin", byteArrayOf(1), handle) }
                }
            assertIs<SyncResult.Failed>(result)
        }
}
