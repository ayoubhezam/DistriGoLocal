package com.distrigo.app.data.backup.cloud

import android.util.Log
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
    /** Null: no check of Android's job list (KEEP alone). */
    private val probe: CloudJobHealth.Probe? = null,
    /** Between the two looks at a job that seems lost, so a job being planned or started isn't taken for one. */
    private val pause: (Long) -> Unit = Thread::sleep,
    private val warn: (String) -> Unit = { Log.w(TAG, it) },
) {

    /**
     * At app start and from the license jobs: a schedule lost with WorkManager's data comes back, an existing one keeps
     * its timing, and one WorkManager still has but Android lost (see [CloudJobHealth]) is replaced. Returns what it
     * found. Blocking: not on the main thread.
     */
    fun ensureScheduled(): CloudJobHealth.Verdict {
        val state = store.read()
        if (!state.linked || !state.enabled) return CloudJobHealth.Verdict.Healthy
        val probe = probe ?: return CloudJobHealth.Verdict.Healthy.also { schedule(state, ExistingPeriodicWorkPolicy.KEEP) }
        var verdict = look(probe)
        if (verdict is CloudJobHealth.Verdict.Lost) {
            pause(RECHECK_MILLIS)
            verdict = look(probe)
        }
        when (verdict) {
            CloudJobHealth.Verdict.Healthy -> if (CloudJobHealth.slipped(probe.works(WORK_NAME), nextAnchor())) {
                warn("the daily cloud job slipped away from $RUN_AT: setting it back")
                reanchor()
            }
            CloudJobHealth.Verdict.Missing -> schedule(state, ExistingPeriodicWorkPolicy.KEEP)
            is CloudJobHealth.Verdict.Lost -> {
                warn("the daily cloud job was lost by Android (running=${verdict.wasRunning}): planning it again")
                schedule(state, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                if (verdict.wasRunning) {
                    // That night never finished: say so, and run it again now rather than wait for the next one (the
                    // daily job keeps its 3:30 slot).
                    store.update { it.copy(lastScheduledOutcome = OUTCOME_INTERRUPTED) }
                    catchUp(state)
                }
            }
        }
        return verdict
    }

    /**
     * After a night's run: the next one back at [RUN_AT]. A 24-hour job counts from when it last ran, so each late
     * night (Doze, a stop, a catch-up at 7:23) pushed the slot later; this pins the next run to the next 3:30 again.
     * Never fails the run that calls it.
     */
    fun reanchor() {
        try {
            val state = store.read()
            if (state.linked && state.enabled) {
                workManager().enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request(state, nextRunAt = nextAnchor()))
            }
        } catch (e: Exception) {
            warn("could not set the next cloud backup back to $RUN_AT: $e")
        }
    }

    /** The next [RUN_AT], in epoch millis. */
    private fun nextAnchor(): Long = clock().plus(AutoBackupScheduler.delayUntilNextRun(clock(), zone(), RUN_AT)).toEpochMilli()

    /** [ensureScheduled] from another job: a problem here must never fail that job. */
    fun checkQuietly() {
        try {
            ensureScheduled()
        } catch (e: Exception) {
            warn("could not check the cloud backup's job: $e")
        }
    }

    private fun look(probe: CloudJobHealth.Probe) =
        CloudJobHealth.verdict(probe.works(WORK_NAME), probe.plannedIds(), probe.runningHere())

    /**
     * After linking, unlinking or a settings change: the job matches the state. UPDATE keeps the timing and takes the
     * new network rule.
     */
    fun apply() {
        val state = store.read()
        if (state.linked && state.enabled) schedule(state, ExistingPeriodicWorkPolicy.UPDATE) else workManager().cancelUniqueWork(WORK_NAME)
        if (!state.linked) workManager().cancelUniqueWork(NOW_NAME)
        if (!state.linked || !state.enabled) workManager().cancelUniqueWork(CATCH_UP_NAME)
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

    /** A night's run again, under the daily job's conditions; counted as a scheduled run. */
    private fun catchUp(state: CloudBackupState) {
        val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
            .setConstraints(constraints(state))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .addTag(CATCH_UP_NAME)
            .build()
        workManager().enqueueUniqueWork(CATCH_UP_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun constraints(state: CloudBackupState) = Constraints.Builder()
        .setRequiredNetworkType(networkFor(state))
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()

    private fun schedule(state: CloudBackupState, policy: ExistingPeriodicWorkPolicy) {
        workManager().enqueueUniquePeriodicWork(WORK_NAME, policy, request(state, nextRunAt = null))
    }

    private fun request(state: CloudBackupState, nextRunAt: Long?) =
        PeriodicWorkRequestBuilder<CloudBackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(AutoBackupScheduler.delayUntilNextRun(clock(), zone(), RUN_AT).toMillis(), TimeUnit.MILLISECONDS)
            .apply { if (nextRunAt != null) setNextScheduleTimeOverride(nextRunAt) }
            .setConstraints(constraints(state))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()

    companion object {
        private const val TAG = "CloudBackupScheduler"
        const val WORK_NAME = "cloud-backup"

        /** [CloudBackupState.lastScheduledOutcome] of a night the phone cut short before it could say so. */
        const val OUTCOME_INTERRUPTED = "interrupted"

        const val RECHECK_MILLIS = 3_000L
        const val NOW_NAME = "cloud-backup-now"

        /** A night cut short, run again once the job is repaired. */
        const val CATCH_UP_NAME = "cloud-backup-catch-up"

        /** Half an hour after the local backup, so the two never compete for the database copy. */
        val RUN_AT: LocalTime = LocalTime.of(3, 30)

        /** First retry after 30 min, then 1 h, 2 h…, capped by WorkManager at 5 h. */
        const val BACKOFF_MINUTES = 30L

        fun networkFor(state: CloudBackupState): NetworkType =
            if (state.allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED
    }
}
