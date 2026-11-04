package com.distrigo.app.data.backup.cloud

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The cloud name of a photo (plan §3.5): a keyed hash of its content hash, so the same photo always has the same name
 * (sent once, shared by every backup) and the provider learns nothing from the names, not even which photos two
 * businesses share. The name key is derived from the backup key: a restore with the key finds every photo again.
 */
object PhotoNames {
    private const val LABEL = "DistriGo photo names v1"

    fun nameKey(key: BackupKey): ByteArray = hmac(key.material, LABEL.toByteArray(Charsets.UTF_8))

    /** [sha256] is the photo's content hash as the phone stores it (`<sha256>.jpg`). 43 characters. */
    fun name(nameKey: ByteArray, sha256: String): String = base64Url(hmac(nameKey, sha256.lowercase().toByteArray(Charsets.UTF_8)))

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }
}

/**
 * Which photos each cloud backup sent from this phone uses, by cloud name, in `no_backup/cloud_backup/photo_index.json`.
 * Photo cleanup (plan §3.5) deletes a cloud photo only when every remaining backup is listed here and none uses it;
 * a backup it does not know (another phone's, or one made before this file was lost) stops the cleanup until that
 * backup ages out. Tied to the key that named the photos: a new key starts an empty index.
 */
class CloudPhotoIndex(private val dir: File) {
    private val file get() = File(dir, "photo_index.json")

    /** Backup id → photo names, for the backups sent under [keyId]. */
    fun read(keyId: String): Map<String, Set<String>> = synchronized(LOCK) {
        if (!file.isFile) return emptyMap()
        return try {
            val json = JsonParser().parse(file.readText()).asJsonObject
            if (json.get("key")?.asString != keyId) return emptyMap()
            json.getAsJsonObject("backups").entrySet().associate { (id, names) ->
                id to names.asJsonArray.map { it.asString }.toSet()
            }
        } catch (e: RuntimeException) {
            emptyMap()
        }
    }

    fun update(keyId: String, change: (Map<String, Set<String>>) -> Map<String, Set<String>>) = synchronized(LOCK) {
        val next = change(read(keyId))
        val json = JsonObject().apply {
            addProperty("key", keyId)
            add("backups", JsonObject().apply {
                next.forEach { (id, names) -> add(id, JsonArray().apply { names.sorted().forEach(::add) }) }
            })
        }
        dir.mkdirs()
        val temp = File(dir, "photo_index.json.tmp")
        temp.writeText(json.toString())
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        val LOCK = Any()
    }
}
