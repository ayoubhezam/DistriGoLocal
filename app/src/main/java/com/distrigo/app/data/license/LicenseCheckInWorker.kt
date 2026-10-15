package com.distrigo.app.data.license

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The background check-in (docs/license_l4_checkin.md §3), as LicenseCheckInScheduler plans it. A failure of the
 * network or the server asks WorkManager to try again (from 30 minutes, doubling); any other answer is final until
 * the next period. It holds no wake lock of its own: Android runs it in a window it has already opened.
 */
@HiltWorker
class LicenseCheckInWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val checkIns: LicenseCheckIn,
    private val scheduler: LicenseCheckInScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = checkIns.run(CheckInSource.BACKGROUND)
        Log.i(TAG, "background check-in: $result")
        scheduler.cancelIfUnwanted()
        return if (result is CheckIn.Failed && result.retry) Result.retry() else Result.success()
    }

    private companion object {
        const val TAG = "LicenseCheckInWorker"
    }
}
