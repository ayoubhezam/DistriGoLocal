package com.distrigo.app.data.backup

/**
 * What the user is told when a backup cannot be made or restored: what happened, and what to do next.
 * Technical detail stays in the logs.
 */
object BackupMessages {

    private const val UPDATE_APP =
        "Cette sauvegarde a été créée par une version plus récente de DistriGo. Mettez l'application à jour, puis réessayez."

    fun of(problem: BackupProblem): String = when (problem) {
        BackupProblem.CannotOpen ->
            "Impossible d'ouvrir ce fichier. Vérifiez qu'il est toujours disponible, puis choisissez-le à nouveau."
        BackupProblem.NotABackup ->
            "Ce fichier n'est pas une sauvegarde DistriGo. Choisissez un fichier .distrigo."
        is BackupProblem.NewerFormat -> UPDATE_APP
        is BackupProblem.NewerDatabase -> UPDATE_APP
        is BackupProblem.DatabaseTooOld ->
            "Cette sauvegarde provient d'une version trop ancienne de DistriGo et ne peut pas être restaurée."
        is BackupProblem.Damaged ->
            "Cette sauvegarde est endommagée ou incomplète et ne peut pas être restaurée. Essayez une autre copie."
    }

    fun of(reason: BackupFailedException.Reason): String = when (reason) {
        BackupFailedException.Reason.DATABASE_DAMAGED ->
            "Les données de l'application n'ont pas passé la vérification : aucune sauvegarde n'a été créée."
        BackupFailedException.Reason.WRITE_FAILED ->
            "Impossible d'enregistrer la sauvegarde à cet emplacement. Vérifiez l'espace disponible ou choisissez un autre emplacement."
        BackupFailedException.Reason.NOT_VERIFIED ->
            "La sauvegarde enregistrée ne se relit pas correctement : elle a été supprimée. Réessayez ou choisissez un autre emplacement."
    }
}
