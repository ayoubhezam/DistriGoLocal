package com.distrigo.app

import com.distrigo.app.data.backup.RestoreStartup
import android.os.Looper
import android.os.Handler
import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.distrigo.app.data.backup.BackupMessages
import com.distrigo.app.data.backup.RestoreInstaller
import com.distrigo.app.data.backup.auto.AutoBackupScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DistriGoApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var autoBackupScheduler: AutoBackupScheduler

    override fun onCreate() {
        // Before Hilt and before anything can open the database or read a photo: a restore scheduled before the
        // restart is installed on its own thread, and the first database open waits for it (RestoreStartup).
        // Nothing starts when none is waiting.
        RestoreStartup.begin(RestoreInstaller.forApp(this)) { result ->
            result?.let { Handler(Looper.getMainLooper()).post { Toast.makeText(this, BackupMessages.of(it), Toast.LENGTH_LONG).show() } }
        }
        super.onCreate()
        // Off the main thread: it reads the settings file and may start WorkManager.
        Thread({
            try {
                autoBackupScheduler.ensureScheduled()
            } catch (e: RuntimeException) {
                // A scheduling problem must not take the app down at launch; the next launch tries again.
                Log.w("DistriGoApplication", "could not schedule the daily backup", e)
            }
        }, "auto-backup-schedule").start()
    }

    /**
     * WorkManager starts on first use rather than at launch (its startup initializer is removed in the
     * manifest), so that it is always after [onCreate] — after a waiting restore is installed and Hilt has
     * injected the factory that builds workers with their dependencies.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
