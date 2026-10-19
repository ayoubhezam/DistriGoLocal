package com.distrigo.app.data.local.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The license's write gate off the phone (docs/license_l5_enforcement.md §3, §5): its SQL, and that every table
 * of the current schema has been put on one side or the other. The refusals themselves are checked on the phone
 * (androidTest LicenseWriteGateTest).
 */
class LicenseWriteGateTest {

    @Test
    fun `a gated table refuses while the gate reads closed`() {
        assertEquals(
            "CREATE TRIGGER `trg_ventes_license_insert` BEFORE INSERT ON `ventes` FOR EACH ROW " +
                "WHEN (SELECT `value` FROM `app_meta` WHERE `key` = 'license.write_gate') = 'closed' " +
                "BEGIN SELECT RAISE(ABORT, 'LICENSE_READ_ONLY'); END",
            LicenseWriteGate.triggerSql("ventes", "insert"),
        )
        assertEquals("trg_stock_movements_license_delete", LicenseWriteGate.triggerName("stock_movements", "delete"))
        assertTrue(LicenseWriteGate.triggerSql("clients", "update").contains("BEFORE UPDATE ON `clients`"))
    }

    /**
     * A new table fails here until it is gated or said not to be: a document table can't be forgotten. Read from
     * the schema Room exported for [DATABASE_VERSION].
     */
    @Test
    fun `every table of the schema is gated or says why not`() {
        val schema = File("schemas/com.distrigo.app.data.local.database.AppDatabase/$DATABASE_VERSION.json")
        assertTrue("run from the app module: ${schema.absolutePath}", schema.isFile)
        val tables = Regex(""""tableName"\s*:\s*"(\w+)"""").findAll(schema.readText()).map { it.groupValues[1] }.toSet()
        assertTrue("tables read from the schema", tables.size > 40)

        val gated = LicenseWriteGate.GATED_TABLES.toSet()
        val open = LicenseWriteGate.NOT_GATED.keys
        assertEquals("listed twice", emptySet<String>(), gated intersect open)
        assertEquals("neither gated nor said why not", emptySet<String>(), tables - gated - open)
        assertEquals("no such table", emptySet<String>(), (gated + open) - tables)
        assertEquals("a duplicate in GATED_TABLES", LicenseWriteGate.GATED_TABLES.size, gated.size)
    }

    @Test
    fun `every table with a document number, a quantity or money is gated`() {
        for (table in listOf("ventes", "purchase_orders", "retour_client", "retour_fournisseur", "stock_movements",
            "client_payments", "supplier_payments", "charges", "chargement_items", "pertes", "products", "clients", "suppliers")) {
            assertTrue(table, table in LicenseWriteGate.GATED_TABLES)
        }
    }

    @Test
    fun `drafts and settings stay open`() {
        for (table in listOf("vente_drafts", "purchase_drafts", "tournee_vente_drafts", "chargement_drafts",
            "business_settings", "app_meta", "tombstones")) {
            assertFalse(table, table in LicenseWriteGate.GATED_TABLES)
        }
    }

    @Test
    fun `a refusal is recognised however deep it is wrapped`() {
        val refused = LicenseReadOnlyException()
        assertEquals(LicenseWriteGate.MESSAGE, refused.message)
        assertTrue(LicenseWriteGate.isRefusal(refused))
        assertTrue(LicenseWriteGate.isRefusal(RuntimeException("transaction", IllegalStateException(refused))))
        assertFalse(LicenseWriteGate.isRefusal(IllegalStateException("Stock dépôt insuffisant")))
    }
}
