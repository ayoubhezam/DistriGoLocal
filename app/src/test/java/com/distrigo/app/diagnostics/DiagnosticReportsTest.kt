package com.distrigo.app.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The reports' files, checked on a temporary folder: what the Diagnostic screen lists, in what order, and
 * that trimming the oldest never lets StrictMode's notes push out a crash.
 */
class DiagnosticReportsTest {

    private val dir: File = Files.createTempDirectory("diagnostics").toFile()

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `a report is listed with its kind, time and first line, newest first`() {
        DiagnosticReports.write(dir, ReportKind.CRASH, 1_000L, "IllegalStateException: boom", "trace")
        DiagnosticReports.write(dir, ReportKind.ANR, 2_000L, "Écran bloqué 6 s", "main")

        val listed = DiagnosticReports.list(dir)

        assertEquals(listOf(ReportKind.ANR, ReportKind.CRASH), listed.map { it.kind })
        assertEquals(listOf("Écran bloqué 6 s", "IllegalStateException: boom"), listed.map { it.title })
        assertEquals(2_000L, listed.first().at.toEpochMilli())
        assertEquals("Écran bloqué 6 s\nmain", DiagnosticReports.read(listed.first()))
    }

    @Test
    fun `a title keeps to its one line`() {
        DiagnosticReports.write(dir, ReportKind.CRASH, 1_000L, "first\nsecond", "body")
        assertEquals("first second", DiagnosticReports.list(dir).single().title)
    }

    @Test
    fun `two reports in the same millisecond are both kept`() {
        DiagnosticReports.write(dir, ReportKind.STRICT_MODE, 5_000L, "a", "")
        DiagnosticReports.write(dir, ReportKind.STRICT_MODE, 5_000L, "b", "")
        assertEquals(setOf("a", "b"), DiagnosticReports.list(dir).map { it.title }.toSet())
    }

    @Test
    fun `a flood of StrictMode notes never pushes out a crash`() {
        DiagnosticReports.write(dir, ReportKind.CRASH, 1L, "the crash", "")
        repeat(100) { DiagnosticReports.write(dir, ReportKind.STRICT_MODE, 10L + it, "note $it", "") }

        val listed = DiagnosticReports.list(dir)
        assertTrue(listed.any { it.kind == ReportKind.CRASH && it.title == "the crash" })
        assertEquals(40, listed.count { it.kind == ReportKind.STRICT_MODE })
        assertEquals("note 99", listed.first().title)
    }

    @Test
    fun `problems are trimmed to the newest thirty`() {
        repeat(45) { DiagnosticReports.write(dir, ReportKind.CRASH, 100L + it, "crash $it", "") }
        val listed = DiagnosticReports.list(dir)
        assertEquals(30, listed.size)
        assertEquals("crash 44", listed.first().title)
        assertEquals("crash 15", listed.last().title)
    }

    @Test
    fun `files that are not reports are left out`() {
        File(dir, ".exits-seen").writeText("123")
        File(dir, "notes.txt").writeText("x")
        DiagnosticReports.write(dir, ReportKind.EXIT, 3L, "killed", "")
        assertEquals(listOf("killed"), DiagnosticReports.list(dir).map { it.title })
    }
}
