package com.distrigo.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * A backup started from the screen and run in the background (owner, 2026-10-21): « Créer une sauvegarde » to the
 * file the user picked, or « Sauvegarder maintenant » to the automatic backups' folder. WorkManager runs it, one at
 * a time, so the user can leave the screen and keep working; a notification follows it, and the screen shows it
 * while open. A backup takes about 40 s on a large business (M34, 2026-10-21).
 */
object BackupJob {
    const val UNIQUE_NAME = "backup_now"
    internal const val KEY_DESTINATION = "destination"
    internal const val KEY_STEP = "step"
    private const val TAG_TO_FOLDER = "backup_to_folder"

    /** Starts a backup to [destination], or to the automatic backups' folder when null; nothing if one runs already. */
    fun start(context: Context, destination: Uri?) {
        val request = OneTimeWorkRequestBuilder<BackupNowWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(KEY_DESTINATION to destination?.toString()))
            .apply { if (destination == null) addTag(TAG_TO_FOLDER) }
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** The latest backup started from the screen — running, or done with its result; null when none is known. */
    fun observe(context: Context): Flow<BackupJobState?> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UNIQUE_NAME).map { infos -> infos.lastOrNull()?.let(::stateOf) }

    private fun stateOf(info: WorkInfo): BackupJobState {
        val toFolder = TAG_TO_FOLDER in info.tags
        return when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED ->
                BackupJobState.Running(info.id, toFolder, info.progress.getString(KEY_STEP) ?: "Préparation…")
            WorkInfo.State.SUCCEEDED -> BackupJobState.Done(info.id, toFolder, BackupJobResult.of(info.outputData))
            // The worker always reports success; this is WorkManager stopping it (the app killed, the job cancelled)
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> BackupJobState.Done(info.id, toFolder, BackupJobResult.Failed(STOPPED))
        }
    }

    internal const val STOPPED = "La sauvegarde s'est arrêtée avant la fin. Recommencez."
}

/** A backup job as the screen shows it; [toFolder] for « Sauvegarder maintenant », else a file the user picked. */
sealed class BackupJobState {
    abstract val id: UUID
    abstract val toFolder: Boolean

    data class Running(override val id: UUID, override val toFolder: Boolean, val step: String) : BackupJobState()
    data class Done(override val id: UUID, override val toFolder: Boolean, val result: BackupJobResult) : BackupJobState()
}

/** How a backup job ended, carried by WorkManager from the worker to the screen. */
sealed class BackupJobResult {
    /**
     * Saved as [fileName], of [size] bytes. To a picked file: with [photos], and [photosLeftOut] missing or damaged
     * on the phone. To the folder: in [folderName], [removed] old automatic backups deleted, [inPrivateStorage] when
     * the folder could not be used.
     */
    data class Saved(
        val fileName: String,
        val size: Long,
        val photos: Int = 0,
        val photosLeftOut: Int = 0,
        val folderName: String? = null,
        val removed: Int = 0,
        val inPrivateStorage: Boolean = false,
    ) : BackupJobResult()

    /** Nothing changed since the folder's last backup, which is still there. */
    data object Unchanged : BackupJobResult()

    data class Failed(val message: String) : BackupJobResult()

    fun toData(): Data = when (this) {
        is Saved -> workDataOf(
            KIND to "saved", FILE to fileName, SIZE to size, PHOTOS to photos, LEFT_OUT to photosLeftOut,
            FOLDER to folderName, REMOVED to removed, PRIVATE to inPrivateStorage,
        )
        Unchanged -> workDataOf(KIND to "unchanged")
        is Failed -> workDataOf(KIND to "failed", MESSAGE to message)
    }

    companion object {
        private const val KIND = "kind"
        private const val FILE = "file"
        private const val SIZE = "size"
        private const val PHOTOS = "photos"
        private const val LEFT_OUT = "left_out"
        private const val FOLDER = "folder"
        private const val REMOVED = "removed"
        private const val PRIVATE = "private"
        private const val MESSAGE = "message"

        fun of(data: Data): BackupJobResult = when (data.getString(KIND)) {
            "saved" -> Saved(
                fileName = data.getString(FILE).orEmpty(),
                size = data.getLong(SIZE, 0),
                photos = data.getInt(PHOTOS, 0),
                photosLeftOut = data.getInt(LEFT_OUT, 0),
                folderName = data.getString(FOLDER),
                removed = data.getInt(REMOVED, 0),
                inPrivateStorage = data.getBoolean(PRIVATE, false),
            )
            "unchanged" -> Unchanged
            else -> Failed(data.getString(MESSAGE) ?: BackupJob.STOPPED)
        }
    }
}
