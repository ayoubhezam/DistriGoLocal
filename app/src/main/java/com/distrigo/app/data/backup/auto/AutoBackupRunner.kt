package com.distrigo.app.data.backup.auto

import android.content.Context
import android.net.Uri
import android.util.Log
import com.distrigo.app.data.backup.BackupCreator
import com.distrigo.app.data.backup.BackupFailedException
import com.distrigo.app.data.backup.DataFingerprint
import com.distrigo.app.data.local.database.AppDatabase
import java.io.File
import java.io.IOException
import java.time.Instant

sealed class AutoBackupOutcome {
    /** Nothing changed since the last backup, which is still where it was saved. */
    data object Unchanged : AutoBackupOutcome()

    /**
     * A backup was saved to [folderName]. [inPrivateStorage] means the chosen folder could not be used, or none is
     * chosen, so it went to the app's own storage, which does not survive the app being uninstalled.
     */
    data class Saved(
        val fileName: String,
        val size: Long,
        val folderName: String,
        val inPrivateStorage: Boolean,
        val removed: Int,
    ) : AutoBackupOutcome()

    data class Failed(val reason: BackupFailedException.Reason, val detail: String) : AutoBackupOutcome()
}

/**
 * One automatic backup: skipped when the data has not changed, otherwise saved to the chosen folder — or to the
 * app's own storage when that folder cannot be used — after which only the newest [keep] automatic backups are
 * kept there.
 *
 * The fingerprint recorded is the one of the copy that was saved (see [CreatedBackup.fingerprint]), so a change
 * made while the backup runs is not yet recorded and makes the next run back up again: one backup too many at
 * worst, never one too few. The live fingerprint is only read first to skip a day without changes.
 */
class AutoBackupRunner(
    private val db: AppDatabase,
    private val creator: BackupCreator,
    val store: AutoBackupStore,
    /** The folder a stored tree address names. */
    private val pickedFolder: (String) -> BackupFolder,
    private val privateFolder: BackupFolder,
    private val keep: Int = KEEP,
    private val clock: () -> Instant = Instant::now,
) {

    /** The folder backups would go to now, and whether it is the one the user chose. */
    fun currentFolder(): Pair<BackupFolder, Boolean> {
        val picked = store.read().folderUri?.let(pickedFolder)?.takeIf { it.isAvailable() }
        return if (picked != null) picked to true else privateFolder to false
    }

    /**
     * Blocks for the whole backup: call it off the main thread. [force] saves even when nothing changed.
     *
     * Every run is recorded: when it happened, and whether it left a problem, counted in a streak that a run
     * without one resets.
     */
    fun run(force: Boolean = false): AutoBackupOutcome = synchronized(LOCK) {
        val state = store.read()
        var folderUsable = false
        val outcome = try {
            val (folder, isPicked) = currentFolder()
            folderUsable = isPicked
            attempt(force, state, folder, isPicked)
        } catch (e: Exception) {
            record(AutoBackupProblem.UNEXPECTED)
            throw e
        }
        record(AutoBackupProblem.after(outcome, folderChosen = state.folderUri != null, folderUsable = folderUsable))
        outcome
    }

    /** Records a run that did not get to report an outcome of its own, such as one that crashed. */
    fun record(problem: AutoBackupProblem?) {
        store.update {
            it.copy(
                lastAttemptAt = clock(),
                problemStreak = if (problem == null) 0 else it.problemStreak + 1,
                lastProblem = problem,
            )
        }
    }

    private fun attempt(force: Boolean, state: AutoBackupState, folder: BackupFolder, isPicked: Boolean): AutoBackupOutcome {
        val location = if (isPicked) BackupLocation.PICKED_FOLDER else BackupLocation.PRIVATE

        val fingerprint = DataFingerprint.of(db.openHelper.writableDatabase)
        if (!force && fingerprint == state.lastFingerprint && location == state.lastLocation &&
            state.lastFileName != null && folder.list().any { it.name == state.lastFileName }
        ) {
            return AutoBackupOutcome.Unchanged
        }

        val name = AutoBackupNames.forTime(clock())
        val created = try {
            if (!folder.isAvailable()) throw IOException("${folder.displayName} is not available")
            // Recorded as the data's last backup only where the user keeps backups.
            creator.create(folder.create(name), record = isPicked)
        } catch (e: BackupFailedException) {
            Log.w(TAG, "automatic backup failed", e)
            return AutoBackupOutcome.Failed(e.reason, e.message ?: e.reason.name)
        } catch (e: Exception) {
            if (e !is IOException && e !is SecurityException && e !is IllegalArgumentException && e !is UnsupportedOperationException) throw e
            // The folder refused the file before a backup began: BackupCreator had nothing to clean up.
            Log.w(TAG, "automatic backup could not be created", e)
            return AutoBackupOutcome.Failed(BackupFailedException.Reason.WRITE_FAILED, e.message ?: e.javaClass.simpleName)
        }

        store.update {
            it.copy(lastFingerprint = created.fingerprint, lastAt = created.manifest.createdAt, lastFileName = name, lastLocation = location)
        }
        return AutoBackupOutcome.Saved(name, created.size, folder.displayName, !isPicked, rotate(folder))
    }

    /** Keeps the newest [keep] automatic backups in [folder]. A file that will not delete is left for the next run. */
    private fun rotate(folder: BackupFolder): Int = try {
        val files = folder.list()
        val old = AutoBackupNames.beyondNewest(files.map { it.name }, keep).toSet()
        files.filter { it.name in old }.count { folder.delete(it) }
    } catch (e: Exception) {
        Log.w(TAG, "could not remove old automatic backups", e)
        0
    }

    companion object {
        private const val TAG = "AutoBackupRunner"
        private val LOCK = Any()

        /** How many automatic backups are kept. */
        const val KEEP = 7

        fun forApp(context: Context, db: AppDatabase, creator: BackupCreator, store: AutoBackupStore): AutoBackupRunner {
            val app = context.applicationContext
            return AutoBackupRunner(
                db = db,
                creator = creator,
                store = store,
                pickedFolder = { PickedFolder(app.contentResolver, Uri.parse(it)) },
                privateFolder = PrivateFolder(File(app.noBackupFilesDir, "auto-backup/files")),
            )
        }
    }
}
