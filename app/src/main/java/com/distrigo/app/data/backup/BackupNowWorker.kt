package com.distrigo.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.distrigo.app.data.backup.auto.AutoBackupOutcome
import com.distrigo.app.data.backup.auto.AutoBackupRunner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a [BackupJob]: the backup itself, its steps as WorkManager progress and as the notification, then the
 * result. It always reports success to WorkManager, the outcome in its output: a failed backup is not one to try
 * again by itself — the folder or the space is usually still the problem — and the user is told.
 */
@HiltWorker
class BackupNowWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val creator: BackupCreator,
    private val runner: AutoBackupRunner,
    private val notifier: BackupJobNotifier,
) : CoroutineWorker(context, params) {

    private val destination: Uri? = inputData.getString(BackupJob.KEY_DESTINATION)?.let(Uri::parse)
    private val toFolder get() = destination == null

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        step(if (toFolder) "Sauvegarde dans le dossier…" else "Copie des données…")
        val result = try {
            destination?.let(::toFile) ?: toFolder()
        } catch (e: BackupFailedException) {
            BackupJobResult.Failed(BackupMessages.of(e.reason))
        } catch (e: Exception) {
            Log.e(TAG, "backup stopped", e)
            BackupJobResult.Failed(BackupJob.STOPPED)
        } finally {
            destination?.let(::release)
        }
        notifier.finished(result, toFolder)
        Result.success(result.toData())
    }

    /** Android 11 and older run an expedited job as a foreground service, which shows this. */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(BackupJobNotifier.NOTIFICATION_ID, notifier.progressNotification("Sauvegarde en cours…", toFolder))

    private fun toFile(uri: Uri): BackupJobResult {
        val created = creator.create(uri) { step ->
            step(
                when (step) {
                    BackupCreator.Step.COPYING_DATABASE -> "Copie des données…"
                    BackupCreator.Step.BUILDING_FILE -> "Préparation du fichier…"
                    BackupCreator.Step.SAVING -> "Enregistrement…"
                    BackupCreator.Step.VERIFYING -> "Vérification de la sauvegarde…"
                }
            )
        }
        return BackupJobResult.Saved(
            fileName = created.fileName,
            size = created.size,
            photos = created.manifest.images.size,
            photosLeftOut = created.missingPhotos + created.damagedPhotos,
        )
    }

    private fun toFolder(): BackupJobResult = when (val outcome = runner.run(force = true)) {
        is AutoBackupOutcome.Saved -> BackupJobResult.Saved(
            fileName = outcome.fileName,
            size = outcome.size,
            folderName = outcome.folderName,
            removed = outcome.removed,
            inPrivateStorage = outcome.inPrivateStorage,
        )
        is AutoBackupOutcome.Failed -> BackupJobResult.Failed(BackupMessages.of(outcome.reason))
        AutoBackupOutcome.Unchanged -> BackupJobResult.Unchanged
    }

    private fun step(label: String) {
        setProgressAsync(workDataOf(BackupJob.KEY_STEP to label))
        notifier.progress(label, toFolder)
    }

    /** The picked file's grant, taken to outlive the screen, is not needed once the backup is written. */
    private fun release(uri: Uri) {
        try {
            applicationContext.contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Never taken: the provider did not offer a lasting grant, and the screen's grant was enough.
        }
    }

    private companion object {
        const val TAG = "BackupNowWorker"
    }
}
