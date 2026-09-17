package com.distrigo.app.data.backup.auto

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
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
        store.update { it.copy(enabled = enabled) }
        if (enabled) schedule(ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE) else workManager().cancelUniqueWork(WORK_NAME)
    }

    /**
     * Makes sure a schedule exists when backups are on, keeping its timing if it does: called as the app starts,
     * so a schedule lost with WorkManager's own data comes back.
     */
    fun ensureScheduled() {
        if (store.read().enabled) schedule(ExistingPeriodicWorkPolicy.KEEP)
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

        /** From [now] to the next [RUN_AT] strictly after it. */
        fun delayUntilNextRun(now: Instant, zone: ZoneId, runAt: LocalTime = RUN_AT): Duration {
            val local = now.atZone(zone)
            var next = local.toLocalDate().atTime(runAt).atZone(zone)
            if (!next.isAfter(local)) next = local.toLocalDate().plusDays(1).atTime(runAt).atZone(zone)
            return Duration.between(local, next)
        }
    }
}
