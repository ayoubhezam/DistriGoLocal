package com.distrigo.app.data.backup

import com.distrigo.app.data.local.database.DATABASE_VERSION
import com.distrigo.app.data.local.database.FIRST_MIGRATABLE_VERSION
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The `.distrigo` backup file: a ZIP holding `manifest.json`, `distrigo.db` and `images/<sha256>.jpg`.
 *
 * `docs/data/backup-format.md` describes the format; this is what code relies on. Nothing here touches
 * Android, so the rules a file must meet are all checked by JVM tests.
 */
object BackupFormat {

    /**
     * The layout of the file. Raised only when an older app could no longer read a new backup correctly;
     * a new manifest field that older apps can ignore does not raise it.
     */
    const val FORMAT_VERSION = 1

    const val EXTENSION = "distrigo"
    const val MIME_TYPE = "application/zip"

    const val MANIFEST_ENTRY = "manifest.json"
    const val DATABASE_ENTRY = "distrigo.db"
    const val IMAGES_DIR = "images/"

    /** A manifest is a few kilobytes; anything past this is not one of ours. */
    const val MAX_MANIFEST_BYTES = 1L shl 20

    /**
     * The most one file may unpack to, so a hostile ZIP cannot fill the phone. A photo is at most
     * 1024 px and a few hundred kilobytes; the database is about 1 MB today.
     */
    const val MAX_IMAGE_BYTES = 20L shl 20
    const val MAX_DATABASE_BYTES = 2L shl 30

    private val IMAGE_ENTRY = Regex("images/[0-9a-f]{64}\\.jpg")
    private val SHA256_HEX = Regex("[0-9a-f]{64}")

    /**
     * Whether a ZIP entry has a name this format writes. Anything else is refused rather than skipped:
     * no directories, no `..`, no absolute or backslash paths, so nothing can unpack outside its folder.
     */
    fun isKnownEntry(name: String): Boolean =
        name == MANIFEST_ENTRY || name == DATABASE_ENTRY || IMAGE_ENTRY.matches(name)

    fun isImageEntry(name: String): Boolean = IMAGE_ENTRY.matches(name)

    /** The most [name] may unpack to. */
    fun sizeLimit(name: String): Long = when {
        name == MANIFEST_ENTRY -> MAX_MANIFEST_BYTES
        name == DATABASE_ENTRY -> MAX_DATABASE_BYTES
        else -> MAX_IMAGE_BYTES
    }

    fun isSha256(value: String): Boolean = SHA256_HEX.matches(value)

    /** The entry an `img:<hash>` photo is stored under. */
    fun imageEntry(hash: String): String = "$IMAGES_DIR$hash.jpg"

    /** `DistriGo-2026-09-17-1430.distrigo`, in local time: the name the file picker suggests. */
    fun fileName(createdAt: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        "DistriGo-" + FILE_STAMP.format(createdAt.atZone(zone)) + ".$EXTENSION"

    private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

    /** Lower-case hex SHA-256 of everything [input] still holds. Does not close it. */
    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Whether this app can restore a backup described by [manifest], judged by its versions alone.
     *
     * A database newer than [appVersion] cannot be opened: Room does not downgrade. One older than
     * [firstMigratable] has no migration path, and opening it would recreate it empty.
     */
    fun checkRestorable(
        manifest: BackupManifest,
        appVersion: Int = DATABASE_VERSION,
        firstMigratable: Int = FIRST_MIGRATABLE_VERSION,
    ): BackupProblem? = when {
        manifest.formatVersion > FORMAT_VERSION -> BackupProblem.NewerFormat(manifest.formatVersion)
        manifest.schemaVersion > appVersion -> BackupProblem.NewerDatabase(manifest.schemaVersion, appVersion)
        manifest.schemaVersion < firstMigratable -> BackupProblem.DatabaseTooOld(manifest.schemaVersion, firstMigratable)
        else -> null
    }
}

/** Why a file cannot be restored. Each case is something the user can be told in plain words. */
sealed class BackupProblem {

    /** Not a DistriGo backup at all: not a ZIP, no manifest, or a manifest that is not JSON. */
    data object NotABackup : BackupProblem()

    /** Made by a later app whose file layout this one does not know. */
    data class NewerFormat(val formatVersion: Int) : BackupProblem()

    /** Made by a later app whose database this one cannot open. */
    data class NewerDatabase(val backupVersion: Int, val appVersion: Int) : BackupProblem()

    /** Made by an app too old to migrate. */
    data class DatabaseTooOld(val backupVersion: Int, val firstMigratable: Int) : BackupProblem()

    /** A DistriGo backup that is damaged or was changed after it was written. [detail] is for logs. */
    data class Damaged(val detail: String) : BackupProblem()
}
