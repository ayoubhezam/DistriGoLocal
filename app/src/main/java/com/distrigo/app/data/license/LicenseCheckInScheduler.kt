package com.distrigo.app.data.license

import android.util.Log
import com.distrigo.app.data.auth.AccountState
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
 * Runs [LicenseCheckInWorker] every 12 hours while a signed-in account holds a license here
 * (docs/license_l4_checkin.md §3.1, §4).
 *
 * The schedule follows the account and the license rather than each place that changes them: an activation or a
 * sign-in starts it, a sign-out, a deletion or a revocation stops it, and a suspension keeps it, so the
 * reactivation is picked up by itself. Android picks the moment within the last [FLEX_HOURS] of each period,
 * with a network, mobile data included.
 */
class LicenseCheckInScheduler(
    private val workManager: () -> WorkManager,
    private val account: StateFlow<AccountState>,
    private val license: StateFlow<LicenseState>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)

    /** Once, as the app starts, after the session and the license are read: follows them for the life of the process. */
    fun start() {
        if (started.compareAndSet(false, true)) {
            scope.launch { follow(account, license, { guarded(::schedule) }, { guarded(::cancel) }) }
        }
    }

    /**
     * After a background check-in: a schedule left from before a sign-out or a revocation that the process did
     * not live to act on ends here.
     */
    fun cancelIfUnwanted() {
        if (wanted(account.value, license.value) == false) guarded(::cancel)
    }

    /** WorkManager trouble (its database, say) must not take the app down; the next start tries again. */
    private fun guarded(action: () -> Unit) {
        try {
            action()
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not change the check-in schedule", e)
        }
    }

    /** Keeps the timing of a schedule already there: called at every start, so one lost with WorkManager's data comes back. */
    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<LicenseCheckInWorker>(PERIOD_HOURS, TimeUnit.HOURS, FLEX_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_AFTER_MINUTES, TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()
        workManager().enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun cancel() {
        workManager().cancelUniqueWork(WORK_NAME)
    }

    companion object {
        private const val TAG = "LicenseCheckInScheduler"
        const val WORK_NAME = "license-check-in"
        const val PERIOD_HOURS = 12L
        const val FLEX_HOURS = 4L

        /** Then doubling at each failure (1 h, 2 h, 4 h), up to WorkManager's 5-hour cap. */
        const val RETRY_AFTER_MINUTES = 30L

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
    }
}
