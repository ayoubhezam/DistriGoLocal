package com.distrigo.app.data.backup

import com.distrigo.app.data.backup.SchemaFingerprint.Column
import com.distrigo.app.data.backup.SchemaFingerprint.Index
import com.distrigo.app.data.backup.SchemaFingerprint.Table
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SchemaFingerprintTest {

    private val expected = SchemaFingerprint(
        tables = mapOf(
            "products" to Table(
                columns = mapOf(
                    "id" to Column("INTEGER", notNull = true, primaryKeyPosition = 1),
                    "name" to Column("TEXT", notNull = true, primaryKeyPosition = 0),
                    "uuid" to Column("TEXT", notNull = true, primaryKeyPosition = 0),
                ),
                indices = mapOf("index_products_uuid" to Index(unique = true, columns = listOf("uuid"))),
            ),
            "clients" to Table(mapOf("id" to Column("INTEGER", true, 1)), emptyMap()),
        ),
        views = emptySet(),
        triggers = mapOf("trg_products_updated_at" to "CREATE TRIGGER trg_products_updated_at ..."),
    )

    private fun products(change: (Table) -> Table) =
        expected.copy(tables = expected.tables + ("products" to change(expected.tables.getValue("products"))))

    @Test
    fun `the same shape has no differences`() {
        assertEquals(emptyList<String>(), expected.copy().differencesFrom(expected))
    }

    @Test
    fun `each kind of difference is named`() {
        assertEquals(listOf("missing table clients"), expected.copy(tables = expected.tables - "clients").differencesFrom(expected))
        assertEquals(listOf("extra table notes"), expected.copy(tables = expected.tables + ("notes" to Table(emptyMap(), emptyMap()))).differencesFrom(expected))
        assertEquals(listOf("missing column products.uuid"), products { it.copy(columns = it.columns - "uuid") }.differencesFrom(expected))
        assertEquals(
            listOf("column products.name is Column(affinity=TEXT, notNull=false, primaryKeyPosition=0), not Column(affinity=TEXT, notNull=true, primaryKeyPosition=0)"),
            products { it.copy(columns = it.columns + ("name" to Column("TEXT", false, 0))) }.differencesFrom(expected)
        )
        assertEquals(listOf("indices of products differ"), products { it.copy(indices = emptyMap()) }.differencesFrom(expected))
        assertEquals(listOf("views differ"), expected.copy(views = setOf("v")).differencesFrom(expected))
        assertEquals(listOf("extra trigger evil"), expected.copy(triggers = expected.triggers + ("evil" to "CREATE TRIGGER evil ...")).differencesFrom(expected))
        assertEquals(listOf("missing trigger trg_products_updated_at"), expected.copy(triggers = emptyMap()).differencesFrom(expected))
        assertEquals(listOf("trigger trg_products_updated_at differs"), expected.copy(triggers = mapOf("trg_products_updated_at" to "CREATE TRIGGER other")).differencesFrom(expected))
    }

    /** SQLite's affinity rules, in the order SQLite applies them. */
    @Test
    fun `declared types map to their affinity`() {
        val cases = mapOf("INTEGER" to "INTEGER", "BIGINT" to "INTEGER", "TEXT" to "TEXT", "VARCHAR(20)" to "TEXT",
            "CHARINT" to "INTEGER", "BLOB" to "BLOB", "" to "BLOB", "REAL" to "REAL", "DOUBLE" to "REAL", "NUMERIC" to "NUMERIC", "boolean" to "NUMERIC")
        for ((declared, affinity) in cases) assertEquals(declared, affinity, SchemaFingerprint.affinity(declared))
        assertEquals("BLOB", SchemaFingerprint.affinity(null))
    }

    @Test
    fun `restoring asks for room to unpack every entry and open the database`() {
        val manifest = BackupManifest(1, 52, "", Instant.EPOCH, "d", "p", "", emptyMap(),
            listOf(BackupEntry("distrigo.db", 1_000_000, "0".repeat(64)), BackupEntry("images/${"1".repeat(64)}.jpg", 50_000, "1".repeat(64))))
        assertEquals(1_050_000L + 1_000_000L + (16L shl 20), RestorePreparer.spaceNeeded(manifest))
    }
}
