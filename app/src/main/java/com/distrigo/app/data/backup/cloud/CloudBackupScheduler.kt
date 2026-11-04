package com.distrigo.app.data.backup.cloud

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.distrigo.app.data.backup.auto.AutoBackupScheduler
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The cloud backup's jobs (plan §3.6), apart from the local auto backup's and the license's:
 * - [WORK_NAME]: daily, from the next [RUN_AT] (after the local backup at 3:00), only on Wi-Fi unless « Autoriser les
 *   données mobiles », battery and storage not low; a network failure is retried with exponential backoff;
 * - [NOW_NAME]: « Sauvegarder maintenant », expedited, needing only the network the setting allows.
 */
class CloudBackupScheduler(
    private val workManager: () -> WorkManager,
    private val store: CloudBackupStore,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    /** At app start: a schedule lost with WorkManager's data comes back; an existing one keeps its timing. */
    fun ensureScheduled() {
        val state = store.read()
        if (state.linked && state.enabled) schedule(state, ExistingPeriodicWorkPolicy.KEEP)
    }

    /**
     * After linking, unlinking or a settings change: the job matches the state. UPDATE keeps the timing and takes the
     * new network rule.
     */
    fun apply() {
        val state = store.read()
        if (state.linked && state.enabled) schedule(state, ExistingPeriodicWorkPolicy.UPDATE) else workManager().cancelUniqueWork(WORK_NAME)
        if (!state.linked) workManager().cancelUniqueWork(NOW_NAME)
    }

    /** « Sauvegarder maintenant »: one run now, even when nothing changed. A run already queued is kept. */
    fun backupNow() {
        val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkFor(store.read())).build())
            .setInputData(workDataOf(CloudBackupWorker.KEY_MANUAL to true))
            .addTag(NOW_NAME)
            .build()
        workManager().enqueueUniqueWork(NOW_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun schedule(state: CloudBackupState, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(AutoBackupScheduler.delayUntilNextRun(clock(), zone(), RUN_AT).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkFor(state))
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()
        workManager().enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    companion object {
        const val WORK_NAME = "cloud-backup"
        const val NOW_NAME = "cloud-backup-now"

        /** Half an hour after the local backup, so the two never compete for the database copy. */
        val RUN_AT: LocalTime = LocalTime.of(3, 30)

        /** First retry after 30 min, then 1 h, 2 h…, capped by WorkManager at 5 h. */
        const val BACKOFF_MINUTES = 30L

        fun networkFor(state: CloudBackupState): NetworkType =
            if (state.allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED
    }
}
