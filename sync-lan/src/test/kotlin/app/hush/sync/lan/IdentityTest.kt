// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.lan

import java.io.File
import java.math.BigInteger
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.time.Instant
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdentityTest {
    private val dir: File = Files.createTempDirectory("hush-sync-identity").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `certificate parses as X509 v3 and verifies with its own key`() {
        val cert = SyncIdentity.generate().certificate
        val parsed = SelfSignedCert.parse(cert.encoded)
        parsed.verify(parsed.publicKey)
        assertEquals(3, parsed.version)
        assertEquals("1.2.840.10045.4.3.2", parsed.sigAlgOID)
        assertEquals("CN=hush", parsed.subjectX500Principal.name)
        assertEquals(parsed.subjectX500Principal, parsed.issuerX500Principal)
        assertTrue(parsed.serialNumber > BigInteger.ZERO)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), parsed.notBefore.toInstant())
        assertEquals(Instant.parse("2049-12-31T23:59:59Z"), parsed.notAfter.toInstant())
        assertEquals("EC", parsed.publicKey.algorithm)
    }

    @Test
    fun `pin is base64 SHA-256 of the SubjectPublicKeyInfo`() {
        val identity = SyncIdentity.generate()
        val expected =
            Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(identity.certificate.publicKey.encoded),
            )
        // On the JDK both encodings agree; the pin itself is computed from the certificate's bytes.
        assertContentEquals(identity.certificate.publicKey.encoded, identity.spki)
        assertEquals(expected, identity.pin)
        assertNotEquals(identity.pin, SyncIdentity.generate().pin)
    }

    @Test
    fun `identity is stored once and loaded again`() {
        val first = SyncIdentity.loadOrCreate(dir)
        val second = SyncIdentity.loadOrCreate(dir)
        assertEquals(first.pin, second.pin)
        assertContentEquals(first.privateKey.encoded, second.privateKey.encoded)
        assertEquals(setOf("sync-key.p8", "sync-cert.der"), dir.list()!!.toSet())
    }

    @Test
    fun `key files are owner only on POSIX file systems`() {
        SyncIdentity.loadOrCreate(dir)
        if ("posix" !in FileSystems.getDefault().supportedFileAttributeViews()) return
        for (name in listOf("sync-key.p8", "sync-cert.der")) {
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(File(dir, name).toPath()),
            )
        }
    }

    @Test
    fun `unreadable key files are replaced by a new identity`() {
        val first = SyncIdentity.loadOrCreate(dir)
        File(dir, "sync-key.p8").writeBytes(byteArrayOf(1, 2, 3))
        val second = SyncIdentity.loadOrCreate(dir)
        assertNotEquals(first.pin, second.pin)
        assertEquals(second.pin, SyncIdentity.loadOrCreate(dir).pin)
    }

    @Test
    fun `DER encodes object identifiers and long lengths`() {
        assertContentEquals(
            byteArrayOf(0x06, 0x08, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x04, 0x03, 0x02),
            Der.oid("1.2.840.10045.4.3.2"),
        )
        assertContentEquals(byteArrayOf(0x04, 0x81.toByte(), 0xC8.toByte()), Der.tlv(0x04, ByteArray(200)).copyOf(3))
        assertContentEquals(byteArrayOf(0x04, 0x82.toByte(), 0x01, 0x2C), Der.tlv(0x04, ByteArray(300)).copyOf(4))
    }

    @Test
    fun `SPKI is sliced from the certificate DER (OpenSSL vector)`() {
        // `openssl req -x509` P-256 certificate with extensions; SPKI and pin from
        // `openssl x509 -pubkey | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64`.
        val spki = SelfSignedCert.spki(hex(OPENSSL_CERT))
        assertContentEquals(hex(OPENSSL_SPKI), spki)
        assertEquals("/IgChCaa4b21YxwD4BaL4vxnnj1mRRfiZaRSU5nOP6Y=", pinOf(spki))
        assertEquals("/IgChCaa4b21YxwD4BaL4vxnnj1mRRfiZaRSU5nOP6Y=", pinOf(SelfSignedCert.parse(hex(OPENSSL_CERT)).spki()))
    }

    @Test
    fun `SPKI slicing rejects truncated or foreign DER`() {
        val cert = hex(OPENSSL_CERT)
        assertFailsWith<CertificateException> { SelfSignedCert.spki(cert.copyOf(100)) }
        assertFailsWith<CertificateException> { SelfSignedCert.spki(byteArrayOf(0x04, 0x01, 0x00)) }
        assertFailsWith<CertificateException> { SelfSignedCert.spki(ByteArray(0)) }
    }

    @Test
    fun `a key that doesn't match the stored certificate is replaced`() {
        val first = SyncIdentity.loadOrCreate(dir)
        val other = Files.createTempDirectory("hush-sync-other").toFile()
        try {
            SyncIdentity.loadOrCreate(other)
            // As if a crash left a new key next to the old certificate.
            File(other, "sync-key.p8").copyTo(File(dir, "sync-key.p8"), overwrite = true)
            val second = SyncIdentity.loadOrCreate(dir)
            assertNotEquals(first.pin, second.pin)
            assertTrue(SyncIdentity.matches(second.privateKey, second.certificate))
            assertEquals(second.pin, SyncIdentity.loadOrCreate(dir).pin)
        } finally {
            other.deleteRecursively()
        }
    }

    @Test
    fun `a new directory is owner only on POSIX file systems`() {
        val nested = File(dir, "data/sync")
        SyncIdentity.loadOrCreate(nested)
        if ("posix" !in FileSystems.getDefault().supportedFileAttributeViews()) return
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(nested.toPath()))
    }

    private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private companion object {
        const val OPENSSL_CERT =
            "3082016d30820113a0030201020214609fdc952ad4f873c0afdb9bc15233ba77583246300a06082a8648ce3d040302300c310a" +
                "300806035504030c0174301e170d3236313130373034353832305a170d3236313130383034353832305a300c310a3008060355" +
                "04030c01743059301306072a8648ce3d020106082a8648ce3d0301070342000468c3d3c2adfd22654f75102cf413310bf272" +
                "5153ea602f9776b6ea058cb34b9bfed7416efcccf1064c75364341787ec44bde6495112d6ae6a87347598262cfd1a3533051" +
                "301d0603551d0e04160414852acfdea729a97e0e208486a9c14b31fe7223bd301f0603551d23041830168014852acfdea729" +
                "a97e0e208486a9c14b31fe7223bd300f0603551d130101ff040530030101ff300a06082a8648ce3d04030203480030450220" +
                "278c3e16a0cdb1a6195795825cec36c77dfd29865859133959ae1e45b58db09c022100d82176de0253b0a3f7c08b59535904" +
                "aab7f1bf00f424ff708413c67b6de67a3d"
        const val OPENSSL_SPKI =
            "3059301306072a8648ce3d020106082a8648ce3d0301070342000468c3d3c2adfd22654f75102cf413310bf2725153ea602f" +
                "9776b6ea058cb34b9bfed7416efcccf1064c75364341787ec44bde6495112d6ae6a87347598262cfd1"
    }
}
