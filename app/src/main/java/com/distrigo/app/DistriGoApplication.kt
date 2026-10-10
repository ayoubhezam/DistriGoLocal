package com.distrigo.app

import com.distrigo.app.data.backup.RestoreStartup
import android.os.Looper
import android.os.Handler
import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.distrigo.app.data.backup.BackupMessages
import com.distrigo.app.data.backup.RestoreInstaller
import com.distrigo.app.data.backup.auto.AutoBackupScheduler
import dagger.hilt.android.HiltAndroidApp
import com.distrigo.app.diagnostics.CrashReporter
import com.distrigo.app.diagnostics.DebugStrictMode
import com.distrigo.app.diagnostics.ExitReasons
import com.distrigo.app.diagnostics.MainThreadWatchdog
import javax.inject.Inject

@HiltAndroidApp
class DistriGoApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // Lazy: building it resolves its settings folder on disk, which injection would do on the main thread.
    @Inject
    lateinit var autoBackupScheduler: dagger.Lazy<AutoBackupScheduler>

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate() {
        // First, so a crash in anything after is written up (every build), and StrictMode sees all of
        // startup (debug builds only). See the diagnostics package and Paramètres → Diagnostic.
        CrashReporter.install(this)
        DebugStrictMode.install(this)
        MainThreadWatchdog.install(this)
        // Before any screen: Compose reads it once, when it builds a window's root view. On, Compose UI
        // (1.9, pulled in by a dependency) tells Android "no preferred frame rate" twice on every frame it
        // draws, from Android 15. Samsung's Android 16 logs each such call with the caller's stack trace,
        // which cost the main thread about 0.5 ms a frame on the Galaxy M34 (UI fluidity audit, fix 4).
        // The app asks for no frame rate of its own (no Modifier.preferredFrameRate), so off changes
        // nothing else: the system chooses the refresh rate as it did under Compose 1.7.
        ComposeUiFlags.isAdaptiveRefreshRateEnabled = false
        // Before Hilt and before anything can open the database or read a photo: a restore scheduled before the
        // restart is installed on its own thread, and the first database open waits for it (RestoreStartup).
        // Nothing starts when none is waiting.
        RestoreStartup.begin({ RestoreInstaller.forApp(this) }) { result ->
            result?.let { Handler(Looper.getMainLooper()).post { Toast.makeText(this, BackupMessages.of(it), Toast.LENGTH_LONG).show() } }
        }
        super.onCreate()
        // What the app cannot catch itself — an ANR, a native crash, a kill at the front — read back from
        // Android, on its own thread: an ANR's thread dump is read from a stream.
        Thread({ ExitReasons.collect(this) }, "exit-reasons").start()
        // Off the main thread: it reads the settings file and may start WorkManager.
        Thread({
            try {
                autoBackupScheduler.get().ensureScheduled()
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
