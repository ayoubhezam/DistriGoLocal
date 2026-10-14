package com.distrigo.app.data.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The activation code that is this phone's business key (docs/activation_codes.md), encrypted like the session,
 * in `no_backup/auth/activation_code.bin`. The server keeps only a fingerprint of it, so this phone is the one
 * place it can be read again: Compte shows it, so the merchant can bring the business to a new phone.
 *
 * Kept with the business it opens: Compte shows it only while the license is that business's, so signing in to
 * another account never shows a code that isn't its own.
 */
class ActivationCodeStore(private val dir: File, private val cipher: SessionCipher) {

    data class Saved(val code: String, val businessId: String) {
        override fun toString(): String = "ActivationCodeStore.Saved(business=$businessId)"
    }

    private val file get() = File(dir, "activation_code.bin")

    private val _saved = MutableStateFlow<Saved?>(null)
    /** What [load] read or [save] wrote; null until then, or when there is none. */
    val saved: StateFlow<Saved?> = _saved.asStateFlow()

    /** Reads the stored code. File and Keystore I/O: off the main thread. */
    fun load(): Saved? = synchronized(LOCK) {
        val read = try {
            if (!file.isFile) {
                null
            } else {
                val json = JsonParser().parse(String(cipher.decrypt(file.readBytes()), Charsets.UTF_8)).asJsonObject
                if (json.get("v")?.asInt == 1) Saved(json.get("code").asString, json.get("business_id").asString) else null
            }
        } catch (e: Exception) {
            null
        }
        _saved.value = read
        read
    }

    /** Written whole and moved into place, like the session. A new key replaces the last one. */
    fun save(code: String, businessId: String) {
        synchronized(LOCK) {
            dir.mkdirs()
            val json = JsonObject().apply {
                addProperty("v", 1)
                addProperty("code", code)
                addProperty("business_id", businessId)
            }
            val temp = File(dir, "activation_code.bin.tmp")
            temp.writeBytes(cipher.encrypt(json.toString().toByteArray(Charsets.UTF_8)))
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            _saved.value = Saved(code, businessId)
        }
    }

    /** After the account and its business were deleted: the key opens nothing any more. */
    fun clear() {
        synchronized(LOCK) {
            file.delete()
            _saved.value = null
        }
    }

    companion object {
        private val LOCK = Any()

        fun forApp(context: android.content.Context) =
            ActivationCodeStore(File(context.applicationContext.noBackupFilesDir, "auth"), KeystoreSessionCipher())
    }
}
