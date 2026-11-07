// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.lan

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager

// TLS whose only trust decision is the peer key's pin. The Extended variants are what both JSSE
// and Conscrypt use without wrapping them in their own hostname-checking adapter.
internal object PinnedTls {
    private val PROTOCOLS = listOf("TLSv1.3", "TLSv1.2")

    fun context(
        identity: SyncIdentity,
        trust: PinTrustManager,
    ): SSLContext =
        SSLContext.getInstance("TLS").apply {
            init(arrayOf<KeyManager>(IdentityKeyManager(identity)), arrayOf<TrustManager>(trust), null)
        }

    // API 26 to 28 have no TLS 1.3, so only ask for what the platform supports.
    fun configure(socket: SSLSocket) {
        socket.enabledProtocols = PROTOCOLS.filter { it in socket.supportedProtocols }.toTypedArray()
    }
}

// Accepts exactly expectedPin, or any key when it is null (pairing, and every server handshake:
// the server checks the pin after Hello, when it knows the mode). Pinning a self-signed key is the
// point, so Android lint's CustomX509TrustManager warning doesn't apply.
@Suppress("CustomX509TrustManager")
internal class PinTrustManager(
    private val expectedPin: String?,
) : X509ExtendedTrustManager() {
    @Volatile
    var pinMismatch = false
        private set

    private fun check(chain: Array<out X509Certificate>?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("no peer certificate")
        // Always sliced, so a certificate we can't read fails the handshake, not a later step.
        val pin = pinOf(leaf.spki())
        if (expectedPin != null && !pinsEqual(pin, expectedPin)) {
            pinMismatch = true
            throw CertificateException("peer key does not match the pin")
        }
    }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) = check(chain)

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) = check(chain)

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) = check(chain)

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) = check(chain)

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) = check(chain)

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) = check(chain)

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private class IdentityKeyManager(
    private val identity: SyncIdentity,
) : X509ExtendedKeyManager() {
    private fun aliasFor(keyType: String?): String? = ALIAS.takeIf { keyType == null || keyType in EC_KEY_TYPES }

    private fun aliasFor(keyTypes: Array<out String>?): String? = ALIAS.takeIf { keyTypes == null || keyTypes.any { it in EC_KEY_TYPES } }

    override fun getClientAliases(
        keyType: String?,
        issuers: Array<out Principal>?,
    ): Array<String>? = aliasFor(keyType)?.let { arrayOf(it) }

    override fun chooseClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?,
    ): String? = aliasFor(keyType)

    override fun chooseEngineClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        engine: SSLEngine?,
    ): String? = aliasFor(keyType)

    override fun getServerAliases(
        keyType: String?,
        issuers: Array<out Principal>?,
    ): Array<String>? = aliasFor(keyType)?.let { arrayOf(it) }

    override fun chooseServerAlias(
        keyType: String?,
        issuers: Array<out Principal>?,
        socket: Socket?,
    ): String? = aliasFor(keyType)

    override fun chooseEngineServerAlias(
        keyType: String?,
        issuers: Array<out Principal>?,
        engine: SSLEngine?,
    ): String? = aliasFor(keyType)

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? = if (alias == ALIAS) arrayOf(identity.certificate) else null

    override fun getPrivateKey(alias: String?): PrivateKey? = if (alias == ALIAS) identity.privateKey else null

    private companion object {
        const val ALIAS = "hush"

        // "EC" in JSSE and Conscrypt, "EC_EC" for TLS 1.2 ECDH suites in older JSSE.
        val EC_KEY_TYPES = setOf("EC", "EC_EC", "ECDSA")
    }
}
