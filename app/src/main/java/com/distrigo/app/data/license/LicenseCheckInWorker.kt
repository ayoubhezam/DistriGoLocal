package com.distrigo.app.data.license

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * A check-in in WorkManager (docs/license_l4_checkin.md §3), as LicenseCheckInScheduler plans it: the 12-hour job,
 * or the one-off one when the app came to the front ([SOURCE] says which). A failure of the network or the server
 * asks WorkManager to try again (from 30 minutes, doubling); any other answer is final. It holds no wake lock of
 * its own: Android runs it in a window it has already opened.
 */
@HiltWorker
class LicenseCheckInWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val checkIns: LicenseCheckIn,
    private val scheduler: LicenseCheckInScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val source = inputData.getString(SOURCE)?.let { name -> CheckInSource.entries.firstOrNull { it.name == name } }
            ?: CheckInSource.BACKGROUND
        val result = checkIns.run(source)
        Log.i(TAG, "check-in ($source): $result")
        scheduler.cancelIfUnwanted()
        return if (result is CheckIn.Failed && result.retry) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "LicenseCheckInWorker"

        /** Input: the CheckInSource's name; the 12-hour job has none, and is the background. */
        const val SOURCE = "source"
    }
}
