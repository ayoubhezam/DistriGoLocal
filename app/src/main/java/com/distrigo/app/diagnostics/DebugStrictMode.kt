package com.distrigo.app.diagnostics

import android.content.Context
import android.os.Build
import android.os.StrictMode
import android.os.strictmode.Violation
import android.util.Log
import androidx.annotation.RequiresApi
import com.distrigo.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.Executors

/**
 * StrictMode, in debug builds only: disk or database work on the main thread, a cursor or a stream never
 * closed, an activity kept after it was destroyed, a file URI handed to another app…
 *
 * It notes, it does not crash: each violation goes to the log, and — Android 9 and later — each *distinct*
 * one the app's own code set off also becomes a report on the Diagnostic screen, once, however often it
 * recurs. Distinct means its kind and the first line of the app's code on its stack, so the same slow read on
 * every launch is one report.
 */
object DebugStrictMode {

    private const val SEEN_FILE = ".strictmode-seen"
    private const val MAX_SEEN = 300

    fun install(context: Context) {
        if (!BuildConfig.DEBUG) return
        val dir = DiagnosticReports.dir(context)
        val executor = Executors.newSingleThreadExecutor { Thread(it, "strictmode-reports") }

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) penaltyListener(executor) { record(dir, "thread", it) } }
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .detectFileUriExposure()
                .detectContentUriWithoutPermission()
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        detectUnsafeIntentLaunch()
                        detectIncorrectContextUse()
                    }
                }
                .penaltyLog()
                .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) penaltyListener(executor) { record(dir, "vm", it) } }
                .build()
        )
    }

    private val seen = mutableSetOf<String>()
    private var loaded = false

    @RequiresApi(Build.VERSION_CODES.P)
    private fun record(dir: File, policy: String, violation: Violation) {
        try {
            // Only what the app's own code set off: the phone's framework does its own disk work too (Samsung's
            // reads and deletes a preferences file on every resume), which the app cannot change — logged only.
            val frame = violation.stackTrace.firstOrNull { it.className.startsWith("com.distrigo") } ?: return
            val signature = "${violation.javaClass.simpleName}@${frame.className}.${frame.methodName}:${frame.lineNumber}"
            val seenFile = File(dir, SEEN_FILE)
            synchronized(seen) {
                if (!loaded) {
                    if (seenFile.isFile) seen += seenFile.readLines()
                    loaded = true
                }
                if (!seen.add(signature) || seen.size > MAX_SEEN) return
                dir.mkdirs()
                seenFile.appendText(signature + "\n")
            }
            val where = "${frame.className.substringAfterLast('.')}.${frame.methodName}:${frame.lineNumber}"
            val trace = StringWriter().also { violation.printStackTrace(PrintWriter(it)) }
            DiagnosticReports.write(
                dir, ReportKind.STRICT_MODE, System.currentTimeMillis(),
                "${violation.javaClass.simpleName} — $where",
                buildString {
                    appendLine("Règle : $policy")
                    append(CrashReporter.header())
                    append(CrashReporter.screens())
                    appendLine()
                    append(trace)
                },
            )
        } catch (e: Exception) {
            Log.w("DebugStrictMode", "could not record a violation", e)
        }
    }
}
