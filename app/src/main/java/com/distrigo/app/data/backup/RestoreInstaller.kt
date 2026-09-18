package com.distrigo.app.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.distrigo.app.data.image.ImageBackfill
import com.distrigo.app.data.image.ImageStore
import com.distrigo.app.data.local.database.DATABASE_NAME
import com.distrigo.app.data.local.database.DATABASE_VERSION
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.time.Instant

/** How the last restore ended, for the screen to tell the user once the app is back. */
data class RestoreResult(
    val installed: Boolean,
    val at: Instant,
    /** When the restored backup was made. */
    val backupCreatedAt: Instant?,
    /** The safety backup taken of the data it replaced, in [RestoreInstaller.safetyDir]. */
    val safetyBackup: String?,
    /** For the log, when [installed] is false. */
    val detail: String?,
)

/**
 * Puts a prepared restore in place of the data on the phone, when nothing is using it.
 *
 * ### Two halves
 *
 * [schedule], while the app runs, moves a [PreparedRestore] to `restore/pending` and marks it ready. The
 * app then restarts, and [installPending] — called first thing in `Application.onCreate` and again before
 * the database is built — swaps the files before anything opens them. Swapping at startup is what makes a
 * restore safe: no screen, query or worker can be holding the old database while it is replaced.
 *
 * ### Surviving a kill at any point
 *
 * Every move is a rename within the app's own storage, so each file is either moved or not. The install
 * writes the stage it has reached to `restore/install-state` before each stage, and a start that finds
 * that file finishes or undoes what was interrupted:
 *
 *  1. `moving-old`: the database files and photo folder move to `restore/previous`.
 *  2. `moving-new`: the restored database and photos move into their places, and the database is checked.
 *
 * A backup without photos (`photos_included` false in its manifest, such as the copy taken before an import)
 * leaves the photo folder where it is at both steps: the phone keeps the photos it has.
 *  3. `finishing`: the restore is recorded, and the restored backup becomes the data's last backup; `pending` and
 *     `previous` are deleted.
 *
 * Interrupted in 1 or 2, the files go back where they were and the install starts again, at most
 * [MAX_ATTEMPTS] times. A restored database that fails its check is never retried: the old data goes back,
 * the prepared restore is discarded, and the failure is recorded for the screen. The user's data is never
 * left half replaced, and the safety backup taken before scheduling stays in [safetyDir] either way.
 */
