package org.capnav.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Encrypts/decrypts small blobs with a non-exportable AES-256-GCM key held by Android Keystore. */
class KeystoreCipher(private val alias: String = "cap.local.v1") {

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_LEN))
        return c.doFinal(blob, IV_LEN, blob.size - IV_LEN)
    }

    fun destroyKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}

/**
 * One encrypted file per logical store. Writes are atomic (temp file + rename) and deletes
 * overwrite the content before unlinking, so no plaintext or stale ciphertext lingers.
 */
class EncryptedFile(private val file: File, private val cipher: KeystoreCipher) {

    fun read(): String? {
        if (!file.exists()) return null
        return runCatching { String(cipher.decrypt(file.readBytes()), Charsets.UTF_8) }.getOrNull()
    }

    fun write(text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(cipher.encrypt(text.toByteArray(Charsets.UTF_8)))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    fun wipe() = SecureWipe.wipe(file)
}

object SecureWipe {
    fun wipe(file: File) {
        if (!file.exists()) return
        runCatching {
            val zeros = ByteArray(file.length().toInt().coerceAtMost(1 shl 20))
            file.writeBytes(zeros)
        }
        file.delete()
    }
}

/**
 * Password-based container for manual exports: PBKDF2-HMAC-SHA256 (310k iterations) derives an
 * AES-256-GCM key. Layout: magic(4) | salt(16) | iv(12) | ciphertext+tag.
 */
object PasswordBox {
    private val MAGIC = byteArrayOf('C'.code.toByte(), 'A'.code.toByte(), 'P'.code.toByte(), 1)
    private const val ITERATIONS = 310_000
    private val random = SecureRandom()

    fun seal(plain: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        c.updateAAD(MAGIC)
        return MAGIC + salt + iv + c.doFinal(plain)
    }

    fun open(blob: ByteArray, password: CharArray): ByteArray {
        require(blob.size > 4 + 16 + 12 + 16) { "file too short" }
        require(blob.copyOfRange(0, 4).contentEquals(MAGIC)) { "not a Cap export" }
        val salt = blob.copyOfRange(4, 20)
        val iv = blob.copyOfRange(20, 32)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        c.updateAAD(MAGIC)
        return c.doFinal(blob, 32, blob.size - 32)
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
