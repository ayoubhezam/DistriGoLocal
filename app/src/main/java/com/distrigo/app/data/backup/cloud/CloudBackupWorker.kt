package com.distrigo.app.data.backup.cloud

import android.content.Context
import android.os.Build
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
    private val scheduler: CloudBackupScheduler,
) : CoroutineWorker(context, params) {

    private val manual get() = inputData.getBoolean(KEY_MANUAL, false)

    override suspend fun doWork(): Result {
        val state = store.read()
        if (!state.linked || (!manual && !state.enabled)) return Result.success()
        CloudJobHealth.running.incrementAndGet()
        // A night's run says it started: if the phone kills it without a word, the field shows it never finished.
        if (!manual) store.update { it.copy(lastScheduledOutcome = OUTCOME_STARTED) }
        val outcome = try {
            engine.run(force = manual) { progress -> setProgressAsync(progressData(progress)) }
        } catch (e: CancellationException) {
            if (!manual) store.update { it.copy(lastScheduledOutcome = stoppedOutcome()) }
            Log.i(TAG, "cloud backup stopped by Android (attempt ${runAttemptCount + 1})")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "cloud backup crashed", e)
            CloudBackupOutcome.Failed(CloudBackupProblem.UNEXPECTED, false, e.javaClass.simpleName)
        } finally {
            CloudJobHealth.running.decrementAndGet()
        }
        Log.i(TAG, "cloud backup: $outcome (attempt ${runAttemptCount + 1})")
        if (outcome is CloudBackupOutcome.Failed && outcome.retryable && runAttemptCount + 1 < MAX_ATTEMPTS) {
            return Result.retry()
        }
        alerts.update(engine.record(outcome, scheduled = !manual))
        // The night is over (or a catch-up stood in for it): the next one at 3:30 again, not 24 hours from now.
        if (!manual) scheduler.reanchor()
        return Result.success(workDataOf(KEY_OUTCOME to outcomeName(outcome)))
    }

    /** « stopped:<reason> »: Android's stop reason (JobParameters.STOP_REASON_*), known from Android 12. */
    private fun stoppedOutcome(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "$OUTCOME_STOPPED:$stopReason" else OUTCOME_STOPPED

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

        /** [CloudBackupState.lastScheduledOutcome] while a night's run is going. */
        const val OUTCOME_STARTED = "started"

        /** … once Android stopped it (time limit, network lost); the next run resumes the upload. */
        const val OUTCOME_STOPPED = "stopped"

        fun outcomeName(outcome: CloudBackupOutcome): String = outcome.logName
    }
}
