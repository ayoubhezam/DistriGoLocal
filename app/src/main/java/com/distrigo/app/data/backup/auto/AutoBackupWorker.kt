package com.distrigo.app.data.backup.auto

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The daily automatic backup.
 *
 * It always reports success to WorkManager, even when the backup failed: a failure is recorded, counted and,
 * from the second in a row, notified, and tomorrow's run tries again. Asking WorkManager to retry would run it
 * again within minutes, against a folder or a disk that has usually not changed, and count each retry as
 * another failure.
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: AutoBackupRunner,
    private val alerts: AutoBackupAlerts,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!runner.store.read().enabled) return@withContext Result.success()
        try {
            val outcome = runner.run()
            Log.i(TAG, "automatic backup: $outcome")
        } catch (e: Exception) {
            // Already recorded by the runner as an unexpected problem.
            Log.e(TAG, "automatic backup crashed", e)
        }
        alerts.update(runner.store.read())
        Result.success()
    }

    private companion object {
        const val TAG = "AutoBackupWorker"
    }
}
