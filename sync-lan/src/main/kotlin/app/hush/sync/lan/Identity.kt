// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.lan

import java.io.File
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermissions
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * This install's TLS identity: an EC P-256 key and a self-signed certificate. Peers recognise it by
 * [pin], the Base64 SHA-256 of the certificate's SubjectPublicKeyInfo.
 */
class SyncIdentity internal constructor(
    internal val privateKey: PrivateKey,
    internal val certificate: X509Certificate,
) {
    internal val spki: ByteArray = certificate.spki()

    val pin: String = pinOf(spki)

    companion object {
        private const val KEY_FILE = "sync-key.p8"
        private const val CERT_FILE = "sync-cert.der"

        /** Creates a new identity in memory. */
        fun generate(random: SecureRandom = SecureRandom()): SyncIdentity {
            val keys =
                KeyPairGenerator.getInstance("EC").run {
                    initialize(ECGenParameterSpec("secp256r1"), random)
                    generateKeyPair()
                }
            return SyncIdentity(keys.private, SelfSignedCert.create(keys, random))
        }

        /**
         * Loads the identity stored in [directory], or creates and stores a new one when either file
         * is missing or unreadable (peers then have to pair again). Files are written owner-only
         * where the file system supports POSIX permissions.
         */
        @Throws(IOException::class)
        fun loadOrCreate(directory: File): SyncIdentity {
            val keyFile = File(directory, KEY_FILE)
            val certFile = File(directory, CERT_FILE)
            load(keyFile, certFile)?.let { return it }
            val identity = generate()
            createPrivateDirectory(directory.toPath())
            // Cert last: a crash in between leaves a new key with the old cert, which load() rejects.
            writeAtomically(keyFile, identity.privateKey.encoded)
            writeAtomically(certFile, identity.certificate.encoded)
            return identity
        }

        private fun load(
            keyFile: File,
            certFile: File,
        ): SyncIdentity? {
            if (!keyFile.isFile || !certFile.isFile) return null
            return try {
                val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                val certificate = SelfSignedCert.parse(certFile.readBytes())
                if (matches(key, certificate)) SyncIdentity(key, certificate) else null
            } catch (_: GeneralSecurityException) {
                null
            } catch (_: IOException) {
                null
            }
        }

        internal fun matches(
            key: PrivateKey,
            certificate: X509Certificate,
        ): Boolean {
            val probe = "hush-identity-check".toByteArray(Charsets.US_ASCII)
            val signature =
                Signature.getInstance("SHA256withECDSA").run {
                    initSign(key)
                    update(probe)
                    sign()
                }
            return Signature.getInstance("SHA256withECDSA").run {
                initVerify(certificate.publicKey)
                update(probe)
                verify(signature)
            }
        }

        private fun ownerOnly(permissions: String): Array<FileAttribute<*>> =
            if ("posix" in FileSystems.getDefault().supportedFileAttributeViews()) {
                arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(permissions)))
            } else {
                emptyArray()
            }

        private fun createPrivateDirectory(path: Path) {
            if (Files.isDirectory(path)) return
            try {
                Files.createDirectories(path, *ownerOnly("rwx------"))
            } catch (_: UnsupportedOperationException) {
                Files.createDirectories(path)
            }
        }

        // The rename keeps a half-written key from loading.
        private fun writeAtomically(
            target: File,
            bytes: ByteArray,
        ) {
            val directory = target.parentFile.toPath()
            val temp =
                try {
                    Files.createTempFile(directory, target.name, ".tmp", *ownerOnly("rw-------"))
                } catch (_: UnsupportedOperationException) {
                    Files.createTempFile(directory, target.name, ".tmp")
                }
            try {
                Files.write(temp, bytes)
                Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                Files.deleteIfExists(temp)
            }
        }
    }
}

internal fun X509Certificate.spki(): ByteArray = SelfSignedCert.spki(encoded)

internal fun pinOf(spki: ByteArray): String = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(spki))

internal fun pinsEqual(
    a: String,
    b: String,
): Boolean = MessageDigest.isEqual(a.toByteArray(Charsets.US_ASCII), b.toByteArray(Charsets.US_ASCII))
