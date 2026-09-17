package com.distrigo.app.data.backup

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.util.Log
import androidx.room.Room
import com.distrigo.app.data.device.DeviceIdentity
import com.distrigo.app.data.local.database.ALL_MIGRATIONS
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.DATABASE_VERSION
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import java.io.File
import java.io.IOException
import java.io.InputStream

/** A backup unpacked and checked, ready to replace the data on the phone. Nothing on the phone has changed yet. */
data class PreparedRestore(
    val manifest: BackupManifest,
    /** The folder holding [database] and [images]. */
    val dir: File,
    /** Migrated to this app's version, identified as this phone, and one file. */
    val database: File,
    val images: File,
)

sealed class Preparation {
    data class Ready(val restore: PreparedRestore) : Preparation()
    data class Failed(val problem: BackupProblem) : Preparation()
}

/**
 * Unpacks a backup into a staging folder and proves it can replace the data on the phone.
 *
 * The file is read again, not trusted from its preview, and checked while it unpacks. The database it
 * holds must then be the version its manifest says, pass `integrity_check`, and hold the rows its
 * manifest counts — before migrating, since a migration may add rows. It is opened the way the app
 * opens its own, with every migration but never the destructive fallback, and must end up shaped exactly
 * like a database this app creates ([SchemaFingerprint]).
 *
 * Any failure deletes the staging folder. Nothing outside [restoreDir] is ever written.
 */
