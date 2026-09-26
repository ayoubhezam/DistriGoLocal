package com.distrigo.app.diagnostics

import android.content.Context
import android.os.Build
import android.os.Debug
import com.distrigo.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Writes a report for an uncaught exception, then hands it to Android's own handler, which logs it, shows
 * the system dialog and ends the process as before. Every build: a crash on a rep's phone is the one we
 * most need to read.
 *
 * An out-of-memory crash leaves no heap to write with, so a small reserve is held from the start and let go
 * first. The report records the heap as it stood, which a stack trace alone never shows: the allocation
 * that fails is rarely the one that filled the heap.
 */
object CrashReporter {

    @Volatile private var reserve: ByteArray? = null
    @Volatile private var dir: File? = null
    private lateinit var header: String

    /** First thing in `Application.onCreate`, so a crash in anything after it is caught. */
    fun install(context: Context) {
        val app = context.applicationContext
        header = header()
        reserve = ByteArray(RESERVE_BYTES)
        // The folder is found on disk, so off the main thread; a crash before that finds it itself.
        Thread({ dir = DiagnosticReports.dir(app) }, "crash-reports-dir").start()
        val android = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            reserve = null
            try {
                val into = dir ?: DiagnosticReports.dir(app)
                DiagnosticReports.write(into, ReportKind.CRASH, System.currentTimeMillis(), title(error), body(thread, error))
            } catch (_: Throwable) {
                // Nothing more can be done here; Android's handler still logs the crash.
            }
            android?.uncaughtException(thread, error)
        }
    }

    /** The app and the phone, the same in every report. */
    fun header(): String = buildString {
        appendLine("App : DistriGo ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
        appendLine("Téléphone : ${Build.MANUFACTURER} ${Build.MODEL} — Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    }

    /** The heap as it stands: what an out-of-memory crash needs explained. */
    fun memory(): String {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        return "Mémoire : Java ${mb(used)} utilisés sur ${mb(runtime.maxMemory())} (réservés ${mb(runtime.totalMemory())}), " +
            "native ${mb(Debug.getNativeHeapAllocatedSize())}"
    }

    /** The last screens shown, for a report to say where the app was. */
    fun screens(): String {
        val lines = Breadcrumbs.snapshot()
        return if (lines.isEmpty()) "Écrans : aucun enregistré\n"
               else "Derniers écrans (le plus récent en bas) :\n" + lines.joinToString("") { "  $it\n" }
    }

    private fun title(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.last()
        return "${error.javaClass.simpleName}: ${error.message.orEmpty().take(120)}" +
            if (root !== error) " — cause : ${root.javaClass.simpleName}" else ""
    }

    private fun body(thread: Thread, error: Throwable): String = buildString {
        appendLine("Quand : ${Instant.now()}")
        append(header)
        appendLine("Fil : ${thread.name}")
        appendLine(memory())
        append(screens())
        appendLine()
        val trace = StringWriter()
        error.printStackTrace(PrintWriter(trace))
        append(trace)
    }

    private fun mb(bytes: Long) = "${bytes / (1024 * 1024)} Mo"

    private const val RESERVE_BYTES = 512 * 1024
}
