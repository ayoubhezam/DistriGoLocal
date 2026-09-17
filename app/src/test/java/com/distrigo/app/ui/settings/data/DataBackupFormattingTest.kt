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
