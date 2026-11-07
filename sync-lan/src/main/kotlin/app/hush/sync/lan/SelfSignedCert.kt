// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.lan

import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

// A minimal X.509 v3 certificate (no extensions) written as DER by hand, so no BouncyCastle.
// Peers trust it only through its pinned SPKI, never through the name or dates.
internal object SelfSignedCert {
    private const val ECDSA_WITH_SHA256 = "1.2.840.10045.4.3.2"
    private const val COMMON_NAME = "2.5.4.3"
    private const val NOT_BEFORE = "260101000000Z"
    private const val NOT_AFTER = "491231235959Z"

    fun create(
        keys: KeyPair,
        random: SecureRandom,
    ): X509Certificate {
        val algorithm = Der.sequence(Der.oid(ECDSA_WITH_SHA256))
        val name = Der.sequence(Der.set(Der.sequence(Der.oid(COMMON_NAME), Der.utf8("hush"))))
        val tbs =
            Der.sequence(
                Der.explicit(0, Der.integer(byteArrayOf(2))),
                Der.integer(serial(random)),
                algorithm,
                name,
                Der.sequence(Der.utcTime(NOT_BEFORE), Der.utcTime(NOT_AFTER)),
                name,
                keys.public.encoded,
            )
        val signature =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(keys.private, random)
                update(tbs)
                sign()
            }
        val der = Der.sequence(tbs, algorithm, Der.bitString(signature))
        return parse(der)
    }

    fun parse(der: ByteArray): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate

    // The SubjectPublicKeyInfo exactly as it appears in the certificate's DER. Pins and the pairing
    // binding use these bytes rather than PublicKey.encoded, which each TLS stack re-encodes itself.
    fun spki(certificateDer: ByteArray): ByteArray {
        val certificate = Der.read(certificateDer, 0)
        if (certificate.tag != 0x30 || certificate.end != certificateDer.size) throw CertificateException("certificate")
        val tbs = Der.read(certificateDer, certificate.contentStart)
        if (tbs.tag != 0x30) throw CertificateException("tbsCertificate")
        var element = Der.read(certificateDer, tbs.contentStart)
        // [0] version is optional; then serial, signature, issuer, validity, subject, SPKI.
        val skip = if (element.tag == 0xA0) 6 else 5
        repeat(skip) {
            if (element.end >= tbs.end) throw CertificateException("tbsCertificate fields")
            element = Der.read(certificateDer, element.end)
        }
        if (element.tag != 0x30 || element.end > tbs.end) throw CertificateException("subjectPublicKeyInfo")
        return certificateDer.copyOfRange(element.start, element.end)
    }

    // Positive and minimally encoded: the first byte is 0x40..0x7F.
    private fun serial(random: SecureRandom): ByteArray =
        ByteArray(8).also {
            random.nextBytes(it)
            it[0] = ((it[0].toInt() and 0x3F) or 0x40).toByte()
        }
}

internal object Der {
    class Element(
        val tag: Int,
        val start: Int,
        val contentStart: Int,
        val end: Int,
    )

    // Reads the TLV header at [offset] (definite lengths up to 4 bytes, single-byte tags).
    fun read(
        der: ByteArray,
        offset: Int,
    ): Element {
        fun byteAt(i: Int): Int = if (i < der.size) der[i].toInt() and 0xFF else throw CertificateException("truncated DER")
        val tag = byteAt(offset)
        val first = byteAt(offset + 1)
        var cursor = offset + 2
        val length =
            if (first < 0x80) {
                first
            } else {
                val count = first and 0x7F
                if (count == 0 || count > 4) throw CertificateException("DER length")
                var value = 0L
                repeat(count) { value = (value shl 8) or byteAt(cursor++).toLong() }
                if (value > Int.MAX_VALUE) throw CertificateException("DER length")
                value.toInt()
            }
        val end = cursor.toLong() + length
        if (end > der.size) throw CertificateException("truncated DER")
        return Element(tag, offset, cursor, end.toInt())
    }

    fun sequence(vararg parts: ByteArray) = tlv(0x30, concat(parts))

    fun set(vararg parts: ByteArray) = tlv(0x31, concat(parts))

    fun explicit(
        tag: Int,
        content: ByteArray,
    ) = tlv(0xA0 or tag, content)

    fun integer(value: ByteArray) = tlv(0x02, value)

    fun utf8(value: String) = tlv(0x0C, value.toByteArray(Charsets.UTF_8))

    fun utcTime(value: String) = tlv(0x17, value.toByteArray(Charsets.US_ASCII))

    fun bitString(value: ByteArray) = tlv(0x03, byteArrayOf(0) + value)

    fun oid(dotted: String): ByteArray {
        val arcs = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((arcs[0] * 40 + arcs[1]).toInt())
        for (arc in arcs.drop(2)) {
            var shift = 63 - java.lang.Long.numberOfLeadingZeros(arc or 1)
            shift -= shift % 7
            while (shift > 0) {
                out.write(((arc ushr shift) and 0x7F).toInt() or 0x80)
                shift -= 7
            }
            out.write((arc and 0x7F).toInt())
        }
        return tlv(0x06, out.toByteArray())
    }

    fun tlv(
        tag: Int,
        content: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val length = content.size
        when {
            length < 0x80 -> {
                out.write(length)
            }

            else -> {
                val bytes = generateSequence(length) { it ushr 8 }.takeWhile { it > 0 }.count()
                out.write(0x80 or bytes)
                for (i in bytes - 1 downTo 0) out.write(length ushr (8 * i))
            }
        }
        out.write(content)
        return out.toByteArray()
    }

    private fun concat(parts: Array<out ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        parts.forEach { out.write(it) }
        return out.toByteArray()
    }
}
