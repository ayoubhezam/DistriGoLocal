package com.distrigo.app.data.backup.auto

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.time.Instant

/** Where the last automatic backup went. */
enum class BackupLocation { PICKED_FOLDER, PRIVATE }

/** The phone's automatic backup settings and what the last run did. */
data class AutoBackupState(
    /** The folder the user chose, as its tree address; null until one is chosen. */
    val folderUri: String? = null,
    /** The fingerprint of the data the last successful backup was taken from. */
    val lastFingerprint: String? = null,
    val lastAt: Instant? = null,
    val lastFileName: String? = null,
    val lastLocation: BackupLocation? = null,
)

/**
 * Automatic backup settings and state, kept in `no_backup/auto-backup/state.json`.
 *
 * Not in the database: they describe this phone, not the data. A folder permission exists only on the phone
 * that was granted it, so a restore onto another phone must not bring one along, and restoring an older backup
 * must not make the next run think that older data was already backed up. `no_backup` is also left out of
 * Android's own cloud backup.
 */
class AutoBackupStore(private val dir: File) {

    private val file get() = File(dir, "state.json")

    fun read(): AutoBackupState = synchronized(LOCK) {
        if (!file.isFile) return AutoBackupState()
        return try {
            val json = JsonParser().parse(file.readText()).asJsonObject
            AutoBackupState(
                folderUri = json.text("folder_uri"),
                lastFingerprint = json.text("last_fingerprint"),
                lastAt = json.text("last_at")?.let { runCatching { Instant.parse(it) }.getOrNull() },
                lastFileName = json.text("last_file_name"),
                lastLocation = json.text("last_location")?.let { runCatching { BackupLocation.valueOf(it) }.getOrNull() },
            )
        } catch (e: RuntimeException) {
            AutoBackupState()
        }
    }

    /** Applies [change] to the stored state and writes it whole: a kill mid-write keeps the previous state. */
    fun update(change: (AutoBackupState) -> AutoBackupState): AutoBackupState = synchronized(LOCK) {
        val next = change(read())
        val json = JsonObject().apply {
            addProperty("folder_uri", next.folderUri)
            addProperty("last_fingerprint", next.lastFingerprint)
            addProperty("last_at", next.lastAt?.toString())
            addProperty("last_file_name", next.lastFileName)
            addProperty("last_location", next.lastLocation?.name)
        }
        dir.mkdirs()
        val temp = File(dir, "state.json.tmp")
        temp.writeText(GSON.toJson(json))
        if (!temp.renameTo(file)) throw java.io.IOException("cannot write $file")
        next
    }

    private fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private companion object {
        val LOCK = Any()
        val GSON = GsonBuilder().serializeNulls().setPrettyPrinting().create()
    }
}
