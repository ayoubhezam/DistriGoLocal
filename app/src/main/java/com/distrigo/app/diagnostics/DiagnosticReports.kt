package com.distrigo.app.diagnostics

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/** What a report is about. [code] is in its file name, so a report's kind is known without reading it. */
enum class ReportKind(val code: String, val label: String) {
    CRASH("crash", "Plantage"),
    ANR("anr", "Application figée (ANR)"),
    NATIVE("native", "Plantage natif"),
    EXIT("exit", "Arrêt anormal"),
    STRICT_MODE("strictmode", "StrictMode"),
}

/** One report on disk: its kind, when, and its first line. */
data class DiagnosticReport(val file: File, val kind: ReportKind, val at: Instant, val title: String)

/**
 * The diagnostic reports, one text file each in `filesDir/diagnostics` — on the phone, never sent anywhere:
 * the Diagnostic screen shows them and shares one when asked.
 *
 * A file is named `<epoch millis>-<kind>.txt`; its first line is its title. Kept within bounds per kind of
 * trouble, so a flood of StrictMode notes can never push out the crash that matters.
 */
object DiagnosticReports {

    const val DIR = "diagnostics"
    private const val MAX_PROBLEMS = 30
    private const val MAX_STRICT_MODE = 40

    fun dir(context: Context): File = File(context.filesDir, DIR)

    /**
     * Writes a report and trims the oldest beyond the bounds. Plain streams and nothing else, as it runs
     * from the crash handler, possibly with the heap exhausted.
     */
    fun write(dir: File, kind: ReportKind, atMillis: Long, title: String, body: String): File {
        dir.mkdirs()
        var file = File(dir, "$atMillis-${kind.code}.txt")
        var n = 1
        while (file.exists()) file = File(dir, "$atMillis-${kind.code}-${n++}.txt")
        FileOutputStream(file).use { out ->
            out.write(title.replace('\n', ' ').toByteArray())
            out.write('\n'.code)
            out.write(body.toByteArray())
            out.fd.sync()
        }
        prune(dir)
        return file
    }

    /** Every report, newest first. */
    fun list(dir: File): List<DiagnosticReport> =
        (dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") } ?: emptyArray())
            .mapNotNull { parse(it) }
            .sortedByDescending { it.at }

    fun read(report: DiagnosticReport): String = report.file.readText()

    fun delete(report: DiagnosticReport) {
        report.file.delete()
    }

    fun clear(dir: File) {
        dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }?.forEach { it.delete() }
    }

    private fun parse(file: File): DiagnosticReport? {
        val parts = file.nameWithoutExtension.split('-')
        val millis = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val kind = ReportKind.entries.firstOrNull { it.code == parts.getOrNull(1) } ?: return null
        val title = file.bufferedReader().use { it.readLine() }.orEmpty()
        return DiagnosticReport(file, kind, Instant.ofEpochMilli(millis), title)
    }

    private fun prune(dir: File) {
        val all = list(dir)
        val (strict, problems) = all.partition { it.kind == ReportKind.STRICT_MODE }
        strict.drop(MAX_STRICT_MODE).forEach { it.file.delete() }
        problems.drop(MAX_PROBLEMS).forEach { it.file.delete() }
    }
}
