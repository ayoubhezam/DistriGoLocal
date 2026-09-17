package com.distrigo.app.data.backup

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.time.Instant
import java.time.format.DateTimeParseException

/** One file inside a backup, as the manifest promises it. */
data class BackupEntry(
    val name: String,
    val size: Long,
    val sha256: String,
)

/**
 * `manifest.json`: what a backup holds, and what lets a restore prove it is intact before touching anything.
 *
 * The manifest does not list itself. Every other entry is listed with its size and SHA-256, and a
 * restore accepts a file only if its entries are exactly these.
 */
data class BackupManifest(
    val formatVersion: Int,
    /** The database's schema version, `PRAGMA user_version` of the copy. */
    val schemaVersion: Int,
    /** `versionName (versionCode)` of the app that wrote it; shown, never compared. */
    val appVersion: String,
    /** When the copy was taken: a UTC instant. */
    val createdAt: Instant,
    /** `app_meta.database_id`: which body of data this is, the same on every backup of it. */
    val databaseId: String,
    /** The phone that wrote it, `app_meta.device_id`. */
    val deviceId: String,
    /** A name the user recognises, such as `samsung SM-M346B`. */
    val deviceModel: String,
    /** Rows per table in the copy, to preview a backup and to check the restored database against. */
    val rowCounts: Map<String, Long>,
    val entries: List<BackupEntry>,
) {

    fun entry(name: String): BackupEntry? = entries.firstOrNull { it.name == name }

    val database: BackupEntry get() = entry(BackupFormat.DATABASE_ENTRY)!!

    val images: List<BackupEntry> get() = entries.filter { BackupFormat.isImageEntry(it.name) }

    fun toJson(): String {
        val json = JsonObject().apply {
            addProperty("format_version", formatVersion)
            addProperty("schema_version", schemaVersion)
            addProperty("app_version", appVersion)
            addProperty("created_at", createdAt.toString())
            addProperty("database_id", databaseId)
            addProperty("device_id", deviceId)
            addProperty("device_model", deviceModel)
            add("row_counts", JsonObject().also { counts ->
                rowCounts.toSortedMap().forEach { (table, count) -> counts.addProperty(table, count) }
            })
            add("entries", com.google.gson.JsonArray().also { list ->
                entries.forEach { entry ->
                    list.add(JsonObject().apply {
                        addProperty("name", entry.name)
                        addProperty("size", entry.size)
                        addProperty("sha256", entry.sha256)
                    })
                }
            })
        }
        return GSON.toJson(json)
    }

    companion object {

        private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        /**
         * Reads and checks a manifest. Every field this format needs must be present and well formed;
         * fields it does not know are ignored, so a later app can add some without raising the format.
         *
         * A manifest from a later format is reported as [BackupProblem.NewerFormat] before anything else
         * is checked, since its other fields may mean something else.
         */
        fun parse(text: String): ManifestResult {
            val root = try {
                JsonParser().parse(text)
            } catch (e: RuntimeException) {
                return ManifestResult.Invalid(BackupProblem.NotABackup)
            }
            if (!root.isJsonObject) return ManifestResult.Invalid(BackupProblem.NotABackup)
            val json = root.asJsonObject

            val format = json.int("format_version")
                ?: return ManifestResult.Invalid(BackupProblem.NotABackup)
            if (format > BackupFormat.FORMAT_VERSION) {
                return ManifestResult.Invalid(BackupProblem.NewerFormat(format))
            }
            return try {
                ManifestResult.Valid(read(json, format))
            } catch (e: ManifestException) {
                ManifestResult.Invalid(BackupProblem.Damaged(e.message ?: "manifest"))
            }
        }

        private fun read(json: JsonObject, format: Int): BackupManifest {
            if (format < 1) fail("format_version $format")

            val schema = json.int("schema_version") ?: fail("schema_version")
            if (schema < 1) fail("schema_version $schema")

            val createdAt = json.string("created_at")?.let {
                try { Instant.parse(it) } catch (e: DateTimeParseException) { null }
            } ?: fail("created_at")

            val databaseId = json.string("database_id")?.takeIf { it.isNotBlank() } ?: fail("database_id")
            val deviceId = json.string("device_id")?.takeIf { it.isNotBlank() } ?: fail("device_id")

            val counts = json.get("row_counts")?.takeIf { it.isJsonObject }?.asJsonObject ?: fail("row_counts")
            val rowCounts = counts.entrySet().associate { (table, value) ->
                val count = value.long() ?: fail("row_counts.$table")
                if (count < 0) fail("row_counts.$table $count")
                table to count
            }

            val list = json.get("entries")?.takeIf { it.isJsonArray }?.asJsonArray ?: fail("entries")
            val entries = list.mapIndexed { index, element ->
                val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: fail("entries[$index]")
                val name = entry.string("name") ?: fail("entries[$index].name")
                if (name == BackupFormat.MANIFEST_ENTRY || !BackupFormat.isKnownEntry(name)) fail("entry name $name")
                val size = entry.get("size")?.long() ?: fail("$name size")
                if (size < 0 || size > BackupFormat.sizeLimit(name)) fail("$name size $size")
                val sha256 = entry.string("sha256")?.takeIf(BackupFormat::isSha256) ?: fail("$name sha256")
                BackupEntry(name, size, sha256)
            }
            if (entries.map { it.name }.toSet().size != entries.size) fail("duplicate entries")
            if (entries.none { it.name == BackupFormat.DATABASE_ENTRY }) fail("no ${BackupFormat.DATABASE_ENTRY}")

            return BackupManifest(
                formatVersion = format,
                schemaVersion = schema,
                appVersion = json.string("app_version") ?: "",
                createdAt = createdAt,
                databaseId = databaseId,
                deviceId = deviceId,
                deviceModel = json.string("device_model") ?: "",
                rowCounts = rowCounts,
                entries = entries,
            )
        }

        private class ManifestException(message: String) : Exception(message)

        private fun fail(what: String): Nothing = throw ManifestException("manifest: $what")

        private fun JsonObject.string(key: String): String? =
            get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

        private fun JsonObject.int(key: String): Int? =
            get(key)?.long()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

        /** A whole JSON number, or null for a string, a fraction or anything else. */
        private fun JsonElement.long(): Long? {
            val primitive = takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf(JsonPrimitive::isNumber) ?: return null
            return primitive.asBigDecimal.let { value ->
                try { value.longValueExact() } catch (e: ArithmeticException) { null }
            }
        }
    }
}

sealed class ManifestResult {
    data class Valid(val manifest: BackupManifest) : ManifestResult()
    data class Invalid(val problem: BackupProblem) : ManifestResult()
}
