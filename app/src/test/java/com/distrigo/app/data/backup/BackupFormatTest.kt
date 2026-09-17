package com.distrigo.app.data.backup

import com.distrigo.app.data.local.database.DATABASE_VERSION
import com.distrigo.app.data.local.database.FIRST_MIGRATABLE_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId

class BackupFormatTest {

    private val algiers = ZoneId.of("Africa/Algiers")

    @Test
    fun `the file is named for its local date and time`() {
        // 23:30 UTC on the 16th is 00:30 on the 17th in Algeria.
        assertEquals("DistriGo-2026-09-17-0030.distrigo", BackupFormat.fileName(Instant.parse("2026-09-16T23:30:59Z"), algiers))
        assertEquals("DistriGo-2026-09-17-1430.distrigo", BackupFormat.fileName(Instant.parse("2026-09-17T13:30:00Z"), algiers))
    }

    @Test
    fun `only the names the format writes are known entries`() {
        val hash = "9f".repeat(32)
        for (name in listOf("manifest.json", "distrigo.db", "images/$hash.jpg")) {
            assertTrue(name, BackupFormat.isKnownEntry(name))
        }
        for (name in listOf("", "images/", "distrigo.db-wal", "distrigo.db-journal", "./distrigo.db", "../distrigo.db",
                "/distrigo.db", "images/../distrigo.db", "images\\$hash.jpg", "images/${hash.uppercase()}.jpg",
                "images/${hash.drop(1)}.jpg", "images/$hash.jpg/", "images/a/$hash.jpg", "shared_prefs/business_settings.xml")) {
            assertFalse(name, BackupFormat.isKnownEntry(name))
        }
        assertEquals("images/$hash.jpg", BackupFormat.imageEntry(hash))
        assertTrue(BackupFormat.isImageEntry(BackupFormat.imageEntry(hash)))
    }

    /** The same digest `ImageStore` names its files with, so a photo's entry can be checked against its name. */
    @Test
    fun `sha256 is lower-case hex of the whole stream`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", BackupFormat.sha256(ByteArrayInputStream(ByteArray(0))))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", BackupFormat.sha256(ByteArrayInputStream("abc".toByteArray())))
        val large = ByteArray(200_000) { (it % 251).toByte() }
        assertEquals(BackupFormat.sha256(ByteArrayInputStream(large)), BackupFormat.sha256(ByteArrayInputStream(large.copyOf())))
        assertTrue(BackupFormat.isSha256(BackupFormat.sha256(ByteArrayInputStream(large))))
    }

    private fun backupAt(version: Int, format: Int = 1) = BackupManifest(
        formatVersion = format, schemaVersion = version, appVersion = "", createdAt = Instant.EPOCH,
        databaseId = "d", deviceId = "p", deviceModel = "", rowCounts = emptyMap(),
        entries = listOf(BackupEntry("distrigo.db", 1, "0".repeat(64))),
    )

    @Test
    fun `a backup restores from the first migratable version up to this app's`() {
        assertEquals(32, FIRST_MIGRATABLE_VERSION)
        assertNull(BackupFormat.checkRestorable(backupAt(DATABASE_VERSION)))
        assertNull(BackupFormat.checkRestorable(backupAt(FIRST_MIGRATABLE_VERSION)))
        assertNull(BackupFormat.checkRestorable(backupAt(45)))
    }

    @Test
    fun `a newer database or format, or one too old to migrate, is refused`() {
        assertEquals(BackupProblem.NewerDatabase(53, 52), BackupFormat.checkRestorable(backupAt(53), appVersion = 52))
        assertEquals(BackupProblem.DatabaseTooOld(31, 32), BackupFormat.checkRestorable(backupAt(31)))
        assertEquals(BackupProblem.NewerFormat(2), BackupFormat.checkRestorable(backupAt(52, format = 2)))
    }
}