class RestorePreparer(
    private val context: Context,
    private val resolver: ContentResolver,
    /** Where staging happens: not in the cache, which Android may clear before the restore is installed. */
    private val restoreDir: File,
    private val deviceId: () -> String,
    private val usableSpace: (File) -> Long = { it.usableSpace },
) {

    val stagingDir: File get() = File(restoreDir, "staging")

    /**
     * Blocks for the whole preparation: call it off the main thread. [expected] is the manifest the user
     * was shown; a file that no longer matches it is refused.
     */
    fun prepare(uri: Uri, expected: BackupManifest? = null): Preparation {
        val staging = stagingDir
        staging.deleteRecursively()
        val result = try {
            unpackAndCheck(uri, expected, staging)
        } catch (e: RuntimeException) {
            staging.deleteRecursively()
            throw e
        }
        if (result is Preparation.Failed) {
            Log.w(TAG, "restore not prepared: ${result.problem}")
            staging.deleteRecursively()
        }
        return result
    }

    private fun unpackAndCheck(uri: Uri, expected: BackupManifest?, staging: File): Preparation {
        val images = File(staging, "images")
        if (!images.mkdirs()) return failed(BackupProblem.NotEnoughSpace(0))
        val database = File(staging, BackupFormat.DATABASE_ENTRY)

        val verification = try {
            resolver.openInputStream(uri)?.use { input ->
                BackupArchive.verify(
                    input,
                    onManifest = { manifest ->
                        // Refused before anything is unpacked.
                        if (expected != null && manifest != expected) throw Stop(damaged("the file changed since it was previewed"))
                        BackupFormat.checkRestorable(manifest)?.let { throw Stop(it) }
                        val needed = spaceNeeded(manifest)
                        if (usableSpace(restoreDir) < needed) throw Stop(BackupProblem.NotEnoughSpace(needed))
                    },
                    onEntry = { entry, bytes ->
                        val target = if (entry.name == BackupFormat.DATABASE_ENTRY) database else File(staging, entry.name)
                        unpack(bytes, target)
                    },
                )
            } ?: return failed(BackupProblem.CannotOpen)
        } catch (e: Stop) {
            e.cause?.let { Log.w(TAG, "cannot unpack", it) }
            return failed(e.problem)
        } catch (e: IOException) {
            return failed(BackupProblem.CannotOpen)
        } catch (e: SecurityException) {
            return failed(BackupProblem.CannotOpen)
        } catch (e: IllegalArgumentException) {
            return failed(BackupProblem.CannotOpen)
        }

        val manifest = when (verification) {
            is Verification.Failed -> return failed(verification.problem)
            is Verification.Verified -> verification.manifest
        }

        checkAsWritten(database, manifest)?.let { return failed(it) }

        openAsTheAppWould(database)?.let { return failed(it) }

        val folded = try {
            DatabaseSnapshot.inspect(database, images, manifest.createdAt).also { DatabaseSnapshot.requireSingleFile(database) }
        } catch (e: SnapshotException) {
            return failed(damaged("after opening: ${e.message}"))
        }
        if (folded.schemaVersion != DATABASE_VERSION) return failed(damaged("opened at version ${folded.schemaVersion}"))
        if (folded.databaseId != manifest.databaseId) return failed(damaged("database_id changed on opening"))

        return Preparation.Ready(PreparedRestore(manifest, staging, database, images))
    }

    /**
     * Copies an entry to [target]. Only a failure to write is the phone's: a failure to read is the file's,
     * and propagates for the archive reader to report as damage.
     */
    private fun unpack(bytes: InputStream, target: File) {
        val out = try {
            target.outputStream()
        } catch (e: IOException) {
            throw Stop(BackupProblem.NotEnoughSpace(0), e)
        }
        out.use {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = bytes.read(buffer)
                if (read < 0) break
                try {
                    it.write(buffer, 0, read)
                } catch (e: IOException) {
                    throw Stop(BackupProblem.NotEnoughSpace(0), e)
                }
            }
        }
    }

    /** The database exactly as the backup holds it: its version, its integrity and its row counts. */
    private fun checkAsWritten(database: File, manifest: BackupManifest): BackupProblem? {
        val sql = try {
            SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS, DatabaseSnapshot.KEEP_CORRUPT_FILE)
        } catch (e: SQLiteException) {
            return damaged("the database does not open: ${e.message}")
        }
        try {
            if (sql.version != manifest.schemaVersion) return damaged("user_version ${sql.version}, manifest ${manifest.schemaVersion}")
            val problems = sql.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            if (problems != listOf("ok")) return damaged("integrity_check: " + problems.take(5).joinToString("; "))
            val counts = DatabaseSnapshot.userTables(sql).associateWith { table ->
                sql.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { it.moveToFirst(); it.getLong(0) }
            }
            if (counts != manifest.rowCounts) return damaged("row counts differ from the manifest")
            return null
        } catch (e: SQLiteException) {
            return damaged("the database cannot be read: ${e.message}")
        } finally {
            sql.close()
        }
    }

    /**
     * Opens the staged database as the app opens its own, so migrations run and this phone's identity is
     * set now rather than on first launch, then compares its shape with a database this app creates.
     */
    private fun openAsTheAppWould(database: File): BackupProblem? {
        val staged = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, database.absolutePath)
            .addMigrations(*ALL_MIGRATIONS)
            .withChangeTracking()
            .withDeviceIdentity(deviceId)
            .build()
        return try {
            val actual = SchemaFingerprint.read(staged.openHelper.writableDatabase)
            val differences = actual.differencesFrom(expectedFingerprint())
            if (differences.isEmpty()) null else damaged("schema: " + differences.take(5).joinToString("; "))
        } catch (e: RuntimeException) {
            // A missing migration, a failed one, or an identity hash Room does not recognise.
            Log.w(TAG, "the staged database does not open", e)
            damaged("opening: ${e.message}")
        } finally {
            staged.close()
            removeRoomLock(database)
        }
    }

    /**
     * Room locks a database opened by path with `<cacheDir>/<path>.lck`, creating every folder of the path
     * inside the cache to do it. Removes the lock and those folders once the database is closed.
     */
    private fun removeRoomLock(database: File) {
        val cache = context.applicationContext.cacheDir
        var file: File? = File(cache, database.absolutePath + ".lck")
        file?.delete()
        file = file?.parentFile
        while (file != null && file != cache && file.startsWith(cache) && file.delete()) {
            file = file.parentFile
        }
    }

    private fun expectedFingerprint(): SchemaFingerprint = synchronized(RestorePreparer) {
        reference ?: run {
            val fresh = Room.inMemoryDatabaseBuilder(context.applicationContext, AppDatabase::class.java)
                .withChangeTracking()
                .withDeviceIdentity(deviceId)
                .build()
            try {
                SchemaFingerprint.read(fresh.openHelper.writableDatabase)
            } finally {
                fresh.close()
            }
        }.also { reference = it }
    }

    private fun failed(problem: BackupProblem) = Preparation.Failed(problem)

    private fun damaged(detail: String) = BackupProblem.Damaged(detail)

    /** Thrown out of the archive reader to stop reading a file that will not be restored. */
    private class Stop(val problem: BackupProblem, cause: IOException? = null) : RuntimeException(cause)

    companion object {
        private const val TAG = "RestorePreparer"

        /** The shape of this app's database; the same for every preparation, so read once per process. */
        private var reference: SchemaFingerprint? = null

        /**
         * Room to unpack every entry, then to open the database: migrated and journalled beside itself, about
         * its own size again, with a margin.
         */
        internal fun spaceNeeded(manifest: BackupManifest): Long =
            manifest.entries.sumOf { it.size } + manifest.database.size + SPACE_MARGIN

        private const val SPACE_MARGIN = 16L shl 20

        fun forApp(context: Context): RestorePreparer {
            val app = context.applicationContext
            return RestorePreparer(
                context = app,
                resolver = app.contentResolver,
                restoreDir = File(app.noBackupFilesDir, "restore"),
                deviceId = { DeviceIdentity.id(app) },
            )
        }
    }
}