class RestoreInstaller(
    private val databaseFile: File,
    private val imagesDir: File,
    val restoreDir: File,
    /** Clears ImageBackfill's done flag, so a restored database is checked for base64 photos again. */
    private val clearImageBackfill: () -> Unit,
) {

    val pendingDir: File get() = File(restoreDir, "pending")
    val safetyDir: File get() = File(restoreDir, "safety")
    private val previousDir get() = File(restoreDir, "previous")
    private val readyMarker get() = File(pendingDir, "READY")
    private val stateFile get() = File(restoreDir, "install-state")
    private val resultFile get() = File(restoreDir, "last-result")

    /**
     * Called as the install passes each point that a kill could interrupt, for tests to stop it there by
     * throwing [Interrupted], which is let through as a kill would be. Null in the app.
     */
    internal var onPoint: ((String) -> Unit)? = null

    /** A kill, for tests: not caught, not tidied up after. */
    internal class Interrupted : Error()

    /** Whether a restore is waiting for the app to restart. */
    val hasPending: Boolean get() = readyMarker.isFile

    /**
     * Makes [restore] the one installed on the next start, replacing any restore already waiting. The caller
     * restarts the app straight after: anything written to the database until then is replaced.
     */
    fun schedule(restore: PreparedRestore, safetyBackup: String?, backupFileName: String? = null, backupFileSize: Long? = null) {
        require(restore.dir.isDirectory) { "nothing prepared in ${restore.dir}" }
        pendingDir.deleteRecursively()
        pendingDir.parentFile!!.mkdirs()
        if (!restore.dir.renameTo(pendingDir)) throw java.io.IOException("cannot move ${restore.dir} to $pendingDir")
        val marker = JsonObject().apply {
            addProperty("scheduled_at", Instant.now().toString())
            addProperty("backup_created_at", restore.manifest.createdAt.toString())
            addProperty("database_id", restore.manifest.databaseId)
            safetyBackup?.let { addProperty("safety_backup", it) }
            backupFileName?.takeIf { it.isNotEmpty() }?.let { addProperty("backup_file_name", it) }
            backupFileSize?.let { addProperty("backup_file_size", it) }
            addProperty("keep_photos", !restore.manifest.photosIncluded)
        }
        writeAtomically(readyMarker, GSON.toJson(marker))
    }

    /** Drops a restore that was scheduled but not yet installed. */
    fun cancelPending() {
        if (!stateFile.exists()) pendingDir.deleteRecursively()
    }

    /**
     * Installs the restore waiting in [pendingDir], if any, or finishes one a kill interrupted. Never throws:
     * whatever happens, the app then opens either the restored data or the data it had.
     *
     * Returns how a restore ended during this call, for the app to say so as it starts; null when none did.
     */
    fun installPending(): RestoreResult? = synchronized(RestoreInstaller) {
        endedNow = null
        if (!stateFile.exists() && !readyMarker.exists()) {
            // Left by a schedule that never marked its restore ready, or by a kill after an install finished.
            if (pendingDir.exists()) pendingDir.deleteRecursively()
            if (previousDir.exists()) previousDir.deleteRecursively()
            return@synchronized null
        }
        try {
            when (val state = readState()) {
                null -> install(attempt = 1)
                State.FINISHING -> finish(readMarker())
                else -> {
                    Log.w(TAG, "an install was interrupted at ${state.stage}; putting the data back")
                    putBack()
                    if (state.attempt >= MAX_ATTEMPTS) abandon("interrupted ${state.attempt} times")
                    else if (readyMarker.isFile) install(state.attempt + 1)
                }
            }
        } catch (t: Throwable) {
            if (t is Interrupted) throw t
            try {
                if (readState()?.stage == STAGE_FINISHING) {
                    // The restored database is in place and checked; only recording it failed. Putting files
                    // back now would undo a good restore, so keep it and clear what is left.
                    Log.e(TAG, "restore installed, but not recorded", t)
                    readyMarker.delete()
                    stateFile.delete()
                    pendingDir.deleteRecursively()
                    previousDir.deleteRecursively()
                } else {
                    Log.e(TAG, "restore install failed; putting the data back", t)
                    putBack()
                    abandon("${t.javaClass.simpleName}: ${t.message}")
                }
            } catch (again: Throwable) {
                // The stage file stays, so the next start tries again.
                Log.e(TAG, "could not put the data back", again)
            }
        }
        endedNow
    }

    /** The result [writeResult] wrote during the current [installPending]. */
    private var endedNow: RestoreResult? = null

    /** How the last restore ended, until [clearResult]. */
    fun lastResult(): RestoreResult? {
        if (!resultFile.isFile) return null
        return try {
            val json = JsonParser().parse(resultFile.readText()).asJsonObject
            RestoreResult(
                installed = json.get("installed").asBoolean,
                at = Instant.parse(json.get("at").asString),
                backupCreatedAt = json.get("backup_created_at")?.takeIf { !it.isJsonNull }?.asString?.let(Instant::parse),
                safetyBackup = json.get("safety_backup")?.takeIf { !it.isJsonNull }?.asString,
                detail = json.get("detail")?.takeIf { !it.isJsonNull }?.asString,
            )
        } catch (e: RuntimeException) {
            null
        }
    }

    fun clearResult() {
        resultFile.delete()
    }

    private fun install(attempt: Int) {
        val marker = readMarker()
        val keepPhotos = keepsPhotos(marker)
        val restored = File(pendingDir, BackupFormat.DATABASE_ENTRY)
        if (!restored.isFile) {
            abandon("the prepared database is missing")
            return
        }

        // An earlier install cleans previous/ up before it ends; files still there are someone's data.
        if (previousDir.list()?.isNotEmpty() == true) throw java.io.IOException("files from an earlier install remain in $previousDir")
        writeState(State(STAGE_MOVING_OLD, attempt))
        previousDir.mkdirs()
        for (file in liveDatabaseFiles()) move(file, File(previousDir, file.name))
        onPoint?.invoke("moved-old-database")
        if (!keepPhotos && imagesDir.exists()) move(imagesDir, File(previousDir, IMAGES))
        onPoint?.invoke("moved-old")

        writeState(State(STAGE_MOVING_NEW, attempt))
        move(restored, databaseFile)
        onPoint?.invoke("moved-new-database")
        if (!keepPhotos) File(pendingDir, IMAGES).takeIf { it.exists() }?.let { move(it, imagesDir) }
        onPoint?.invoke("moved-new")
        check(databaseFile, marker)?.let { problem ->
            putBack()
            abandon(problem)
            return
        }

        writeState(State.FINISHING)
        onPoint?.invoke("checked")
        finish(marker)
    }

    /**
     * The swap is done and checked: record it, then delete what is no longer needed. Safe to repeat: the
     * record is written while the ready marker exists, and the marker is deleted once it is, so a start
     * that finds this stage without a marker only has cleaning left to do.
     */
    private fun finish(marker: JsonObject?) {
        if (marker != null) {
            val now = Instant.now()
            val sql = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
            try {
                val backupAt = marker.text("backup_created_at") ?: ""
                sql.execSQL(
                    "INSERT OR REPLACE INTO app_meta (key, value) VALUES (?, ?), (?, ?)",
                    arrayOf(KEY_RESTORED_AT, now.toString(), KEY_RESTORED_BACKUP_AT, backupAt)
                )
                // The data is now exactly the backup restored, which is a backup of it: that is its last backup,
                // not whatever the file recorded before it was made — a backup is recorded only after its copy.
                if (backupAt.isNotEmpty()) {
                    sql.execSQL("DELETE FROM app_meta WHERE key IN (?, ?)", arrayOf(BackupCreator.KEY_LAST_SIZE, BackupCreator.KEY_LAST_NAME))
                    sql.execSQL("INSERT OR REPLACE INTO app_meta (key, value) VALUES (?, ?)", arrayOf(BackupCreator.KEY_LAST_AT, backupAt))
                    marker.text("backup_file_name")?.let {
                        sql.execSQL("INSERT INTO app_meta (key, value) VALUES (?, ?)", arrayOf(BackupCreator.KEY_LAST_NAME, it))
                    }
                    marker.text("backup_file_size")?.let {
                        sql.execSQL("INSERT INTO app_meta (key, value) VALUES (?, ?)", arrayOf(BackupCreator.KEY_LAST_SIZE, it))
                    }
                }
            } finally {
                sql.close()
            }
            clearImageBackfill()
            writeResult(installed = true, at = now, marker = marker, detail = null)
            Log.i(TAG, "restore installed: backup of ${marker.text("backup_created_at")}")
            onPoint?.invoke("recorded")
            readyMarker.delete()
        }
        onPoint?.invoke("marker-deleted")
        stateFile.delete()
        pendingDir.deleteRecursively()
        previousDir.deleteRecursively()
    }

    /**
     * Returns every file to where it was before the install began: restored files back to [pendingDir],
     * the phone's own back from [previousDir]. Only moves what is still out of place, so it can be repeated.
     */
    private fun putBack() {
        if (readState()?.stage == STAGE_MOVING_NEW) {
            // By this stage all of the phone's own files are in previous/, so whatever is in place is restored.
            val restored = File(pendingDir, BackupFormat.DATABASE_ENTRY)
            if (databaseFile.isFile && !restored.exists()) move(databaseFile, restored)
            for (suffix in JOURNALS) File(databaseFile.path + suffix).delete()
            val restoredImages = File(pendingDir, IMAGES)
            // With a backup that has no photos, the folder in place is the phone's own, and stays.
            if (!keepsPhotos(readMarker()) && imagesDir.exists() && !restoredImages.exists()) move(imagesDir, restoredImages)
        }
        if (previousDir.isDirectory) {
            for (file in previousDir.listFiles().orEmpty()) {
                val home = if (file.name == IMAGES) imagesDir else File(databaseFile.parentFile, file.name)
                // Nothing should be there by now; if something is, stop rather than lose either copy.
                if (home.exists()) throw java.io.IOException("$home is in the way of putting the data back")
                move(file, home)
            }
            previousDir.deleteRecursively()
        }
        stateFile.delete()
    }

    /** Gives up on the waiting restore: the phone keeps its data, and the screen is told why. */
    private fun abandon(detail: String) {
        Log.w(TAG, "restore abandoned: $detail")
        writeResult(installed = false, at = Instant.now(), marker = readMarker(), detail = detail)
        pendingDir.deleteRecursively()
        previousDir.deleteRecursively()
        stateFile.delete()
    }

    /** The restored database in its place: whole, at this app's version, and the data that was prepared. */
    private fun check(database: File, marker: JsonObject?): String? {
        val sql = try {
            SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS, DatabaseSnapshot.KEEP_CORRUPT_FILE)
        } catch (e: RuntimeException) {
            return "the restored database does not open: ${e.message}"
        }
        return try {
            val quick = sql.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst(); it.getString(0) }
            val id = sql.rawQuery("SELECT value FROM app_meta WHERE key = 'database_id'", null).use { if (it.moveToFirst()) it.getString(0) else null }
            when {
                quick != "ok" -> "quick_check: $quick"
                sql.version != DATABASE_VERSION -> "the restored database is at version ${sql.version}"
                marker != null && id != marker.text("database_id") -> "the restored database is not the one prepared"
                else -> null
            }
        } catch (e: RuntimeException) {
            "the restored database cannot be read: ${e.message}"
        } finally {
            sql.close()
        }
    }

    private fun liveDatabaseFiles(): List<File> =
        (listOf("") + JOURNALS).map { File(databaseFile.path + it) }.filter { it.exists() }

    private fun move(from: File, to: File) {
        to.parentFile?.mkdirs()
        if (!from.renameTo(to)) throw java.io.IOException("cannot move $from to $to")
    }

    private data class State(val stage: String, val attempt: Int) {
        companion object {
            val FINISHING = State(STAGE_FINISHING, 0)
        }
    }

    private fun readState(): State? {
        if (!stateFile.isFile) return null
        val parts = stateFile.readText().trim().split(' ')
        val stage = parts.getOrNull(0) ?: return null
        if (stage == STAGE_FINISHING) return State.FINISHING
        return State(stage, parts.getOrNull(1)?.toIntOrNull() ?: MAX_ATTEMPTS)
    }

    private fun writeState(state: State) = writeAtomically(stateFile, "${state.stage} ${state.attempt}")

    /** Whether the waiting restore leaves the phone's photos alone: its backup holds none. */
    private fun keepsPhotos(marker: JsonObject?): Boolean =
        marker?.get("keep_photos")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: false

    private fun readMarker(): JsonObject? = try {
        readyMarker.takeIf { it.isFile }?.let { JsonParser().parse(it.readText()).asJsonObject }
    } catch (e: RuntimeException) {
        null
    }

    private fun writeResult(installed: Boolean, at: Instant, marker: JsonObject?, detail: String?) {
        val json = JsonObject().apply {
            addProperty("installed", installed)
            addProperty("at", at.toString())
            addProperty("backup_created_at", marker?.text("backup_created_at"))
            addProperty("safety_backup", marker?.text("safety_backup"))
            addProperty("detail", detail)
        }
        writeAtomically(resultFile, GSON.toJson(json))
        endedNow = RestoreResult(installed, at, marker?.text("backup_created_at")?.let(Instant::parse), marker?.text("safety_backup"), detail)
    }

    private fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

    /** A kill mid-write leaves the old file or the new one, never half of one. */
    private fun writeAtomically(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) throw java.io.IOException("cannot write $file")
    }

    companion object {
        private const val TAG = "RestoreInstaller"
        private const val IMAGES = "images"
        private val JOURNALS = listOf("-wal", "-shm", "-journal")

        private const val STAGE_MOVING_OLD = "moving-old"
        private const val STAGE_MOVING_NEW = "moving-new"
        private const val STAGE_FINISHING = "finishing"

        /** Starts an install may be interrupted before it gives up and keeps the phone's data. */
        const val MAX_ATTEMPTS = 3

        /** When the data on the phone was last replaced by a restore: a UTC instant. */
        const val KEY_RESTORED_AT = "restore.last_at"

        /** When the backup it was replaced by was made. */
        const val KEY_RESTORED_BACKUP_AT = "restore.backup_created_at"

        private val GSON = GsonBuilder().disableHtmlEscaping().serializeNulls().create()

        fun forApp(context: Context): RestoreInstaller {
            val app = context.applicationContext
            return RestoreInstaller(
                databaseFile = app.getDatabasePath(DATABASE_NAME),
                imagesDir = ImageStore.imagesDir(app),
                restoreDir = File(app.noBackupFilesDir, "restore"),
                clearImageBackfill = { ImageBackfill.reset(app) },
            )
        }
    }
}
