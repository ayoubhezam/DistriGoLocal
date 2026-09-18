package com.distrigo.app.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.distrigo.app.data.image.ImageStore
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.DATABASE_NAME
import com.distrigo.app.data.local.entity.AppMetaEntity
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/** A backup that was written and read back whole from where the user saved it. */
data class CreatedBackup(
    val manifest: BackupManifest,
    /** Bytes of the `.distrigo` file. */
    val size: Long,
    /** The name the file was saved under, as the file picker shows it. */
    val fileName: String,
    /** Photos the data refers to that are not on the phone, so not in the backup. */
    val missingPhotos: Int,
    /** Photos whose file no longer matches its name, so left out rather than saved damaged. */
    val damagedPhotos: Int,
)

class BackupFailedException(val reason: Reason, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Reason {
        /** The database copy failed its checks: nothing was written. */
        DATABASE_DAMAGED,
        /** The chosen location could not be written: full, gone, or refused. */
        WRITE_FAILED,
        /** What was saved does not read back the same. The file is removed. */
        NOT_VERIFIED,
    }
}

/**
 * Makes a `.distrigo` backup and saves it where the user chose.
 *
 * The file is built and checked in the app's own cache first, then copied to [destination] and read
 * back from there. Only a file that reads back exactly as written counts as a backup: if anything fails,
 * [destination] is deleted, so what the user finds in their folder is always usable. The last backup is then
 * recorded in `app_meta` (`backup.last_*`).
 *
 * Everything this touches is passed in, so tests run it on their own database and folders.
 */
