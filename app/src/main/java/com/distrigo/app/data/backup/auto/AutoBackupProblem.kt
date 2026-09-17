package com.distrigo.app.data.backup.auto

import com.distrigo.app.data.backup.BackupFailedException

/**
 * Why an automatic run left the data without a backup where the user keeps backups. A copy saved in the app's
 * own storage still counts as a problem: it is lost with the app, and the user does not know it is there.
 */
enum class AutoBackupProblem {
    NO_FOLDER,
    FOLDER_UNAVAILABLE,
    DATABASE_DAMAGED,
    WRITE_FAILED,
    NOT_VERIFIED,
    UNEXPECTED;

    /** What the user is told, in the notification and on the screen. */
    val message: String
        get() = when (this) {
            NO_FOLDER -> "Aucun dossier de sauvegarde n'est choisi : les sauvegardes restent dans l'application."
            FOLDER_UNAVAILABLE -> "Le dossier de sauvegarde n'est plus accessible : les sauvegardes restent dans l'application."
            DATABASE_DAMAGED -> "Les données de l'application n'ont pas passé la vérification : aucune sauvegarde n'a été faite."
            WRITE_FAILED -> "Impossible d'enregistrer la sauvegarde dans le dossier. Vérifiez l'espace disponible."
            NOT_VERIFIED -> "La sauvegarde enregistrée ne se relisait pas correctement et a été supprimée."
            UNEXPECTED -> "Une erreur inattendue a empêché la sauvegarde."
        }

    companion object {
        fun of(reason: BackupFailedException.Reason): AutoBackupProblem = when (reason) {
            BackupFailedException.Reason.DATABASE_DAMAGED -> DATABASE_DAMAGED
            BackupFailedException.Reason.WRITE_FAILED -> WRITE_FAILED
            BackupFailedException.Reason.NOT_VERIFIED -> NOT_VERIFIED
        }

        /**
         * The problem a run leaves, or null if the data is backed up where the user keeps backups. [folderChosen]
         * and [folderUsable] describe the chosen folder when the run started: while it cannot be used, even an
         * unchanged day is a problem, since the only backups are in the app's own storage.
         */
        fun after(outcome: AutoBackupOutcome, folderChosen: Boolean, folderUsable: Boolean): AutoBackupProblem? = when {
            outcome is AutoBackupOutcome.Failed -> of(outcome.reason)
            !folderChosen -> NO_FOLDER
            !folderUsable -> FOLDER_UNAVAILABLE
            else -> null
        }
    }
}
