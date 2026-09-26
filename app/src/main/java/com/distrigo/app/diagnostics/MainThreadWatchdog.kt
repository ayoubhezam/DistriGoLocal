package com.distrigo.app.diagnostics

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Notices the screen's thread stuck for [THRESHOLD_MS] or more, and writes down what it is stuck on — while
 * it still is, which is the moment that shows the culprit.
 *
 * Android's own record of an ANR ([ExitReasons]) exists only when the app was killed for it; a rep who taps
 * "Attendre" and gets the app back leaves no trace there, and Android 8 to 10 keep none at all. This covers
 * both. Every build; it only samples while one of the app's screens is visible, once a second, and not while a
 * debugger holds the app paused.
 */
object MainThreadWatchdog {

    private const val TICK_MS = 1_000L
    private const val POLL_MS = 200L
    private const val THRESHOLD_MS = 5_000L
    private const val OTHER_FRAMES = 25

    private val visible = AtomicInteger(0)

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { visible.incrementAndGet() }
            override fun onActivityStopped(activity: Activity) { visible.decrementAndGet() }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        val main = Handler(Looper.getMainLooper())
        // The reports folder is found on the watchdog's own thread: it touches the disk.
        Thread({ watch(main, DiagnosticReports.dir(app)) }, "main-watchdog").apply { isDaemon = true }.start()
    }

    private fun watch(main: Handler, dir: File) {
        while (true) {
            try {
                Thread.sleep(TICK_MS)
                if (visible.get() <= 0 || Debug.isDebuggerConnected()) continue
                val sent = SystemClock.uptimeMillis()
                val answered = AtomicBoolean(false)
                main.post { answered.set(true) }
                var report: File? = null
                while (!answered.get()) {
                    Thread.sleep(POLL_MS)
                    val blocked = SystemClock.uptimeMillis() - sent
                    if (report == null && blocked >= THRESHOLD_MS && !Debug.isDebuggerConnected()) {
                        report = write(dir, blocked)
                    }
                }
                report?.appendText("\nDébloqué après ${(SystemClock.uptimeMillis() - sent) / 1000.0} s.\n")
            } catch (_: InterruptedException) {
                return
            } catch (_: Exception) {
                // A report that could not be written is not worth stopping the watch for.
            }
        }
    }

    private fun write(dir: File, blockedMs: Long): File {
        val mainThread = Looper.getMainLooper().thread
        val stuckAt = mainThread.stackTrace
        val culprit = stuckAt.firstOrNull { it.className.startsWith("com.distrigo") }
        val body = buildString {
            appendLine("Quand : ${Instant.now()}")
            append(CrashReporter.header())
            appendLine("L'écran ne répond plus depuis ${blockedMs / 1000.0} s (relevé pendant le blocage).")
            appendLine(CrashReporter.memory())
            append(CrashReporter.screens())
            appendLine()
            appendLine("Fil « main » (celui de l'écran), là où il est bloqué :")
            stuckAt.forEach { appendLine("\tat $it") }
            // What else the app is doing: the main thread is often waiting for one of these — a database
            // transaction, a lock — rather than working itself.
            val others = Thread.getAllStackTraces()
                .filterKeys { it !== mainThread }
                .filterValues { frames -> frames.any { it.className.startsWith("com.distrigo") } }
            if (others.isNotEmpty()) {
                appendLine()
                appendLine("Autres fils de l'application occupés :")
                for ((thread, frames) in others) {
                    appendLine("« ${thread.name} » (${thread.state})")
                    frames.take(OTHER_FRAMES).forEach { appendLine("\tat $it") }
                }
            }
        }
        val title = "Écran bloqué ${blockedMs / 1000} s — " +
            (culprit?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                ?: stuckAt.firstOrNull()?.let { "${it.className.substringAfterLast('.')}.${it.methodName}" } ?: "?")
        return DiagnosticReports.write(dir, ReportKind.ANR, System.currentTimeMillis(), title, body)
    }
}
