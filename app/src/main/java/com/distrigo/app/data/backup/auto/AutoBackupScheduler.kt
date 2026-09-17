package com.distrigo.app.data.backup.auto

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Runs [AutoBackupWorker] once a day while automatic backups are on.
 *
 * The first run is at the next [RUN_AT] local time, when the phone is least likely to be in use; after that
 * WorkManager repeats it every 24 hours, only while the battery and storage are not low. Android decides the
 * exact moment — a phone asleep in Doze runs it at its next maintenance window — so a run can come hours late,
 * but it comes.
 */
class AutoBackupScheduler(
    private val workManager: () -> WorkManager,
    private val store: AutoBackupStore,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    /** Turns automatic backups on or off. Turning them on starts a fresh schedule from the next [RUN_AT]. */
    fun setEnabled(enabled: Boolean) {
        store.update { if (enabled) it.copy(enabled = true, enabledAt = clock()) else it.copy(enabled = false) }
        if (enabled) schedule(ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE) else workManager().cancelUniqueWork(WORK_NAME)
    }

    /**
     * Makes sure a schedule exists when backups are on, keeping its timing if it does: called as the app starts,
     * so a schedule lost with WorkManager's own data comes back.
     */
    fun ensureScheduled() {
        if (store.read().enabled) schedule(ExistingPeriodicWorkPolicy.KEEP)
    }

    /**
     * When WorkManager plans the next run, or null if backups are off or it has none. Blocks on WorkManager's
     * database: call it off the main thread. Not called while off, so WorkManager is not started for nothing.
     */
    fun nextRunAt(): Instant? {
        if (!store.read().enabled) return null
        val next = workManager().getWorkInfosForUniqueWork(WORK_NAME).get()
            .filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            .map { it.nextScheduleTimeMillis }
            .filter { it in 1 until Long.MAX_VALUE }
            .minOrNull()
        return next?.let(Instant::ofEpochMilli)
    }

    private fun schedule(policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntilNextRun(clock(), zone()).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .addTag(WORK_NAME)
            .build()
        workManager().enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    companion object {
        const val WORK_NAME = "auto-backup"

        /** When the daily backup is due, in the phone's time. */
        val RUN_AT: LocalTime = LocalTime.of(3, 0)

        /**
         * No run for this long while on means the schedule is being held back — usually by the phone's battery
         * settings — rather than just waiting for its next night: the first run comes within a day of turning
         * backups on, and each run within a day of the last, with hours to spare for Doze.
         */
        val OVERDUE_AFTER: Duration = Duration.ofHours(36)

        /**
         * Whether backups are on but the daily job has not run for longer than [OVERDUE_AFTER]. Manual backups do
         * not count: pressing Sauvegarder maintenant every day would otherwise hide a schedule that never runs.
         */
        fun isOverdue(state: AutoBackupState, now: Instant): Boolean {
            if (!state.enabled) return false
            val since = listOfNotNull(state.lastScheduledRunAt, state.enabledAt).maxOrNull() ?: return false
            return Duration.between(since, now) > OVERDUE_AFTER
        }

        /** From [now] to the next [RUN_AT] strictly after it. */
        fun delayUntilNextRun(now: Instant, zone: ZoneId, runAt: LocalTime = RUN_AT): Duration {
            val local = now.atZone(zone)
            var next = local.toLocalDate().atTime(runAt).atZone(zone)
            if (!next.isAfter(local)) next = local.toLocalDate().plusDays(1).atTime(runAt).atZone(zone)
            return Duration.between(local, next)
        }
    }
}
