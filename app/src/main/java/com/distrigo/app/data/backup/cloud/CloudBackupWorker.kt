package com.distrigo.app.data.backup.cloud

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Runs [CloudBackupEngine] for the daily job and for « Sauvegarder maintenant » ([KEY_MANUAL]).
 *
 * Unlike the local backup's worker, a network failure asks WorkManager to retry (with the backoff set by
 * [CloudBackupScheduler]) up to [MAX_ATTEMPTS] times: the connection usually comes back, and the upload resumes where it
 * stopped. Every other failure ends the run and is recorded once, for the alerts. A job stopped by WorkManager (its
 * time limit, Wi-Fi lost) is simply stopped: the pending upload is kept and resumed by the next run.
 */
@HiltWorker
class CloudBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: CloudBackupEngine,
    private val store: CloudBackupStore,
    private val alerts: CloudBackupAlerts,
) : CoroutineWorker(context, params) {

    private val manual get() = inputData.getBoolean(KEY_MANUAL, false)

    override suspend fun doWork(): Result {
        val state = store.read()
        if (!state.linked || (!manual && !state.enabled)) return Result.success()
        val outcome = try {
            engine.run(force = manual) { progress -> setProgressAsync(progressData(progress)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "cloud backup crashed", e)
            CloudBackupOutcome.Failed(CloudBackupProblem.UNEXPECTED, false, e.javaClass.simpleName)
        }
        Log.i(TAG, "cloud backup: $outcome (attempt ${runAttemptCount + 1})")
        if (outcome is CloudBackupOutcome.Failed && outcome.retryable && runAttemptCount + 1 < MAX_ATTEMPTS) {
            return Result.retry()
        }
        alerts.update(engine.record(outcome, scheduled = !manual))
        return Result.success(workDataOf(KEY_OUTCOME to outcomeName(outcome)))
    }

    /** Android 11 and older run an expedited job as a foreground service, which shows this. */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(CloudBackupNotifications.PROGRESS_ID, CloudBackupNotifications.progress(applicationContext))

    private fun progressData(progress: CloudBackupProgress) = when (progress) {
        CloudBackupProgress.Preparing -> workDataOf(KEY_STEP to STEP_PREPARING)
        CloudBackupProgress.Encrypting -> workDataOf(KEY_STEP to STEP_ENCRYPTING)
        is CloudBackupProgress.Photos ->
            workDataOf(KEY_STEP to STEP_PHOTOS, KEY_SENT to progress.done.toLong(), KEY_TOTAL to progress.total.toLong())
        is CloudBackupProgress.Uploading ->
            workDataOf(KEY_STEP to STEP_UPLOADING, KEY_SENT to progress.sent, KEY_TOTAL to progress.total)
    }

    companion object {
        private const val TAG = "CloudBackupWorker"
        const val KEY_MANUAL = "manual"
        const val KEY_OUTCOME = "outcome"
        const val KEY_STEP = "step"
        const val KEY_SENT = "sent"
        const val KEY_TOTAL = "total"
        const val STEP_PREPARING = "preparing"
        const val STEP_ENCRYPTING = "encrypting"
        const val STEP_UPLOADING = "uploading"

        /** [KEY_SENT] photos checked of [KEY_TOTAL]. */
        const val STEP_PHOTOS = "photos"

        /** The first run and four retries (30 min, 1 h, 2 h, 4 h): a whole night's worth. */
        const val MAX_ATTEMPTS = 5

        fun outcomeName(outcome: CloudBackupOutcome): String = outcome.logName
    }
}