class BackupCreator(
    private val db: AppDatabase,
    private val databaseFile: File,
    private val imagesDir: File,
    /** Scratch space, emptied before and after every backup. */
    private val workDir: File,
    private val resolver: ContentResolver,
    private val appVersion: String,
    private val deviceModel: String,
) {

    enum class Step { COPYING_DATABASE, BUILDING_FILE, SAVING, VERIFYING }

    /**
     * Blocks for the whole backup, doing file I/O: call it off the main thread. One backup runs at a
     * time; a second call waits for the first.
     *
     * [record] is false for the safety backup a restore takes: it is not a backup the user made, and the
     * data it would be recorded in is about to be replaced. [includePhotos] is false for the copy taken before
     * an import, which touches no photo: the file then holds the database alone, and restoring it keeps the
     * phone's photos (see [BackupManifest.photosIncluded]).
     */
    fun create(destination: Uri, record: Boolean = true, includePhotos: Boolean = true, onStep: (Step) -> Unit = {}): CreatedBackup = synchronized(LOCK) {
        workDir.deleteRecursively()
        workDir.mkdirs()
        try {
            onStep(Step.COPYING_DATABASE)
            val snapshot = try {
                DatabaseSnapshot.take(db, databaseFile, imagesDir, File(workDir, "staging"))
            } catch (e: SnapshotException) {
                throw BackupFailedException(BackupFailedException.Reason.DATABASE_DAMAGED, e.message ?: "snapshot", e)
            }

            onStep(Step.BUILDING_FILE)
            val sources = linkedMapOf(BackupFormat.DATABASE_ENTRY to snapshot.database)
            var damagedPhotos = 0
            val photos = if (!includePhotos) emptyList() else snapshot.imageHashes.mapNotNull { hash ->
                val name = BackupFormat.imageEntry(hash)
                val file = File(imagesDir, "$hash.jpg")
                val entry = BackupArchive.describe(name, file)
                if (entry.sha256 != hash) {
                    damagedPhotos++
                    return@mapNotNull null
                }
                sources[name] = file
                entry
            }
            val manifest = BackupManifest(
                formatVersion = BackupFormat.FORMAT_VERSION,
                schemaVersion = snapshot.schemaVersion,
                appVersion = appVersion,
                createdAt = snapshot.createdAt,
                databaseId = snapshot.databaseId,
                deviceId = snapshot.deviceId,
                deviceModel = deviceModel,
                rowCounts = snapshot.rowCounts,
                entries = listOf(BackupArchive.describe(BackupFormat.DATABASE_ENTRY, snapshot.database)) + photos,
                photosIncluded = includePhotos,
            )
            val local = File(workDir, "backup.${BackupFormat.EXTENSION}")
            local.outputStream().use { BackupArchive.write(manifest, sources, it) }
            requireSame(manifest, local.inputStream().use { BackupArchive.verify(it) }, "built file")

            onStep(Step.SAVING)
            failingAs(BackupFailedException.Reason.WRITE_FAILED) {
                openForWriting(destination).use { out -> local.inputStream().use { it.copyTo(out) } }
            }

            onStep(Step.VERIFYING)
            failingAs(BackupFailedException.Reason.NOT_VERIFIED) {
                val readBack = resolver.openInputStream(destination)?.use { BackupArchive.verify(it) }
                    ?: throw IOException("the saved file cannot be opened")
                requireSame(manifest, readBack, "saved file")
            }

            val created = CreatedBackup(
                manifest = manifest,
                size = local.length(),
                fileName = displayName(destination),
                missingPhotos = if (includePhotos) snapshot.missingImages else 0,
                damagedPhotos = damagedPhotos,
            )
            if (record) db.appMetaDao().putAll(
                listOf(
                    AppMetaEntity(KEY_LAST_AT, manifest.createdAt.toString()),
                    AppMetaEntity(KEY_LAST_SIZE, created.size.toString()),
                    AppMetaEntity(KEY_LAST_NAME, created.fileName),
                )
            )
            created
        } catch (e: Exception) {
            // The picker created the file before the backup began: an empty or unverified one must not stay
            // in the user's folder looking like a backup.
            deleteQuietly(destination)
            throw e
        } finally {
            workDir.deleteRecursively()
        }
    }

    /** Reports a failure of the chosen location as [reason]; anything else is a bug and propagates as it is. */
    private inline fun failingAs(reason: BackupFailedException.Reason, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            throw when (e) {
                is BackupFailedException -> e
                is IOException, is SecurityException, is IllegalArgumentException ->
                    BackupFailedException(reason, e.message ?: reason.name, e)
                else -> e
            }
        }
    }

    private fun requireSame(manifest: BackupManifest, verification: Verification, what: String) {
        val problem = when (verification) {
            is Verification.Failed -> verification.problem.toString()
            is Verification.Verified -> if (verification.manifest == manifest) return else "a different manifest"
        }
        throw BackupFailedException(BackupFailedException.Reason.NOT_VERIFIED, "$what: $problem")
    }

    /** Truncating where the provider supports it; a document the picker just created is empty either way. */
    private fun openForWriting(uri: Uri): OutputStream {
        val truncating = try {
            resolver.openOutputStream(uri, "wt")
        } catch (e: FileNotFoundException) {
            if (uri.scheme == ContentResolver.SCHEME_FILE) throw e
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: UnsupportedOperationException) {
            null
        }
        return truncating ?: resolver.openOutputStream(uri, "w") ?: throw IOException("no output stream for $uri")
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "no display name for the saved backup", e)
            }
        }
        return uri.lastPathSegment ?: ""
    }

    /** Removes a file that is not a usable backup, so it is not mistaken for one. */
    private fun deleteQuietly(uri: Uri) {
        try {
            when (uri.scheme) {
                ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).delete() }
                ContentResolver.SCHEME_CONTENT -> DocumentsContract.deleteDocument(resolver, uri)
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not remove the unusable backup at $uri", e)
        }
    }

    companion object {
        const val KEY_LAST_AT = "backup.last_at"
        const val KEY_LAST_SIZE = "backup.last_size"
        const val KEY_LAST_NAME = "backup.last_name"

        private const val TAG = "BackupCreator"
        private val LOCK = Any()

        fun forApp(context: Context, db: AppDatabase): BackupCreator {
            val app = context.applicationContext
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            return BackupCreator(
                db = db,
                databaseFile = app.getDatabasePath(DATABASE_NAME),
                imagesDir = ImageStore.imagesDir(app),
                workDir = File(app.cacheDir, "backup"),
                resolver = app.contentResolver,
                appVersion = "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})",
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            )
        }
    }
}
