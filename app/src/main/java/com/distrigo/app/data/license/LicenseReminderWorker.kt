package com.distrigo.app.data.license

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.distrigo.app.data.auth.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decision 3 (docs/license_l4_checkin.md §12): when the phone must check in within two days and hasn't managed to,
 * a notification says so. Planned by LicenseCheckInScheduler for each license; a newer license replaces it, so
 * when it runs, no check-in has succeeded since. It looks again all the same: the license is what decides.
 */
@HiltWorker
class LicenseReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val license: LicenseManager,
    private val alerts: CheckInAlerts,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        auth.restore()
        val state = license.evaluate()
        val at = LicenseCheckInScheduler.remindAt(auth.state.value, state)
        val held = state.heldLicense
        if (at != null && held != null && System.currentTimeMillis() >= at) alerts.show(held.offlineUntil)
        Result.success()
    }
}
