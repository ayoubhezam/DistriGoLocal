package com.distrigo.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** What `manifest.json` must hold for a restore to trust it. */
class BackupManifestTest {

    private val databaseHash = "a".repeat(64)
    private val logoHash = "0123456789abcdef".repeat(4)

    private val manifest = BackupManifest(
        formatVersion = 1,
        schemaVersion = 52,
        appVersion = "1.0 (1)",
        createdAt = Instant.parse("2026-09-17T13:30:05.123Z"),
        databaseId = "38b1db1e-5d0c-4c52-9f63-0f1d2b1c6a11",
        deviceId = "6ded79d5-210a-4e54-a58b-5caf1b0e7ee9",
        deviceModel = "samsung SM-M346B",
        rowCounts = mapOf("products" to 153L, "clients" to 18L, "ventes" to 27L),
        entries = listOf(
            BackupEntry("distrigo.db", 966_656, databaseHash),
            BackupEntry("images/$logoHash.jpg", 48_211, "b".repeat(64)),
        ),
    )

    private fun valid(text: String) = (BackupManifest.parse(text) as ManifestResult.Valid).manifest

    private fun problem(text: String) = (BackupManifest.parse(text) as ManifestResult.Invalid).problem

    /** Takes the written manifest and replaces one line, the way a hand-edited or damaged file would differ. */
    private fun edited(from: String, to: String): String {
        val json = manifest.toJson()
        assertTrue("the manifest has $from", from in json)
        return json.replace(from, to)
    }

    @Test
    fun `a written manifest reads back the same`() {
        assertEquals(manifest, valid(manifest.toJson()))
    }

    @Test
    fun `it is readable JSON with the names the format document gives`() {
        val json = manifest.toJson()
        for (key in listOf("format_version", "schema_version", "app_version", "created_at", "database_id",
                "device_id", "device_model", "row_counts", "entries", "name", "size", "sha256")) {
            assertTrue(key, "\"$key\"" in json)
        }
        assertTrue("\"created_at\": \"2026-09-17T13:30:05.123Z\"" in json)
        assertEquals(manifest.database, manifest.entry("distrigo.db"))
        assertEquals(listOf("images/$logoHash.jpg"), manifest.images.map { it.name })
    }

    @Test
    fun `fields a later app adds are ignored`() {
        val later = edited("\"format_version\": 1,", "\"format_version\": 1,\n  \"encrypted\": false,")
        assertEquals(manifest, valid(later))
    }

    @Test
    fun `a later format is reported as such before anything else is checked`() {
        assertEquals(BackupProblem.NewerFormat(2), problem("""{"format_version": 2, "something": "else"}"""))
    }

    @Test
    fun `something that is not a manifest is not a backup`() {
        for (text in listOf("", "PK", "[1, 2]", "\"distrigo\"", "{}", """{"format_version": "1"}""")) {
            assertEquals(text, BackupProblem.NotABackup, problem(text))
        }
    }

    @Test
    fun `a missing or malformed field makes it damaged`() {
        val damaged = listOf(
            edited("\"schema_version\": 52,", ""),
            edited("\"schema_version\": 52", "\"schema_version\": 0"),
            edited("\"schema_version\": 52", "\"schema_version\": 52.5"),
            edited("2026-09-17T13:30:05.123Z", "17/09/2026 14:30"),
            edited("\"database_id\": \"38b1db1e-5d0c-4c52-9f63-0f1d2b1c6a11\"", "\"database_id\": \"  \""),
            edited("\"device_id\": \"6ded79d5-210a-4e54-a58b-5caf1b0e7ee9\",", ""),
            edited("\"products\": 153", "\"products\": -1"),
            edited("\"products\": 153", "\"products\": \"153\""),
            edited("\"size\": 966656", "\"size\": \"966656\""),
            edited(databaseHash, databaseHash.uppercase()),
            edited(databaseHash, databaseHash.take(63)),
        )
        for (text in damaged) {
            assertTrue(text, problem(text) is BackupProblem.Damaged)
        }
    }

    @Test
    fun `an entry the format does not write makes it damaged`() {
        for (name in listOf("../distrigo.db", "/data/data/com.distrigo.app/databases/distrigo", "images/",
                "images/../../shared_prefs/x.xml", "images\\\\$logoHash.jpg", "images/$logoHash.png",
                "images/sub/$logoHash.jpg", "manifest.json", "distrigo.db-wal")) {
            val text = edited("\"name\": \"images/$logoHash.jpg\"", "\"name\": \"$name\"")
            assertTrue(name, problem(text) is BackupProblem.Damaged)
        }
    }

    @Test
    fun `a backup without a database or with an entry listed twice is damaged`() {
        val noDatabase = manifest.copy(entries = manifest.images).toJson()
        assertTrue(problem(noDatabase) is BackupProblem.Damaged)
        val twice = manifest.copy(entries = manifest.entries + manifest.images).toJson()
        assertTrue(problem(twice) is BackupProblem.Damaged)
    }

    @Test
    fun `an entry larger than its limit is damaged`() {
        val huge = manifest.copy(entries = listOf(manifest.database, BackupEntry("images/$logoHash.jpg", BackupFormat.MAX_IMAGE_BYTES + 1, "b".repeat(64))))
        assertTrue(problem(huge.toJson()) is BackupProblem.Damaged)
    }

    @Test
    fun `app version and device model are optional`() {
        val bare = manifest.toJson().lines()
            .filterNot { "\"app_version\"" in it || "\"device_model\"" in it }
            .joinToString("\n")
        assertEquals(manifest.copy(appVersion = "", deviceModel = ""), valid(bare))
    }
}
