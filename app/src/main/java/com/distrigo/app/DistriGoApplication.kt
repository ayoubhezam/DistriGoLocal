package com.distrigo.app

import android.app.Application
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
        // Before Hilt and before anything can open the database or read a photo: a restore scheduled before
        // the restart replaces both here. A no-op when none is waiting.
        val restored = RestoreInstaller.forApp(this).installPending()
        super.onCreate()
        restored?.let { Toast.makeText(this, BackupMessages.of(it), Toast.LENGTH_LONG).show() }
        // Off the main thread: it reads the settings file and may start WorkManager.
        Thread({ autoBackupScheduler.ensureScheduled() }, "auto-backup-schedule").start()
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
