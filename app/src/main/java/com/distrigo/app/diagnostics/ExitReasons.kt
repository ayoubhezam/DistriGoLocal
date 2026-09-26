package com.distrigo.app.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.time.Instant

/**
 * What no handler in the app can catch — a freeze Android ended (ANR), a crash in native code, the process
 * killed at the front for want of memory — read back from Android at the next start (Android 11 and later).
 *
 * Each exit is reported once: the time of the newest one read is kept beside the reports. The first start
 * with this in place reads back whatever Android still keeps, so earlier trouble is not lost either.
 */
object ExitReasons {

    private const val TAG = "ExitReasons"
    private const val SEEN_FILE = ".exits-seen"
    private const val MAX_TRACE_BYTES = 256 * 1024

    /** Off the main thread: an ANR's thread dump is read from a stream. */
    fun collect(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            collectSince(context)
        } catch (e: Exception) {
            Log.w(TAG, "could not read the exit reasons", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun collectSince(context: Context) {
        val dir = DiagnosticReports.dir(context).apply { mkdirs() }
        val seenFile = File(dir, SEEN_FILE)
        val seen = seenFile.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() ?: 0L

        val manager = context.getSystemService(ActivityManager::class.java)
        val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 0)
            .filter { it.timestamp > seen }
            .sortedBy { it.timestamp }
        if (exits.isEmpty()) return

        val crashes = DiagnosticReports.list(dir).filter { it.kind == ReportKind.CRASH }.map { it.at.toEpochMilli() }
        for (exit in exits) {
            val kind = kindOf(exit, crashes) ?: continue
            DiagnosticReports.write(dir, kind, exit.timestamp, title(exit), body(exit))
        }
        seenFile.writeText(exits.maxOf { it.timestamp }.toString())
    }

    /** The exits worth a report, or null: a user closing the app, an update, a background kill are not trouble. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun kindOf(exit: ApplicationExitInfo, crashReports: List<Long>): ReportKind? {
        val atTheFront = exit.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        return when (exit.reason) {
            ApplicationExitInfo.REASON_ANR -> ReportKind.ANR
            ApplicationExitInfo.REASON_CRASH_NATIVE -> ReportKind.NATIVE
            // A Java crash the app's own handler already wrote up is not reported twice; one it did not —
            // from before it was installed, or when it could not write — is, with what Android kept.
            ApplicationExitInfo.REASON_CRASH ->
                if (crashReports.any { kotlin.math.abs(it - exit.timestamp) < 15_000 }) null else ReportKind.EXIT
            ApplicationExitInfo.REASON_LOW_MEMORY,
            ApplicationExitInfo.REASON_SIGNALED -> if (atTheFront) ReportKind.EXIT else null
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> ReportKind.EXIT
            else -> null
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun title(exit: ApplicationExitInfo): String = when (exit.reason) {
        ApplicationExitInfo.REASON_ANR -> "L'application ne répondait plus (ANR)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "Plantage dans du code natif"
        ApplicationExitInfo.REASON_CRASH -> "Plantage (Java) sans rapport de l'application"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "Arrêtée par Android : mémoire insuffisante, au premier plan"
        ApplicationExitInfo.REASON_SIGNALED -> "Arrêtée par un signal (${exit.status}), au premier plan"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Arrêtée par Android : ressources excessives"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Échec au démarrage"
        else -> "Arrêt (${exit.reason})"
    } + exit.description?.takeIf { it.isNotBlank() }?.let { " — ${it.take(120)}" }.orEmpty()

    @RequiresApi(Build.VERSION_CODES.R)
    private fun body(exit: ApplicationExitInfo): String = buildString {
        appendLine("Quand : ${Instant.ofEpochMilli(exit.timestamp)}")
        append(CrashReporter.header())
        appendLine("Processus : ${exit.processName} (pid ${exit.pid})")
        appendLine("Raison Android : ${exit.reason}, statut ${exit.status}, importance ${exit.importance}")
        exit.description?.takeIf { it.isNotBlank() }?.let { appendLine("Description : $it") }
        appendLine("Mémoire au moment de l'arrêt : PSS ${exit.pss / 1024} Mo, RSS ${exit.rss / 1024} Mo")
        // Native crashes come as a binary tombstone; only an ANR's dump is text worth keeping.
        if (exit.reason == ApplicationExitInfo.REASON_ANR) {
            val trace = exit.traceInputStream?.use { stream ->
                val bytes = stream.readNBytesCompat(MAX_TRACE_BYTES)
                String(bytes)
            }
            appendLine()
            if (trace.isNullOrEmpty()) appendLine("Pas de vidage des fils conservé par Android.")
            else {
                appendLine("Vidage des fils au moment du blocage (le fil « main » est celui de l'écran) :")
                append(trace)
            }
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < max) {
            val read = read(buffer, 0, minOf(buffer.size, max - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
