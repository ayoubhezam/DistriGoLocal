package com.distrigo.app.data.backup

import com.distrigo.app.R

/**
 * What the user is told when a backup cannot be made or restored: what happened, and what to do next.
 * Technical detail stays in the logs.
 */
object BackupMessages {

    private val UPDATE_APP: String get() = backupText(R.string.backup_update_app)

    fun of(problem: BackupProblem): String = when (problem) {
        BackupProblem.CannotOpen ->
            backupText(R.string.backup_cannot_open)
        is BackupProblem.NotEnoughSpace ->
            if (problem.neededBytes > 0) {
                backupText(R.string.backup_no_space_mb, megabytes(problem.neededBytes))
            } else {
                backupText(R.string.backup_no_space)
            }
        BackupProblem.NotABackup ->
            backupText(R.string.backup_not_a_backup)
        is BackupProblem.NewerFormat -> UPDATE_APP
        is BackupProblem.NewerDatabase -> UPDATE_APP
        is BackupProblem.DatabaseTooOld ->
            backupText(R.string.backup_too_old)
        is BackupProblem.Damaged ->
            backupText(R.string.backup_damaged)
    }

    /** A restore that did not go ahead because the current data could not be saved first. */
    fun safetyBackupFailed(reason: BackupFailedException.Reason): String = when (reason) {
        BackupFailedException.Reason.DATABASE_DAMAGED ->
            backupText(R.string.backup_safety_damaged)
        else ->
            backupText(R.string.backup_safety_failed)
    }

    /** How the last restore ended, told once the app has restarted. */
    fun of(result: RestoreResult): String =
        if (result.installed) {
            backupText(R.string.backup_restore_done)
        } else {
            backupText(R.string.backup_restore_not_installed)
        }

    /** Whole megabytes, rounded up: asking for too little space would fail again. */
    internal fun megabytes(bytes: Long): Long = (bytes + (1L shl 20) - 1) shr 20

    fun of(reason: BackupFailedException.Reason): String = when (reason) {
        BackupFailedException.Reason.DATABASE_DAMAGED ->
            backupText(R.string.backup_data_damaged)
        BackupFailedException.Reason.WRITE_FAILED ->
            backupText(R.string.backup_write_failed)
        BackupFailedException.Reason.NOT_VERIFIED ->
            backupText(R.string.backup_not_verified)
    }
}
