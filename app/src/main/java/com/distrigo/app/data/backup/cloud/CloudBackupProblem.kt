package com.distrigo.app.data.backup.cloud

import com.distrigo.app.R
import com.distrigo.app.data.backup.BackupFailedException
import com.distrigo.app.data.backup.backupText

/**
 * Why a cloud run left the data without a fresh backup in the cloud.
 *
 * [needsTheUser]: it will not fix itself (Drive full, access lost, password needed…), so the user is told after the
 * first run; the others (no connection, Drive busy) are told only if they last, like the local backup's alerts.
 */
enum class CloudBackupProblem(val needsTheUser: Boolean) {
    /** No license has bound the data to a business yet. */
    NO_BUSINESS(true),

    /** The data now belongs to another business (a restore): link again for this one. */
    OTHER_BUSINESS(true),

    /** The phone lost its copy of the backup key (Keystore reset): type the password again. */
    NEEDS_PASSWORD(true),

    /** « Reconnecter Google Drive ». */
    NEEDS_CONSENT(true),

    /** « Google Drive est plein — la sauvegarde en ligne est suspendue » (owner's decision §9.2: alert only). */
    QUOTA_EXCEEDED(true),

    /** No Google Play services any more on this phone. */
    UNAVAILABLE(true),

    /** No connection, Drive busy or failing, or a copy that did not arrive whole. */
    NO_CONNECTION(false),

    DATABASE_DAMAGED(false),

    /** The phone had no room to prepare the backup. */
    NO_SPACE(false),

    UNEXPECTED(false);

    val message: String
        get() = backupText(
            when (this) {
                NO_BUSINESS -> R.string.cloud_backup_problem_no_business
                OTHER_BUSINESS -> R.string.cloud_backup_problem_other_business
                NEEDS_PASSWORD -> R.string.cloud_backup_problem_needs_password
                NEEDS_CONSENT -> R.string.cloud_backup_problem_needs_consent
                QUOTA_EXCEEDED -> R.string.cloud_backup_problem_quota
                UNAVAILABLE -> R.string.cloud_backup_problem_unavailable
                NO_CONNECTION -> R.string.cloud_backup_problem_no_connection
                DATABASE_DAMAGED -> R.string.backup_auto_data_damaged
                NO_SPACE -> R.string.cloud_backup_problem_no_space
                UNEXPECTED -> R.string.backup_auto_unexpected
            },
        )

    companion object {
        fun of(problem: CloudBackupException.Problem): CloudBackupProblem = when (problem) {
            CloudBackupException.Problem.UNAVAILABLE -> UNAVAILABLE
            CloudBackupException.Problem.NEEDS_CONSENT -> NEEDS_CONSENT
            CloudBackupException.Problem.QUOTA_EXCEEDED -> QUOTA_EXCEEDED
            CloudBackupException.Problem.RATE_LIMITED,
            CloudBackupException.Problem.SERVER,
            CloudBackupException.Problem.NETWORK,
            CloudBackupException.Problem.UPLOAD_CORRUPTED,
            CloudBackupException.Problem.NOT_FOUND -> NO_CONNECTION
            CloudBackupException.Problem.REJECTED -> UNEXPECTED
        }

        fun of(reason: BackupFailedException.Reason): CloudBackupProblem = when (reason) {
            BackupFailedException.Reason.DATABASE_DAMAGED -> DATABASE_DAMAGED
            BackupFailedException.Reason.WRITE_FAILED,
            BackupFailedException.Reason.NOT_VERIFIED -> NO_SPACE
        }
    }
}
