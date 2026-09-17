package com.distrigo.app.ui.settings.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.ZoneId

class DataBackupFormattingTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val algiers = ZoneId.of("Africa/Algiers")

    @Test
    fun `sizes read as a file manager shows them`() {
        assertEquals("512 octets", DataBackupFormatting.size(512))
        assertEquals("1 Ko", DataBackupFormatting.size(1024))
        assertEquals("776 Ko", DataBackupFormatting.size(794_624))
        assertEquals("777 Ko", DataBackupFormatting.size(794_625))
        assertEquals("1,4 Mo", DataBackupFormatting.size(1_500_000))
        assertEquals("12 Mo", DataBackupFormatting.size(12_600_000))
    }

    @Test
    fun `dates are local and in French`() {
        // 23:30 UTC on the 16th is 00:30 on the 17th in Algeria.
        assertEquals("17 septembre 2026 à 00:30", DataBackupFormatting.dateTime(Instant.parse("2026-09-16T23:30:00Z"), algiers))
    }

    @Test
    fun `counts read as a sentence, singular for one`() {
        assertEquals("153 produits · 1 client · 0 ventes · 19 bons d'achat",
            DataBackupFormatting.countsSummary(mapOf("products" to 153L, "clients" to 1L, "purchase_orders" to 19L)))
    }

    @Test
    fun `a quiet night is shown as a check, a saved or failed one is not`() {
        val now = Instant.parse("2026-09-18T10:00:00Z")
        val saved = com.distrigo.app.data.backup.auto.AutoBackupState(
            enabled = true, lastAt = Instant.parse("2026-09-17T02:00:05Z"), lastAttemptAt = Instant.parse("2026-09-17T02:00:06Z")
        )
        assertEquals(null, DataBackupFormatting.lastCheck(saved, now, algiers))
        val quiet = saved.copy(lastAttemptAt = Instant.parse("2026-09-18T02:01:00Z"))
        assertEquals("18 septembre 2026 à 03:01 (il y a 7 h) : aucun changement à sauvegarder", DataBackupFormatting.lastCheck(quiet, now, algiers))
        assertEquals(null, DataBackupFormatting.lastCheck(quiet.copy(lastProblem = com.distrigo.app.data.backup.auto.AutoBackupProblem.WRITE_FAILED), now, algiers))
        assertEquals(null, DataBackupFormatting.lastCheck(com.distrigo.app.data.backup.auto.AutoBackupState(enabled = true), now, algiers))
    }

    @Test
    fun `a lasting problem says for how long`() {
        val problem = com.distrigo.app.data.backup.auto.AutoBackupProblem.FOLDER_UNAVAILABLE
        assertEquals(problem.message, DataBackupFormatting.problem(problem, 1))
        assertEquals(problem.message + " (3 échecs de suite)", DataBackupFormatting.problem(problem, 3))
    }

    @Test
    fun `every table the preview compares has a French name`() {
        for (table in com.distrigo.app.data.backup.BackupPreview.HEADLINE_TABLES) {
            assertEquals(table, false, DataBackupFormatting.tableLabel(table) == table)
        }
    }

    @Test
    fun `a safety backup's time comes from its name, or its file`() {
        val named = folder.newFile("avant-restauration-2026-09-17-143005.distrigo")
        assertEquals(Instant.parse("2026-09-17T13:30:05Z"), DataBackupFormatting.safetyBackupTime(named, algiers))
        val other = folder.newFile("avant-restauration-copie.distrigo").apply { setLastModified(1_000_000_000_000) }
        assertEquals(Instant.ofEpochMilli(1_000_000_000_000), DataBackupFormatting.safetyBackupTime(other, algiers))
    }
}
