package com.distrigo.app.data.backup.cloud

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

/** A backup sealed and waiting to go up, kept so a cut upload resumes instead of starting over (plan §3.6). */
data class PendingUpload(
    /** The sealed file's name in the staging folder. */
    val fileName: String,
    val fingerprint: String,
    val createdAtMillis: Long,
    val schemaVersion: Int,
    val deviceId: String,
    val plainSize: Long,
    val keyId: String,
    /** The provider's upload session, once one is open. */
    val session: String? = null,
    /** The photos this backup needs, by content hash: sent before it (plan §3.5). */
    val photos: List<String> = emptyList(),
)

/** This phone's cloud backup: the link, the settings, and what the last runs did. */
data class CloudBackupState(
    val linked: Boolean = false,
    val providerId: String? = null,
    /** The linked account (an email), shown on the status card and used to ask for tokens silently. */
    val account: String? = null,
    /** The business the link was made for: data restored from another business must not be sent under this one. */
    val businessId: String? = null,
    val linkedAt: Instant? = null,
    /** « Sauvegarde automatique ». */
    val enabled: Boolean = true,
    /** « Autoriser les données mobiles »: off means Wi-Fi only (owner's decision §9.4). */
    val allowMobileData: Boolean = false,
    /** « Inclure les photos » (owner's decision §9.3; used from Phase 4). */
    val includePhotos: Boolean = true,
    /** The fingerprint of the data the last backup in the cloud was taken from. */
    val lastFingerprint: String? = null,
    val lastAt: Instant? = null,
    /** The provider's id for the last backup: a day without changes checks it is still there. */
    val lastEntryId: String? = null,
    /** Encrypted size of the last backup. */
    val lastSize: Long? = null,
    val lastAttemptAt: Instant? = null,
    /** When the daily job last ran, apart from « Sauvegarder maintenant ». */
    val lastScheduledRunAt: Instant? = null,
    /** Runs in a row that ended with a problem; 0 after a good run. */
    val problemStreak: Int = 0,
    val lastProblem: CloudBackupProblem? = null,
    val pending: PendingUpload? = null,
)

/**
 * Cloud backup settings and state, in `no_backup/cloud_backup/state.json`. Not in the database, for the reasons of
 * [com.distrigo.app.data.backup.auto.AutoBackupStore]: they describe this phone and its link, not the data.
 */
class CloudBackupStore(private val dir: File) {

    private val file get() = File(dir, "state.json")

    fun read(): CloudBackupState = synchronized(LOCK) {
        if (!file.isFile) return CloudBackupState()
        return try {
            val json = JsonParser().parse(file.readText()).asJsonObject
            CloudBackupState(
                linked = json.bool("linked") ?: false,
                providerId = json.text("provider"),
                account = json.text("account"),
                businessId = json.text("business_id"),
                linkedAt = json.instant("linked_at"),
                enabled = json.bool("enabled") ?: true,
                allowMobileData = json.bool("allow_mobile_data") ?: false,
                includePhotos = json.bool("include_photos") ?: true,
                lastFingerprint = json.text("last_fingerprint"),
                lastAt = json.instant("last_at"),
                lastEntryId = json.text("last_entry_id"),
                lastSize = json.text("last_size")?.toLongOrNull(),
                lastAttemptAt = json.instant("last_attempt_at"),
                lastScheduledRunAt = json.instant("last_scheduled_run_at"),
                problemStreak = json.text("problem_streak")?.toIntOrNull() ?: 0,
                lastProblem = json.text("last_problem")?.let { runCatching { CloudBackupProblem.valueOf(it) }.getOrNull() },
                pending = (json.get("pending") as? JsonObject)?.let(::pendingFrom),
            )
        } catch (e: RuntimeException) {
            CloudBackupState()
        }
    }

    /** Applies [change] and writes the state whole: a kill mid-write keeps the previous state. */
    fun update(change: (CloudBackupState) -> CloudBackupState): CloudBackupState = synchronized(LOCK) {
        val next = change(read())
        val json = JsonObject().apply {
            addProperty("linked", next.linked)
            addProperty("provider", next.providerId)
            addProperty("account", next.account)
            addProperty("business_id", next.businessId)
            addProperty("linked_at", next.linkedAt?.toString())
            addProperty("enabled", next.enabled)
            addProperty("allow_mobile_data", next.allowMobileData)
            addProperty("include_photos", next.includePhotos)
            addProperty("last_fingerprint", next.lastFingerprint)
            addProperty("last_at", next.lastAt?.toString())
            addProperty("last_entry_id", next.lastEntryId)
            addProperty("last_size", next.lastSize?.toString())
            addProperty("last_attempt_at", next.lastAttemptAt?.toString())
            addProperty("last_scheduled_run_at", next.lastScheduledRunAt?.toString())
            addProperty("problem_streak", next.problemStreak.toString())
            addProperty("last_problem", next.lastProblem?.name)
            add("pending", next.pending?.let(::pendingJson))
        }
        dir.mkdirs()
        val temp = File(dir, "state.json.tmp")
        temp.writeText(GSON.toJson(json))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        next
    }

    private fun pendingJson(p: PendingUpload) = JsonObject().apply {
        addProperty("file", p.fileName)
        addProperty("fingerprint", p.fingerprint)
        addProperty("created_at", p.createdAtMillis.toString())
        addProperty("schema", p.schemaVersion.toString())
        addProperty("device_id", p.deviceId)
        addProperty("plain_size", p.plainSize.toString())
        addProperty("key_id", p.keyId)
        addProperty("session", p.session)
        add("photos", com.google.gson.JsonArray().apply { p.photos.forEach(::add) })
    }

    private fun pendingFrom(json: JsonObject): PendingUpload? {
        return PendingUpload(
            fileName = json.text("file") ?: return null,
            fingerprint = json.text("fingerprint") ?: return null,
            createdAtMillis = json.text("created_at")?.toLongOrNull() ?: return null,
            schemaVersion = json.text("schema")?.toIntOrNull() ?: return null,
            deviceId = json.text("device_id") ?: return null,
            plainSize = json.text("plain_size")?.toLongOrNull() ?: return null,
            keyId = json.text("key_id") ?: return null,
            session = json.text("session"),
            photos = (json.get("photos") as? com.google.gson.JsonArray)?.map { it.asString }.orEmpty(),
        )
    }

    private fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.bool(key: String): Boolean? = get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean

    private fun JsonObject.instant(key: String): Instant? = text(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }

    companion object {
        private val LOCK = Any()
        private val GSON = GsonBuilder().serializeNulls().setPrettyPrinting().create()

        fun forApp(context: android.content.Context) =
            CloudBackupStore(File(context.applicationContext.noBackupFilesDir, "cloud_backup"))
    }
}
