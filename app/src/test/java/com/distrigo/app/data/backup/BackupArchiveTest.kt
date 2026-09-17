package com.distrigo.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A backup file is accepted only whole, exactly as its manifest describes it. */
class BackupArchiveTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val databaseBytes = ByteArray(300_000) { (it * 7 % 256).toByte() }
    private val photoBytes = ByteArray(12_000) { (it % 97).toByte() }
    private val photoHash = BackupFormat.sha256(ByteArrayInputStream(photoBytes))
    private val photoName = BackupFormat.imageEntry(photoHash)

    private fun file(name: String, bytes: ByteArray) = File(folder.root, name).apply { writeBytes(bytes) }

    private val database by lazy { file("distrigo.db", databaseBytes) }
    private val photo by lazy { file("$photoHash.jpg", photoBytes) }

    private val manifest by lazy {
        BackupManifest(
            formatVersion = 1, schemaVersion = 52, appVersion = "1.0 (1)", createdAt = Instant.parse("2026-09-17T13:30:05Z"),
            databaseId = "38b1db1e", deviceId = "6ded79d5", deviceModel = "samsung SM-M346B",
            rowCounts = mapOf("products" to 153L),
            entries = listOf(BackupArchive.describe(BackupFormat.DATABASE_ENTRY, database), BackupArchive.describe(photoName, photo)),
        )
    }

    private fun backup(): ByteArray = ByteArrayOutputStream().also {
        BackupArchive.write(manifest, mapOf(BackupFormat.DATABASE_ENTRY to database, photoName to photo), it)
    }.toByteArray()

    /** A ZIP of exactly these entries, in this order, for files the writer would never produce. */
    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private val manifestBytes get() = manifest.toJson().toByteArray()

    private fun verify(bytes: ByteArray) = BackupArchive.verify(ByteArrayInputStream(bytes))

    private fun assertDamaged(bytes: ByteArray, detail: String) {
        val result = verify(bytes)
        assertTrue("$detail: $result", result is Verification.Failed && result.problem is BackupProblem.Damaged)
        assertTrue("$detail: $result", detail in ((result as Verification.Failed).problem as BackupProblem.Damaged).detail)
    }

    @Test
    fun `a written backup verifies, with the manifest first`() {
        val bytes = backup()
        assertEquals(Verification.Verified(manifest), verify(bytes))
        val first = java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).nextEntry!!
        assertEquals("manifest.json", first.name)
    }

    @Test
    fun `each entry's bytes reach the caller as they are read`() {
        val received = mutableMapOf<String, ByteArray>()
        val result = BackupArchive.verify(ByteArrayInputStream(backup()), onEntry = { entry, input -> received[entry.name] = input.readBytes() })
        assertTrue(result is Verification.Verified)
        assertArrayEquals(databaseBytes, received[BackupFormat.DATABASE_ENTRY])
        assertArrayEquals(photoBytes, received[photoName])
    }

    /** A caller that reads only part of an entry, or closes it, does not stop the rest being checked. */
    @Test
    fun `a caller reading part of an entry does not skip its check`() {
        val result = BackupArchive.verify(ByteArrayInputStream(backup()), onEntry = { _, input -> input.read(ByteArray(10)); input.close() })
        assertEquals(Verification.Verified(manifest), result)
    }

    @Test
    fun `the manifest is handed over before any entry, and throwing there stops the reading`() {
        val order = mutableListOf<String>()
        val result = BackupArchive.verify(
            ByteArrayInputStream(backup()),
            onManifest = { order += "manifest:${it.entries.size}" },
            onEntry = { entry, _ -> order += entry.name },
        )
        assertTrue(result is Verification.Verified)
        assertEquals(listOf("manifest:2", BackupFormat.DATABASE_ENTRY, photoName), order)

        class Refused : RuntimeException()
        var entries = 0
        try {
            BackupArchive.verify(ByteArrayInputStream(backup()), onManifest = { throw Refused() }, onEntry = { _, _ -> entries++ })
            throw AssertionError("not stopped")
        } catch (expected: Refused) {
        }
        assertEquals(0, entries)
    }

    @Test
    fun `what is not a backup says so`() {
        assertEquals(Verification.Failed(BackupProblem.NotABackup), verify(ByteArray(0)))
        assertEquals(Verification.Failed(BackupProblem.NotABackup), verify("PDF-1.7 not a zip".toByteArray()))
        assertEquals(Verification.Failed(BackupProblem.NotABackup), verify(zipOf("photo.jpg" to photoBytes)))
        assertEquals(Verification.Failed(BackupProblem.NotABackup), verify(zipOf("distrigo.db" to databaseBytes, "manifest.json" to manifestBytes)))
        assertEquals(Verification.Failed(BackupProblem.NotABackup), verify(zipOf("manifest.json" to "<html>".toByteArray())))
    }

    @Test
    fun `a backup from a later format is reported as such`() {
        val later = zipOf("manifest.json" to """{"format_version": 2}""".toByteArray())
        assertEquals(Verification.Failed(BackupProblem.NewerFormat(2)), verify(later))
    }

    @Test
    fun `a changed byte is caught`() {
        val changed = databaseBytes.copyOf().also { it[150_000] = (it[150_000] + 1).toByte() }
        assertDamaged(zipOf("manifest.json" to manifestBytes, "distrigo.db" to changed, photoName to photoBytes), "distrigo.db checksum")
    }

    @Test
    fun `a missing, extra or foreign entry is caught`() {
        assertDamaged(zipOf("manifest.json" to manifestBytes, "distrigo.db" to databaseBytes), "missing $photoName")
        val other = BackupFormat.imageEntry("0".repeat(64))
        assertDamaged(zipOf("manifest.json" to manifestBytes, "distrigo.db" to databaseBytes, photoName to photoBytes, other to photoBytes), "unlisted $other")
        assertDamaged(zipOf("manifest.json" to manifestBytes, "../shared_prefs/x.xml" to photoBytes), "unknown entry ../shared_prefs/x.xml")
        assertDamaged(zipOf("manifest.json" to manifestBytes, "distrigo.db" to databaseBytes, "distrigo.db-wal" to photoBytes), "unknown entry distrigo.db-wal")
    }

    @Test
    fun `an entry shorter or longer than listed is caught`() {
        assertDamaged(zipOf("manifest.json" to manifestBytes, "distrigo.db" to databaseBytes.copyOf(299_999), photoName to photoBytes), "distrigo.db is 299999 bytes")
        assertDamaged(zipOf("manifest.json" to manifestBytes, "distrigo.db" to databaseBytes + 0, photoName to photoBytes), "distrigo.db larger than 300000")
    }

    /** 50 MB of zeros compresses to a few kilobytes; reading stops one byte past what the manifest allows. */
    @Test
    fun `an entry that unpacks far past its listed size is not unpacked`() {
        val bomb = zipOf("manifest.json" to manifestBytes, "distrigo.db" to ByteArray(50 shl 20))
        assertTrue(bomb.size < 200_000)
        var read = 0L
        BackupArchive.verify(ByteArrayInputStream(bomb), onEntry = { _, input -> read += input.readBytes().size })
        assertTrue("read $read", read <= 300_000)
        assertDamaged(bomb, "distrigo.db larger than 300000")
    }

    @Test
    fun `an oversized manifest is refused before it is parsed`() {
        assertDamaged(zipOf("manifest.json" to ByteArray((1 shl 20) + 1) { ' '.code.toByte() }), "manifest too large")
    }

    @Test
    fun `a file cut short, or with bytes after its end, is caught`() {
        val bytes = backup()
        for (length in listOf(bytes.size - 30, bytes.size / 2, 200)) {
            val result = verify(bytes.copyOf(length))
            assertTrue("cut at $length: $result", result is Verification.Failed)
        }
        assertDamaged(bytes + "trailing".toByteArray(), "no end record")
    }

    @Test
    fun `verifying reads from a stream it does not close`() {
        var closed = false
        val input = object : InputStream() {
            val inner = ByteArrayInputStream(backup())
            override fun read() = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int) = inner.read(b, off, len)
            override fun close() { closed = true }
        }
        assertTrue(BackupArchive.verify(input) is Verification.Verified)
        assertEquals(false, closed)
    }
}
