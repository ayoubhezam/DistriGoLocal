package com.distrigo.app.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the session at rest. On the phone: AES-GCM with a key that never leaves the Keystore. */
interface SessionCipher {
    fun encrypt(plain: ByteArray): ByteArray

    /** Throws when the bytes were not sealed by this cipher's key, or were altered. */
    fun decrypt(sealed: ByteArray): ByteArray
}

/**
 * The signed-in session, encrypted, in `no_backup/auth/session.bin`.
 *
 * Out of Auto Backup and device transfer like the license (LicenseStore): a copy on another phone could not be
 * decrypted anyway, its key being in this phone's Keystore. An update keeps the file and the key, so nobody is
 * signed out by an update (D8). If the Keystore loses the key, the session reads as none: sign in again.
 */
class SessionStore(private val dir: File, private val cipher: SessionCipher) {

    private val file get() = File(dir, "session.bin")

    fun load(): AuthSession? = synchronized(LOCK) {
        if (!file.isFile) return null
        try {
            val plain = cipher.decrypt(file.readBytes())
            AuthSession.fromJson(JsonParser().parse(String(plain, Charsets.UTF_8)).asJsonObject)
        } catch (e: Exception) {
            null
        }
    }

    /** Written whole and moved into place: a kill mid-write keeps the previous session. */
    fun save(session: AuthSession) {
        synchronized(LOCK) {
            dir.mkdirs()
            val temp = File(dir, "session.bin.tmp")
            temp.writeBytes(cipher.encrypt(session.toJson().toString().toByteArray(Charsets.UTF_8)))
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }

    fun clear() {
        synchronized(LOCK) { file.delete() }
    }

    companion object {
        private val LOCK = Any()

        fun forApp(context: android.content.Context) =
            SessionStore(File(context.applicationContext.noBackupFilesDir, "auth"), KeystoreSessionCipher())
    }
}

/** AES-256-GCM with a Keystore key: the 12-byte IV, then the ciphertext and its tag. */
class KeystoreSessionCipher(private val alias: String = ALIAS) : SessionCipher {

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        require(sealed.size > IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_SIZE))
        }
        return cipher.doFinal(sealed, IV_SIZE, sealed.size - IV_SIZE)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    companion object {
        /** Never renamed: an update must find the key that sealed the session. */
        const val ALIAS = "distrigo_session_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
