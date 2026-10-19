package com.distrigo.app.data.license

import android.util.Log
import com.distrigo.app.data.auth.AccountState
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The license's work in WorkManager (docs/license_l4_checkin.md §3.1, §4):
 *
 *  - [LicenseCheckInWorker] every 12 hours while a signed-in account holds a license here. Android picks the moment
 *    within the last [FLEX_HOURS] of each period, with a network, mobile data included.
 *  - The same worker once, as soon as there is a network, when the app comes to the front and a check-in is due
 *    ([checkInNow], LicenseInFront).
 *  - [LicenseReminderWorker] when the phone must check in within [REMIND_BEFORE] and hasn't managed to: no network
 *    needed, since the phones that need the reminder are those without one.
 *  - [LicenseGraceWorker] when the subscription ends ([graceNoticeAt]): one notification on the first day of grace
 *    (docs/license_l5_enforcement.md, decision 6).
 *
 * The schedule follows the account and the license rather than each place that changes them: an activation or a
 * sign-in starts it, a sign-out, a deletion or a revocation stops it, and a suspension keeps it, so the
 * reactivation is picked up by itself. The reminder follows the license held: each new one plans it again.
 */
class LicenseCheckInScheduler(
    private val workManager: () -> WorkManager,
    private val account: StateFlow<AccountState>,
    private val license: StateFlow<LicenseState>,
    private val log: CheckInLog,
    private val alerts: CheckInAlerts,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)

    /** Once, as the app starts, after the session and the license are read: follows them for the life of the process. */
    fun start() {
        if (started.compareAndSet(false, true)) {
            scope.launch { follow(account, license, { guarded(::schedule) }, { guarded(::cancel) }) }
            scope.launch { followReminder(account, license, { at -> guarded { remind(at) } }, { guarded(::dropReminder) }) }
            scope.launch { followReminder(account, license, { at -> guarded { noticeGrace(at) } }, { guarded(::dropGraceNotice) }, ::graceNoticeAt) }
        }
    }

    /**
     * After a background check-in: a schedule left from before a sign-out or a revocation that the process did
     * not live to act on ends here.
     */
    fun cancelIfUnwanted() {
        if (wanted(account.value, license.value) == false) guarded(::cancel)
    }

    /** One check-in as soon as there is a network; one already waiting is kept, not doubled. */
    fun checkInNow() = guarded {
        val request = OneTimeWorkRequestBuilder<LicenseCheckInWorker>()
            .setConstraints(network())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_AFTER_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf(LicenseCheckInWorker.SOURCE to CheckInSource.FOREGROUND.name))
            .addTag(NOW_NAME)
            .build()
        workManager().enqueueUniqueWork(NOW_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** Keeps the timing of a schedule already there: called at every start, so one lost with WorkManager's data comes back. */
    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<LicenseCheckInWorker>(PERIOD_HOURS, TimeUnit.HOURS, FLEX_HOURS, TimeUnit.HOURS)
            .setConstraints(network())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_AFTER_MINUTES, TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()
        workManager().enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        log.markScheduled(clock())
    }

    private fun cancel() {
        workManager().cancelUniqueWork(WORK_NAME)
        workManager().cancelUniqueWork(NOW_NAME)
        log.markUnscheduled()
    }

    /**
     * The reminder for the license held now, at [at]. Still to come, any warning shown is about an older license
     * and goes; already due, the worker runs at once and shows or keeps it.
     */
    private fun remind(at: Long) {
        val delay = at - clock()
        if (delay > 0) alerts.clear()
        val request = OneTimeWorkRequestBuilder<LicenseReminderWorker>()
            .setInitialDelay(delay.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .addTag(REMINDER_NAME)
            .build()
        workManager().enqueueUniqueWork(REMINDER_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun dropReminder() {
        workManager().cancelUniqueWork(REMINDER_NAME)
        alerts.clear()
    }

    /** The grace notice for the license held now, at its subscription's end; already past, the worker looks at once. */
    private fun noticeGrace(at: Long) {
        val delay = at - clock()
        // Still to come: a notice shown is about an earlier end, renewed since
        if (delay > 0) alerts.clearGrace()
        val request = OneTimeWorkRequestBuilder<LicenseGraceWorker>()
            .setInitialDelay(delay.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .addTag(GRACE_NAME)
            .build()
        workManager().enqueueUniqueWork(GRACE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun dropGraceNotice() {
        workManager().cancelUniqueWork(GRACE_NAME)
        alerts.clearGrace()
    }

    private fun network() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** WorkManager trouble (its database, say) must not take the app down; the next start tries again. */
    private fun guarded(action: () -> Unit) {
        try {
            action()
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not change the license's work", e)
        }
    }

    /** A reminder's time, or none; a separate type so that "none" and "not read yet" differ. */
    private data class Reminder(val at: Long?)

    companion object {
        private const val TAG = "LicenseCheckInScheduler"
        const val WORK_NAME = "license-check-in"
        const val NOW_NAME = "license-check-in-now"
        const val REMINDER_NAME = "license-check-in-reminder"
        const val GRACE_NAME = "license-grace-notice"
        const val PERIOD_HOURS = 12L
        const val FLEX_HOURS = 4L

        /** Then doubling at each failure (1 h, 2 h, 4 h), up to WorkManager's 5-hour cap. */
        const val RETRY_AFTER_MINUTES = 30L

        /** Decision 3: the notification comes when the phone must check in within two days. */
        const val REMIND_BEFORE = 2 * 24 * 3_600_000L

        /**
         * Whether background check-ins should run; null until the session and the license are read. A license
         * that can't be read now (the Keystore) keeps them: only a phone known to hold none, or revoked, stops.
         */
        fun wanted(account: AccountState, license: LicenseState): Boolean? = when {
            account == AccountState.Loading || license == LicenseState.Unchecked -> null
            account !is AccountState.SignedIn -> false
            else -> license !is LicenseState.NoLicense && license !is LicenseState.Revoked
        }

        /**
         * When to remind this phone to check in: [REMIND_BEFORE] before its offline window ends, or half-way through
         * a shorter window (a reduced phone's one day), so a phone checking in as it should is never told to. None
         * when signed out (a check-in can't help) or suspended (only DistriGo can).
         */
        fun remindAt(account: AccountState, license: LicenseState): Long? {
            if (account !is AccountState.SignedIn || license is LicenseState.Suspended) return null
            val held = license.heldLicense ?: return null
            return held.offlineUntil - minOf(REMIND_BEFORE, (held.offlineUntil - held.issuedAt) / 2)
        }

        /**
         * When to announce the grace days (decision 6): the end of the subscription held, a second after. None when
         * signed out, suspended, or for a license past its grace already: there is no grace left to announce.
         */
        fun graceNoticeAt(account: AccountState, license: LicenseState): Long? {
            if (account !is AccountState.SignedIn || license is LicenseState.Suspended || license is LicenseState.Expired) return null
            val held = license.heldLicense ?: return null
            if (held.graceUntil <= held.validTo) return null
            return held.validTo + 1_000
        }

        /**
         * Schedules whenever it becomes wanted, the first time included; cancels whenever it stops being wanted,
         * but not at the first reading: a phone without a license doesn't start WorkManager at each launch for a
         * schedule it never had.
         */
        internal suspend fun follow(
            account: Flow<AccountState>,
            license: Flow<LicenseState>,
            schedule: () -> Unit,
            cancel: () -> Unit,
        ) {
            var first = true
            combine(account, license, ::wanted).filterNotNull().distinctUntilChanged().collect { wanted ->
                if (wanted) schedule() else if (!first) cancel()
                first = false
            }
        }

        /**
         * The same for the reminder ([timeOf] is [remindAt]) and the grace notice ([graceNoticeAt]): planned again for each
         * license, dropped when none applies, not at the first reading.
         */
        internal suspend fun followReminder(
            account: Flow<AccountState>,
            license: Flow<LicenseState>,
            remind: (Long) -> Unit,
            drop: () -> Unit,
            timeOf: (AccountState, LicenseState) -> Long? = ::remindAt,
        ) {
            var first = true
            combine(account, license) { a, l -> if (a == AccountState.Loading || l == LicenseState.Unchecked) null else Reminder(timeOf(a, l)) }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { reminder ->
                    val at = reminder.at
                    if (at != null) remind(at) else if (!first) drop()
                    first = false
                }
        }
    }
}
