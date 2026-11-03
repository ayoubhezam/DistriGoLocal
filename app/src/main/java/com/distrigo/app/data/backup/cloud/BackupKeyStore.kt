package com.distrigo.app.data.backup.cloud

import android.content.Context
import com.distrigo.app.data.auth.KeystoreSessionCipher
import com.distrigo.app.data.auth.SessionCipher
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * This phone's copy of the business's backup key and key ring, in `no_backup/cloud_backup/` (plan §3.3).
 *
 * - `key.bin`: the [BackupKey] sealed by a Keystore key that never leaves the phone, so the daily backup runs without
 *   asking for the password. Out of Auto Backup and device transfer like the session: on another phone it could not
 *   be opened anyway, and a new phone gets the key from the ring and the password.
 * - `keyring.json`: the ring as last sent, safe to keep in clear (it is sealed by the password and the code), so it
 *   can be put back on Drive if the user deletes it there.
 *
 * One business per phone: a key saved for one business is never handed out for another. If the Keystore loses its
 * key, [loadKey] reads as none and the user types the password again.
 */
class BackupKeyStore(private val dir: File, private val cipher: SessionCipher) {

    private val keyFile get() = File(dir, "key.bin")
    private val ringFile get() = File(dir, BackupKeyRing.FILE_NAME)

    /** File and Keystore I/O: off the main thread. */
    fun loadKey(businessId: String): BackupKey? = synchronized(LOCK) {
        if (!keyFile.isFile) return null
        try {
            val json = JsonParser().parse(String(cipher.decrypt(keyFile.readBytes()), Charsets.UTF_8)).asJsonObject
            if (json.get("businessId")?.asString != businessId) return null
            BackupKey(fromBase64Url(json.get("keyId").asString)!!, fromBase64Url(json.get("key").asString)!!)
        } catch (e: Exception) {
            null
        }
    }

    fun saveKey(businessId: String, key: BackupKey) {
        val json = JsonObject().apply {
            addProperty("businessId", businessId)
            addProperty("keyId", key.idText)
            addProperty("key", base64Url(key.material))
        }
        synchronized(LOCK) { writeAtomically(keyFile, cipher.encrypt(json.toString().toByteArray(Charsets.UTF_8))) }
    }

    fun loadKeyRing(businessId: String): BackupKeyRing? = synchronized(LOCK) {
        if (!ringFile.isFile) return null
        try {
            BackupKeyRing.fromJson(ringFile.readText(Charsets.UTF_8)).takeIf { it.businessId == businessId }
        } catch (e: Exception) {
            null
        }
    }

    fun saveKeyRing(ring: BackupKeyRing) {
        synchronized(LOCK) { writeAtomically(ringFile, ring.toJson().toByteArray(Charsets.UTF_8)) }
    }

    /** « Délier »: the phone forgets the key and the ring; the backups and the ring on Drive stay. */
    fun clear() {
        synchronized(LOCK) {
            keyFile.delete()
            ringFile.delete()
        }
    }

    /** Written whole and moved into place: a kill mid-write keeps the previous file. */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        dir.mkdirs()
        val temp = File(dir, "${target.name}.tmp")
        temp.writeBytes(bytes)
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        private val LOCK = Any()

        /** Never renamed: an update must find the key that sealed `key.bin`. */
        const val KEYSTORE_ALIAS = "distrigo_cloud_backup_v1"

        fun forApp(context: Context) = BackupKeyStore(
            File(context.applicationContext.noBackupFilesDir, "cloud_backup"),
            KeystoreSessionCipher(KEYSTORE_ALIAS),
        )
    }
}
