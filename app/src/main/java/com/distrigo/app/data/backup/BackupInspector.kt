package com.distrigo.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.DATABASE_VERSION
import java.io.File
import java.io.IOException

/** What the user is shown before choosing to restore a backup: the file checked whole, next to the data it would replace. */
data class BackupPreview(
    val uri: Uri,
    val manifest: BackupManifest,
    /** As the file picker shows it; empty if the provider does not say. */
    val fileName: String,
    /** Bytes, or null if the provider does not say. */
    val fileSize: Long?,
    /** Rows per table in the data on the phone now. */
    val currentCounts: Map<String, Long>,
    /** The backup is of the same data as the phone holds: an earlier or later state of it, not another business's. */
    val sameDatabase: Boolean,
    /** Made on this phone, rather than brought from another. */
    val fromThisPhone: Boolean,
) {
    /** Restoring will run migrations: the backup is from an earlier app version. */
    val needsUpgrade: Boolean get() = manifest.schemaVersion < DATABASE_VERSION

    val photoCount: Int get() = manifest.images.size

    /** The tables worth showing side by side, each as backup count to current count. */
    val headline: List<Pair<String, Pair<Long, Long>>>
        get() = HEADLINE_TABLES.map { it to ((manifest.rowCounts[it] ?: 0L) to (currentCounts[it] ?: 0L)) }

    companion object {
        /** What a user recognises their data by. Counts include rows moved to the bin. */
        val HEADLINE_TABLES = listOf("products", "clients", "suppliers", "ventes", "purchase_orders", "stock_movements")
    }
}

sealed class BackupInspection {
    data class Restorable(val preview: BackupPreview) : BackupInspection()
    data class NotRestorable(val problem: BackupProblem) : BackupInspection()
}

/**
 * Opens a backup the user picked and decides whether it can be restored, before anything is changed.
 *
 * The whole file is read and checked — every entry's size and checksum, not just the manifest — so a
 * preview is only ever shown for a file that is intact. Then its versions are checked against this app.
 * Nothing is unpacked: the restore reads the file again.
 */
class BackupInspector(
    private val db: AppDatabase,
    private val resolver: ContentResolver,
) {

    /** Blocks, reading the whole file: call it off the main thread. */
    fun inspect(uri: Uri): BackupInspection {
        val verification = try {
            resolver.openInputStream(uri)?.use { BackupArchive.verify(it) }
                ?: return BackupInspection.NotRestorable(BackupProblem.CannotOpen)
        } catch (e: IOException) {
            Log.w(TAG, "cannot open $uri", e)
            return BackupInspection.NotRestorable(BackupProblem.CannotOpen)
        } catch (e: SecurityException) {
            Log.w(TAG, "no permission to read $uri", e)
            return BackupInspection.NotRestorable(BackupProblem.CannotOpen)
        } catch (e: IllegalArgumentException) {
            // What a provider throws for an address it does not know, such as one picked before and since removed.
            Log.w(TAG, "the provider does not know $uri", e)
            return BackupInspection.NotRestorable(BackupProblem.CannotOpen)
        }

        val manifest = when (verification) {
            is Verification.Failed -> {
                Log.w(TAG, "not restorable: ${verification.problem}")
                return BackupInspection.NotRestorable(verification.problem)
            }
            is Verification.Verified -> verification.manifest
        }
        BackupFormat.checkRestorable(manifest)?.let { return BackupInspection.NotRestorable(it) }

        val (fileName, fileSize) = describe(uri)
        val meta = db.appMetaDao()
        return BackupInspection.Restorable(
            BackupPreview(
                uri = uri,
                manifest = manifest,
                fileName = fileName,
                fileSize = fileSize,
                currentCounts = currentCounts(),
                sameDatabase = manifest.databaseId == meta.get("database_id"),
                fromThisPhone = manifest.deviceId == meta.get("device_id"),
            )
        )
    }

    private fun currentCounts(): Map<String, Long> {
        val sql = db.openHelper.readableDatabase
        val tables = sql.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite\\_%' ESCAPE '\\' " +
                "AND name NOT IN ('android_metadata', 'room_master_table')"
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        return tables.associateWith { table -> sql.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getLong(0) } }
    }

    private fun describe(uri: Uri): Pair<String, Long?> {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = if (cursor.isNull(0)) "" else cursor.getString(0)
                        val size = if (cursor.isNull(1)) null else cursor.getLong(1)
                        return name to size
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "no name or size for $uri", e)
            }
            return "" to null
        }
        val file = uri.path?.let { File(it) }
        return (uri.lastPathSegment ?: "") to file?.takeIf { it.isFile }?.length()
    }

    private companion object {
        const val TAG = "BackupInspector"
    }
}
