// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.lan

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A pairing code on screen. [code] is the 12 plain characters; [display] groups them as `XXXX-XXXX-XXXX`. */
data class ShownCode(
    val code: String,
    val expiresAtMillis: Long,
) {
    val display: String get() = code.chunked(4).joinToString("-")
}

/**
 * The host's pairing code: 12 Crockford base32 characters (60 bits) from [random], valid for
 * [validMillis] and for one attempt. Any checked proof, right or wrong, and any expiry replace it, and
 * [onChange] is called with the new code (null once closed). Thread-safe.
 */
class PairingCodes(
    private val validMillis: Long = DEFAULT_VALID_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val onChange: (ShownCode?) -> Unit = {},
) {
    private var shown: ShownCode? = null

    /** Shows a fresh code (replacing any current one) and returns it. */
    fun open(): ShownCode = update { next() }!!

    /** The code to show now, rotated first if it expired; null when pairing is closed. */
    fun current(): ShownCode? =
        update { current ->
            if (current != null && clock() >= current.expiresAtMillis) next() else current
        }

    /** Stops pairing: no code is shown and pairing attempts are refused. */
    fun close() {
        update { null }
    }

    internal enum class State { Open, Expired, Closed }

    // Whether an attempt may start. Doesn't use up the code (so a bare Hello can't rotate it), but
    // rotates one that has expired.
    internal fun state(): State {
        var state = State.Closed
        update { current ->
            when {
                current == null -> {
                    null
                }

                clock() >= current.expiresAtMillis -> {
                    state = State.Expired
                    next()
                }

                else -> {
                    state = State.Open
                    current
                }
            }
        }
        return state
    }

    internal sealed interface Attempt {
        data class Accepted(
            val code: String,
        ) : Attempt

        data object WrongCode : Attempt

        data object Expired : Attempt

        data object Closed : Attempt
    }

    // Checks a client's proof against the current code and uses the code up, right or wrong. One
    // lock, so of two proofs arriving together only the first can match.
    internal fun attempt(proofMatches: (code: String) -> Boolean): Attempt {
        var attempt: Attempt = Attempt.Closed
        update { current ->
            when {
                current == null -> {
                    null
                }

                clock() >= current.expiresAtMillis -> {
                    attempt = Attempt.Expired
                    next()
                }

                else -> {
                    attempt = if (proofMatches(current.code)) Attempt.Accepted(current.code) else Attempt.WrongCode
                    next()
                }
            }
        }
        return attempt
    }

    private fun next() = ShownCode(generate(random), clock() + validMillis)

    private fun update(change: (ShownCode?) -> ShownCode?): ShownCode? {
        val (before, after) =
            synchronized(this) {
                val before = shown
                shown = change(before)
                before to shown
            }
        if (before != after) onChange(after)
        return after
    }

    companion object {
        const val DEFAULT_VALID_MILLIS = 120_000L
        const val LENGTH = 12
        internal const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

        internal fun generate(random: SecureRandom): String = String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

        /**
         * What the user typed, as a code: upper case, dashes and spaces removed, `O` read as `0` and
         * `I`/`L` as `1`. Null when it isn't 12 characters of the alphabet.
         */
        fun normalize(input: String): String? {
            val code =
                input
                    .uppercase()
                    .filterNot { it == '-' || it.isWhitespace() }
                    .map {
                        when (it) {
                            'O' -> '0'
                            'I', 'L' -> '1'
                            else -> it
                        }
                    }.joinToString("")
            return code.takeIf { it.length == LENGTH && it.all { c -> c in ALPHABET } }
        }
    }
}

// Both sides derive the same binding from their own view of the TLS keys and Hellos. A relay that
// terminates TLS has different keys on each leg, so the two views (and the HMACs) differ.
internal object PairingProof {
    private const val LABEL = "hush-pair-v1"

    fun transcript(
        hostSpki: ByteArray,
        clientSpki: ByteArray,
        host: Hello,
        client: Hello,
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            fun field(value: ByteArray) {
                out.writeInt(value.size)
                out.write(value)
            }
            field(LABEL.toByteArray(Charsets.US_ASCII))
            field(hostSpki)
            field(clientSpki)
            field(host.deviceId.toByteArray(Charsets.UTF_8))
            field(client.deviceId.toByteArray(Charsets.UTF_8))
            field(host.deviceName.toByteArray(Charsets.UTF_8))
            field(client.deviceName.toByteArray(Charsets.UTF_8))
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
    }

    fun client(
        code: String,
        transcript: ByteArray,
    ) = hmac(code, "client", transcript)

    fun host(
        code: String,
        transcript: ByteArray,
    ) = hmac(code, "host", transcript)

    private fun hmac(
        code: String,
        role: String,
        transcript: ByteArray,
    ): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(code.toByteArray(Charsets.US_ASCII), "HmacSHA256"))
            update(role.toByteArray(Charsets.US_ASCII))
            doFinal(transcript)
        }
}
