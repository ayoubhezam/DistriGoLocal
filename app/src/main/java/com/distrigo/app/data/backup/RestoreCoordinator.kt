package com.distrigo.app.data.backup

import android.net.Uri
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed class RestoreOutcome {
    /** Ready: the app must restart now, and the restore is installed as it starts. */
    data class Scheduled(val manifest: BackupManifest, val safetyBackup: File) : RestoreOutcome()

    /** The backup cannot be restored; nothing changed. */
    data class NotRestorable(val problem: BackupProblem) : RestoreOutcome()

    /** The current data could not be saved first, so the restore did not go ahead; nothing changed. */
    data class SafetyBackupFailed(val reason: BackupFailedException.Reason) : RestoreOutcome()
}

/**
 * A restore from start to scheduled: prepare the backup, save the current data, then schedule the install.
 *
 * The safety backup comes after the preparation, so a file that turns out not to be restorable costs no
 * copy, and before scheduling, so no restore is ever installed without one. It is a normal `.distrigo`
 * file in [RestoreInstaller.safetyDir], restorable the same way; the newest [KEEP_SAFETY_BACKUPS] are kept.
 * They live in the app's private storage, so they undo a restore but do not survive uninstalling the app.
 */
class RestoreCoordinator(
    private val preparer: RestorePreparer,
    private val creator: BackupCreator,
    private val installer: RestoreInstaller,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    enum class Step { CHECKING_BACKUP, SAVING_CURRENT_DATA, SCHEDULING }

    /** Blocks for the whole preparation: call it off the main thread. [expected] is the manifest the user confirmed. */
    fun restore(uri: Uri, expected: BackupManifest?, onStep: (Step) -> Unit = {}): RestoreOutcome {
        onStep(Step.CHECKING_BACKUP)
        val restore = when (val preparation = preparer.prepare(uri, expected)) {
            is Preparation.Failed -> return RestoreOutcome.NotRestorable(preparation.problem)
            is Preparation.Ready -> preparation.restore
        }

        onStep(Step.SAVING_CURRENT_DATA)
        val safety = File(installer.safetyDir, safetyName(Instant.now()))
        try {
            installer.safetyDir.mkdirs()
            creator.create(Uri.fromFile(safety), record = false)
        } catch (e: BackupFailedException) {
            Log.w(TAG, "no safety backup, so no restore", e)
            restore.dir.deleteRecursively()
            return RestoreOutcome.SafetyBackupFailed(e.reason)
        } catch (e: RuntimeException) {
            restore.dir.deleteRecursively()
            throw e
        }

        onStep(Step.SCHEDULING)
        try {
            installer.schedule(restore, safety.name)
        } catch (e: java.io.IOException) {
            Log.w(TAG, "could not schedule the restore", e)
            restore.dir.deleteRecursively()
            installer.cancelPending()
            return RestoreOutcome.SafetyBackupFailed(BackupFailedException.Reason.WRITE_FAILED)
        }
        rotateSafetyBackups()
        return RestoreOutcome.Scheduled(restore.manifest, safety)
    }

    /** The safety backups kept, newest first. */
    fun safetyBackups(): List<File> =
        installer.safetyDir.listFiles { file -> file.isFile && file.name.startsWith(SAFETY_PREFIX) && file.name.endsWith(".${BackupFormat.EXTENSION}") }
            .orEmpty()
            .sortedByDescending { it.name }

    private fun rotateSafetyBackups() {
        safetyBackups().drop(KEEP_SAFETY_BACKUPS).forEach { it.delete() }
    }

    /** Sorts by time as text, and to the second, so two restores in one minute keep both copies. */
    private fun safetyName(at: Instant): String =
        SAFETY_PREFIX + SAFETY_STAMP.format(at.atZone(zone())) + ".${BackupFormat.EXTENSION}"

    companion object {
        private const val TAG = "RestoreCoordinator"
        const val KEEP_SAFETY_BACKUPS = 3
        const val SAFETY_PREFIX = "avant-restauration-"
        private val SAFETY_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
    }
}
