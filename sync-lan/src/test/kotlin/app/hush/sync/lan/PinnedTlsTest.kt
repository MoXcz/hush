// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.lan

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Mutual TLS on its own, for each protocol version: TLS 1.2 is all Android 8 and 9 have.
class PinnedTlsTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val server = SyncIdentity.generate()
    private val client = SyncIdentity.generate()
    private val pool = Executors.newSingleThreadExecutor()

    @AfterTest
    fun cleanUp() {
        pool.shutdownNow()
    }

    // Returns the client's pin as the server saw it.
    private fun handshake(
        protocol: String,
        serverTrust: PinTrustManager,
        clientTrust: PinTrustManager,
    ): String =
        ServerSocket(0, 1, loopback).use { listener ->
            val accepted =
                pool.submit(
                    Callable {
                        listener.accept().use { raw ->
                            val ssl =
                                PinnedTls
                                    .context(server, serverTrust)
                                    .socketFactory
                                    .createSocket(raw, null, raw.port, true) as SSLSocket
                            ssl.useClientMode = false
                            ssl.needClientAuth = true
                            ssl.enabledProtocols = arrayOf(protocol)
                            ssl.soTimeout = 5_000
                            ssl.startHandshake()
                            ssl.outputStream.write(1)
                            pinOf(
                                ssl.session.peerCertificates[0]
                                    .publicKey.encoded,
                            )
                        }
                    },
                )
            val ssl = PinnedTls.context(client, clientTrust).socketFactory.createSocket(loopback, listener.localPort) as SSLSocket
            ssl.use {
                it.enabledProtocols = arrayOf(protocol)
                it.soTimeout = 5_000
                it.startHandshake()
                assertEquals(protocol, it.session.protocol)
                assertEquals(1, it.inputStream.read())
            }
            accepted.get(5, TimeUnit.SECONDS)
        }

    @Test
    fun `mutual TLS 1_3 succeeds with the right pins`() {
        val seen = handshake("TLSv1.3", PinTrustManager(client.pin), PinTrustManager(server.pin))
        assertEquals(client.pin, seen)
    }

    @Test
    fun `mutual TLS 1_2 succeeds with the right pins`() {
        val seen = handshake("TLSv1.2", PinTrustManager(client.pin), PinTrustManager(server.pin))
        assertEquals(client.pin, seen)
    }

    @Test
    fun `client fails on the wrong server key`() {
        for (protocol in listOf("TLSv1.3", "TLSv1.2")) {
            val trust = PinTrustManager(SyncIdentity.generate().pin)
            assertFailsWith<IOException> { handshake(protocol, PinTrustManager(null), trust) }
            assertTrue(trust.pinMismatch, protocol)
        }
    }

    @Test
    fun `server fails on the wrong client key`() {
        for (protocol in listOf("TLSv1.3", "TLSv1.2")) {
            val trust = PinTrustManager(SyncIdentity.generate().pin)
            assertFailsWith<Exception> { handshake(protocol, trust, PinTrustManager(server.pin)) }
            assertTrue(trust.pinMismatch, protocol)
        }
    }

    @Test
    fun `pairing trust accepts any key and flags nothing`() {
        val trust = PinTrustManager(null)
        handshake("TLSv1.3", PinTrustManager(null), trust)
        assertFalse(trust.pinMismatch)
    }
}
