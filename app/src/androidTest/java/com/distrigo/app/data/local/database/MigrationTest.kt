package com.distrigo.app.data.local.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The migration policy the app ships with: every registered path migrates, only a pre-32 install
 * is recreated, and anything else fails without touching the file.
 *
 * These run on a real device, next to the app's own database. Every test works on [TEST_DB] and
 * nothing here opens `distrigo` or calls `AppDatabase.getDatabase`.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, AppDatabase::class.java)

    @Before
    fun startClean() {
        assertNotEquals("distrigo", TEST_DB)
        deleteTestDatabase()
    }

    /** `deleteDatabase` removes the -wal, -shm and -journal files, but not the lock file Room adds. */
    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DB)
        File(context.getDatabasePath(TEST_DB).path + ".lck").delete()
    }

    /**
     * Each migration, run alone from its own version, produces exactly the schema Room exported for
     * the next one.
     *
     * 32 was never exported, so its database is rebuilt as 33 without `purchase_drafts`, the one
     * table MIGRATION_32_33 adds.
     */
    @Test
    fun eachMigrationProducesTheExportedSchema() {
        for (migration in ALL_MIGRATIONS) {
            if (migration.startVersion == 32) {
                helper.createDatabase(TEST_DB, 33).apply {
                    execSQL("DROP TABLE `purchase_drafts`")
                    version = 32
                    close()
                }
            } else {
                helper.createDatabase(TEST_DB, migration.startVersion).close()
            }
            helper.runMigrationsAndValidate(TEST_DB, migration.endVersion, true, migration).close()
        }
    }

    /** The app's own builder takes a v33 database with a ledger in it to v42, and keeps the ledger. */
    @Test
    fun appBuilderMigrates33To42AndKeepsData() {
        helper.createDatabase(TEST_DB, 33).apply {
            insertLedgerAtVersion33()
            close()
        }

        val db = openWithAppPolicy()
        try {
            val sql = db.openHelper.writableDatabase
            assertEquals(42, sql.version)

            for ((table, expected) in EXPECTED_ROW_COUNTS) {
                assertEquals("rows in $table", expected, sql.count(table))
            }
            assertEquals(12.5, sql.double("SELECT stock FROM products WHERE id = 1"), 0.0)

            // MIGRATION_40_41 recomputes both balances, so the wrong values stored at v33 are gone.
            // Client: sales 1000 - paid 400 - payment 100 - return 50.
            assertEquals(450.0, sql.double("SELECT balance FROM clients WHERE id = 1"), 0.001)
            // Supplier: opening 200 + orders 5000 - paid 1000 - payment 500 - return 300.
            assertEquals(3400.0, sql.double("SELECT balance FROM suppliers WHERE id = 1"), 0.001)
        } finally {
            db.close()
        }
    }

    /** A pre-32 install has no migration path, and is recreated empty as it always was. */
    @Test
    fun appBuilderRecreatesPre32Database() {
        context.openOrCreateDatabase(TEST_DB, Context.MODE_PRIVATE, null).apply {
            execSQL("CREATE TABLE clients (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
            execSQL("INSERT INTO clients (name) VALUES ('Épicerie El Amel')")
            version = 31
            close()
        }

        val db = openWithAppPolicy()
        try {
            val sql = db.openHelper.writableDatabase
            assertEquals(42, sql.version)
            assertEquals(0, sql.count("clients"))
        } finally {
            db.close()
        }
    }

    /**
     * A version with no registered path now fails on open and keeps the file as it was. The old
     * unconditional fallback deleted the data here instead.
     *
     * A downgrade is the case that can be built without breaking the migration list: an older
     * build installed over a newer database.
     */
    @Test
    fun appBuilderRefusesUnknownVersionAndKeepsData() {
        helper.createDatabase(TEST_DB, 42).apply {
            execSQL(
                "INSERT INTO clients (id, name, balance, customer_type) " +
                    "VALUES (1, 'Supérette Nour', 0.0, 'retail')"
            )
            version = 43
            close()
        }

        val db = openWithAppPolicy()
        try {
            db.openHelper.writableDatabase
            fail("Opening a v43 database with a v42 app should throw")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("was required but not found"))
        } finally {
            db.close()
        }

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(TEST_DB).path, null, SQLiteDatabase.OPEN_READONLY
        ).use { raw ->
            assertEquals(43, raw.version)
            raw.rawQuery("SELECT name FROM clients WHERE id = 1", null).use { c ->
                assertTrue("client row survived", c.moveToFirst())
                assertEquals("Supérette Nour", c.getString(0))
            }
        }
    }

    private fun openWithAppPolicy(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .withMigrationPolicy()
            .build()

    /** One client and one supplier with a small ledger each, and deliberately wrong stored balances. */
    private fun SupportSQLiteDatabase.insertLedgerAtVersion33() {
        val now = "2026-09-01T09:30:00Z"
        execSQL(
            "INSERT INTO products (id, name, barcode, selling_price, purchase_price, stock, min_stock, " +
                "unit_type, packages, pack_size, has_expiry, camion_stock) " +
                "VALUES (1, 'Lait Candia 1L', '6130000000017', 110.0, 95.0, 12.5, 10, 'pièce', 0, 12, 0, 2.0)"
        )
        execSQL(
            "INSERT INTO clients (id, name, balance, customer_type) " +
                "VALUES (1, 'Épicerie El Amel', 999.0, 'retail')"
        )
        execSQL(
            "INSERT INTO ventes (id, client_id, source, total, montant_paye, status, created_at) " +
                "VALUES (1, 1, 'depot', 1000.0, 400.0, 'delivered', '$now')"
        )
        execSQL(
            "INSERT INTO vente_items (vente_id, product_id, product_name, unit_type, quantity, unit_price, total_price) " +
                "VALUES (1, 1, 'Lait Candia 1L', 'pièce', 10.0, 100.0, 1000.0)"
        )
        execSQL("INSERT INTO client_payments (client_id, amount, created_at) VALUES (1, 100.0, '$now')")
        execSQL(
            "INSERT INTO retour_client (client_id, date, total, created_at) " +
                "VALUES (1, '2026-09-02', 50.0, '$now')"
        )
        execSQL(
            "INSERT INTO suppliers (id, name, balance, initial_balance, created_at) " +
                "VALUES (1, 'Laiterie Soummam', 0.0, 200.0, '$now')"
        )
        execSQL(
            "INSERT INTO purchase_orders (supplier_id, date, total, status, montant_paye, created_at) " +
                "VALUES (1, '2026-09-01', 5000.0, 'received', 1000.0, '$now')"
        )
        execSQL("INSERT INTO supplier_payments (supplier_id, amount, created_at) VALUES (1, 500.0, '$now')")
        execSQL(
            "INSERT INTO retour_fournisseur (supplier_id, date, total, created_at) " +
                "VALUES (1, '2026-09-03', 300.0, '$now')"
        )
        execSQL(
            "INSERT INTO stock_movements (product_id, product_name, type, direction, quantity, emplacement, " +
                "source_label, source_type, source_id, unit_price, total_value, created_at) " +
                "VALUES (1, 'Lait Candia 1L', 'vente', 'sortie', 10.0, 'depot', 'Épicerie El Amel', 'vente', 1, 100.0, 1000.0, '$now')"
        )
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    private fun SupportSQLiteDatabase.double(sql: String): Double =
        query(sql).use { assertTrue(sql, it.moveToFirst()); it.getDouble(0) }

    private companion object {
        const val TEST_DB = "migration-test"

        val EXPECTED_ROW_COUNTS = mapOf(
            "products" to 1, "clients" to 1, "ventes" to 1, "vente_items" to 1,
            "client_payments" to 1, "retour_client" to 1, "suppliers" to 1,
            "purchase_orders" to 1, "supplier_payments" to 1, "retour_fournisseur" to 1,
            "stock_movements" to 1, "product_images" to 0,
        )
    }
}
