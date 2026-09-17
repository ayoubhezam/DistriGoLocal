package com.distrigo.app.data.backup.auto

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.time.Instant

/** Where the last automatic backup went. */
enum class BackupLocation { PICKED_FOLDER, PRIVATE }

/** The phone's automatic backup settings and what the last runs did. */
data class AutoBackupState(
    /** Whether backups run on their own every day. */
    val enabled: Boolean = false,
    /** When they were last turned on: until a first run, how long the schedule has had to run. */
    val enabledAt: Instant? = null,
    /** The folder the user chose, as its tree address; null until one is chosen. */
    val folderUri: String? = null,
    /** The fingerprint of the data the last successful backup was taken from. */
    val lastFingerprint: String? = null,
    val lastAt: Instant? = null,
    val lastFileName: String? = null,
    val lastLocation: BackupLocation? = null,
    /** When a backup was last attempted, whether it saved, skipped or failed. */
    val lastAttemptAt: Instant? = null,
    /** When the daily job last ran. Kept apart from [lastAttemptAt], which a manual backup also sets. */
    val lastScheduledRunAt: Instant? = null,
    /** Runs in a row that left the data without a backup in the chosen folder; 0 after a good run. */
    val problemStreak: Int = 0,
    /** What went wrong on the last run, or null if it went well. */
    val lastProblem: AutoBackupProblem? = null,
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
                enabled = json.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
                enabledAt = json.instant("enabled_at"),
                folderUri = json.text("folder_uri"),
                lastFingerprint = json.text("last_fingerprint"),
                lastAt = json.instant("last_at"),
                lastFileName = json.text("last_file_name"),
                lastLocation = json.text("last_location")?.let { runCatching { BackupLocation.valueOf(it) }.getOrNull() },
                lastAttemptAt = json.instant("last_attempt_at"),
                lastScheduledRunAt = json.instant("last_scheduled_run_at"),
                problemStreak = json.get("problem_streak")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                lastProblem = json.text("last_problem")?.let { runCatching { AutoBackupProblem.valueOf(it) }.getOrNull() },
            )
        } catch (e: RuntimeException) {
            AutoBackupState()
        }
    }

    /** Applies [change] to the stored state and writes it whole: a kill mid-write keeps the previous state. */
    fun update(change: (AutoBackupState) -> AutoBackupState): AutoBackupState = synchronized(LOCK) {
        val next = change(read())
        val json = JsonObject().apply {
            addProperty("enabled", next.enabled)
            addProperty("enabled_at", next.enabledAt?.toString())
            addProperty("folder_uri", next.folderUri)
            addProperty("last_fingerprint", next.lastFingerprint)
            addProperty("last_at", next.lastAt?.toString())
            addProperty("last_file_name", next.lastFileName)
            addProperty("last_location", next.lastLocation?.name)
            addProperty("last_attempt_at", next.lastAttemptAt?.toString())
            addProperty("last_scheduled_run_at", next.lastScheduledRunAt?.toString())
            addProperty("problem_streak", next.problemStreak)
            addProperty("last_problem", next.lastProblem?.name)
        }
        dir.mkdirs()
        val temp = File(dir, "state.json.tmp")
        temp.writeText(GSON.toJson(json))
        if (!temp.renameTo(file)) throw java.io.IOException("cannot write $file")
        next
    }

    private fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.instant(key: String): Instant? = text(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }

    companion object {
        private val LOCK = Any()
        private val GSON = GsonBuilder().serializeNulls().setPrettyPrinting().create()

        fun forApp(context: android.content.Context) = AutoBackupStore(File(context.applicationContext.noBackupFilesDir, "auto-backup"))
    }
}
