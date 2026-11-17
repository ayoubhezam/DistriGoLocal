package com.distrigo.app.data.backup.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AutoBackupNamesTest {

    private val algiers = ZoneId.of("Africa/Algiers")

    @Test
    fun `names are local time to the second`() {
        // 02:00:15 UTC is 03:00:15 in Algeria.
        assertEquals("DISTRONIQ-auto-2026-09-18-030015.distrigo", AutoBackupNames.forTime(Instant.parse("2026-09-18T02:00:15Z"), algiers))
    }

    @Test
    fun `only exact automatic names are automatic`() {
        assertTrue(AutoBackupNames.isAutomatic("DISTRONIQ-auto-2026-09-18-030015.distrigo"))
        // Made before the app was renamed: still this app's own, still pruned
        assertTrue(AutoBackupNames.isAutomatic("DistriGo-auto-2026-09-18-030015.distrigo"))
        for (name in listOf(
            "DistriGo-2026-09-17-1311.distrigo",          // a manual backup
            "DistriGo-auto-2026-09-18-030015 (1).distrigo", // a copy the provider renamed
            "DistriGo-auto-2026-09-18-030015.distrigo.zip",
            "DistriGo-auto-2026-09-18-0300.distrigo",
            "avant-restauration-2026-09-17-134017.distrigo",
            "Copie de DistriGo-auto-2026-09-18-030015.distrigo",
            "DISTRONIQ-2026-09-17-1311.distrigo",
            "Distrigo-auto-2026-09-18-030015.distrigo",
            "notes.txt",
        )) {
            assertFalse(name, AutoBackupNames.isAutomatic(name))
        }
    }

    @Test
    fun `only automatic backups beyond the newest are removed`() {
        val autos = (10..19).map { "DistriGo-auto-2026-09-$it-030000.distrigo" }
        val others = listOf("DistriGo-2026-09-01-1200.distrigo", "notes.txt", "DistriGo-auto-2026-09-01-030000 (1).distrigo")
        val removed = AutoBackupNames.beyondNewest((others + autos).shuffled(java.util.Random(7)), keep = 7)
        assertEquals((10..12).map { "DistriGo-auto-2026-09-$it-030000.distrigo" }.toSet(), removed.toSet())
    }

    /** Names sort by time as text, across months and years. */
    @Test
    fun `the newest is the one sorted last`() {
        val names = listOf(
            AutoBackupNames.forTime(Instant.parse("2026-12-31T22:00:00Z"), algiers),
            AutoBackupNames.forTime(Instant.parse("2027-01-01T00:30:00Z"), algiers),
            AutoBackupNames.forTime(Instant.parse("2026-09-18T02:00:00Z"), algiers),
        )
        assertEquals(listOf(names[2], names[0]), AutoBackupNames.beyondNewest(names, keep = 1).sorted())
    }

    /** After the rename the folder holds both names: the newest are chosen by their time, never by their prefix. */
    @Test
    fun `old and new names are pruned by their time`() {
        val old = (10..14).map { "DistriGo-auto-2026-11-$it-033000.distrigo" }
        val new = (15..19).map { "DISTRONIQ-auto-2026-11-$it-033000.distrigo" }
        val removed = AutoBackupNames.beyondNewest((old + new).shuffled(java.util.Random(3)), keep = 7)
        assertEquals(old.take(3).toSet(), removed.toSet())
    }
}
