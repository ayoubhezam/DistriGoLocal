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
 * Decision 6 (docs/license_l5_enforcement.md §7): on the first day of grace, one notification beside the red banner.
 * Planned by LicenseCheckInScheduler at the end of each license's subscription; it reads the license again when it
 * runs, since a renewal in between means there is no grace to announce.
 */
@HiltWorker
class LicenseGraceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val license: LicenseManager,
    private val alerts: CheckInAlerts,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        auth.restore()
        val state = license.evaluate()
        if (state is LicenseState.Grace) alerts.showGrace(state.license.validTo, state.license.graceUntil)
        Result.success()
    }
}
