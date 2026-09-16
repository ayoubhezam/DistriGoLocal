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

    /**
     * The app's own builder takes a v33 database with a ledger in it to the latest version, keeps the
     * ledger, and leaves every row with its own UUID.
     */
    @Test
    fun appBuilderMigrates33ToLatestAndKeepsData() {
        helper.createDatabase(TEST_DB, 33).apply {
            insertLedgerAtVersion33()
            close()
        }

        val db = openWithAppPolicy()
        try {
            val sql = db.openHelper.writableDatabase
            assertEquals(LATEST_VERSION, sql.version)

            for ((table, expected) in EXPECTED_ROW_COUNTS) {
                assertDistinctV4Uuids(sql, table, expected)
                assertEquals("unstamped rows in $table", 0, sql.count(table, "updated_at <= 0"))
                assertEquals("rows without created_at in $table", 0, sql.count(table, "created_at = ''"))
            }
            assertEquals(35, UpdatedAtTriggers.trackedTables(sql).size)
            for (table in UpdatedAtTriggers.trackedTables(sql)) {
                assertTrue(table, UpdatedAtTriggers.storedTriggerSql(sql, UpdatedAtTriggers.triggerName(table)) != null)
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
            assertEquals(LATEST_VERSION, sql.version)
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
        helper.createDatabase(TEST_DB, LATEST_VERSION).apply {
            execSQL(
                "INSERT INTO clients (id, name, balance, customer_type) " +
                    "VALUES (1, 'Supérette Nour', 0.0, 'retail')"
            )
            version = LATEST_VERSION + 1
            close()
        }

        val db = openWithAppPolicy()
        try {
            db.openHelper.writableDatabase
            fail("Opening a database newer than the app should throw")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("was required but not found"))
        } finally {
            db.close()
        }

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(TEST_DB).path, null, SQLiteDatabase.OPEN_READONLY
        ).use { raw ->
            assertEquals(LATEST_VERSION + 1, raw.version)
            raw.rawQuery("SELECT name FROM clients WHERE id = 1", null).use { c ->
                assertTrue("client row survived", c.moveToFirst())
                assertEquals("Supérette Nour", c.getString(0))
            }
        }
    }

    /**
     * 42 -> 43 gives each existing row of every business table its own well-formed v4 UUID, and
     * leaves the draft tables without one.
     */
    @Test
    fun migration42To43GivesEveryRowItsOwnUuid() {
        helper.createDatabase(TEST_DB, 42).apply {
            val now = "2026-09-01T09:30:00Z"
            for (i in 1..3) {
                execSQL("INSERT INTO clients (name, balance, customer_type) VALUES ('Client $i', 0.0, 'retail')")
                execSQL(
                    "INSERT INTO ventes (client_id, source, total, montant_paye, status, created_at) " +
                        "VALUES ($i, 'depot', 100.0, 0.0, 'delivered', '$now')"
                )
                execSQL("INSERT INTO policy_tiers (policy_id, min_threshold, tier_order) VALUES (1, ${i * 1000}, $i)")
            }
            for (i in 1..50) {
                execSQL(
                    "INSERT INTO stock_movements (product_id, product_name, type, direction, quantity, " +
                        "emplacement, source_label, source_type, source_id, total_value, created_at) " +
                        "VALUES (1, 'Lait Candia 1L', 'vente', 'sortie', 1.0, 'depot', 'Client', 'vente', $i, 100.0, '$now')"
                )
            }
            execSQL(
                "INSERT INTO vente_drafts (items_json, note, montant_paye, user_name, item_count, total, " +
                    "last_step, created_at, updated_at) VALUES ('[]', '', '', '', 0, 0.0, 'client', '$now', '$now')"
            )
            close()
        }

        val sql = helper.runMigrationsAndValidate(TEST_DB, 43, true, MIGRATION_42_43)
        try {
            assertDistinctV4Uuids(sql, "clients", 3)
            assertDistinctV4Uuids(sql, "ventes", 3)
            assertDistinctV4Uuids(sql, "policy_tiers", 3)
            assertDistinctV4Uuids(sql, "stock_movements", 50)

            val uuidTables = sql.tablesWithColumn("uuid")
            assertEquals(35, uuidTables.size)
            for (draft in listOf("purchase_drafts", "vente_drafts", "tournee_vente_drafts", "chargement_drafts")) {
                assertTrue("$draft has no uuid", draft !in uuidTables)
            }
            // The other tables are empty here; this still catches a row left holding the '' default.
            for (table in uuidTables) {
                assertDistinctV4Uuids(sql, table, sql.count(table))
            }
        } finally {
            sql.close()
        }
    }

    /**
     * 43 -> 44 stamps every existing row as of the upgrade, gives lines their document's creation
     * time, and marks the creation time nothing recorded as unknown.
     */
    @Test
    fun migration43To44StampsRowsAndBackfillsCreatedAt() {
        helper.createDatabase(TEST_DB, 43).apply {
            execSQL(
                "INSERT INTO clients (id, name, balance, customer_type, uuid) " +
                    "VALUES (1, 'Épicerie El Amel', 0.0, 'retail', '00000000-0000-4000-8000-000000000001')"
            )
            execSQL(
                "INSERT INTO ventes (id, client_id, source, total, montant_paye, status, created_at, uuid) " +
                    "VALUES (7, 1, 'depot', 220.0, 0.0, 'delivered', '2026-08-14T10:05:00Z', " +
                    "'00000000-0000-4000-8000-000000000002')"
            )
            execSQL(
                "INSERT INTO vente_items (vente_id, product_id, product_name, unit_type, quantity, unit_price, total_price, uuid) " +
                    "VALUES (7, 1, 'Lait Candia 1L', 'pièce', 2.0, 110.0, 220.0, '00000000-0000-4000-8000-000000000003')"
            )
            // A line whose vente is gone
            execSQL(
                "INSERT INTO vente_items (vente_id, product_id, product_name, unit_type, quantity, unit_price, total_price, uuid) " +
                    "VALUES (99, 1, 'Lait Candia 1L', 'pièce', 1.0, 110.0, 110.0, '00000000-0000-4000-8000-000000000004')"
            )
            execSQL(
                "INSERT INTO inventory_sessions (id, status, started_at, uuid) " +
                    "VALUES (1, 'completed', '2026-07-01T08:00:00Z', '00000000-0000-4000-8000-000000000005')"
            )
            close()
        }

        val before = System.currentTimeMillis()
        val sql = helper.runMigrationsAndValidate(TEST_DB, 44, true, MIGRATION_43_44)
        val after = System.currentTimeMillis()
        try {
            val stamps = listOf("clients", "ventes", "vente_items", "inventory_sessions").flatMap { table ->
                sql.query("SELECT updated_at FROM `$table`").use { c ->
                    buildList { while (c.moveToNext()) add(c.getLong(0)) }
                }
            }
            assertEquals(5, stamps.size)
            assertEquals("one stamp for the whole upgrade", 1, stamps.toSet().size)
            assertTrue(stamps.first() in before..after)

            assertEquals("2026-08-14T10:05:00Z", sql.text("SELECT created_at FROM vente_items WHERE vente_id = 7"))
            assertEquals(UNKNOWN_CREATED_AT, sql.text("SELECT created_at FROM vente_items WHERE vente_id = 99"))
            assertEquals("2026-07-01T08:00:00Z", sql.text("SELECT created_at FROM inventory_sessions WHERE id = 1"))
            assertEquals(UNKNOWN_CREATED_AT, sql.text("SELECT created_at FROM clients WHERE id = 1"))
            // Tables that already had created_at keep it
            assertEquals("2026-08-14T10:05:00Z", sql.text("SELECT created_at FROM ventes WHERE id = 7"))
        } finally {
            sql.close()
        }
    }

    private fun openWithAppPolicy(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .withMigrationPolicy()
            .withChangeTracking()
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

    /** [table] has [expected] rows, each with its own lowercase version-4 UUID. */
    private fun assertDistinctV4Uuids(sql: SupportSQLiteDatabase, table: String, expected: Int) {
        val uuids = sql.query("SELECT uuid FROM `$table`").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        assertEquals("rows in $table", expected, uuids.size)
        for (uuid in uuids) {
            assertTrue("$table: '$uuid' is not a v4 UUID", V4_UUID.matches(uuid))
        }
        assertEquals("$table: duplicate uuids", uuids.size, uuids.toSet().size)
    }

    private fun SupportSQLiteDatabase.tablesWithColumn(column: String): Set<String> {
        val tables = query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table', 'android_metadata')"
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        return tables.filter { table ->
            query("PRAGMA table_info(`$table`)").use { c ->
                val name = c.getColumnIndexOrThrow("name")
                generateSequence { if (c.moveToNext()) c.getString(name) else null }.any { it == column }
            }
        }.toSet()
    }

    private fun SupportSQLiteDatabase.text(sql: String): String =
        query(sql).use { assertTrue(sql, it.moveToFirst()); it.getString(0) }

    private fun SupportSQLiteDatabase.count(table: String, where: String = "1"): Int =
        query("SELECT COUNT(*) FROM `$table` WHERE $where").use { it.moveToFirst(); it.getInt(0) }

    private fun SupportSQLiteDatabase.double(sql: String): Double =
        query(sql).use { assertTrue(sql, it.moveToFirst()); it.getDouble(0) }

    private companion object {
        const val TEST_DB = "migration-test"

        val LATEST_VERSION = ALL_MIGRATIONS.last().endVersion

        const val UNKNOWN_CREATED_AT = "1970-01-01T00:00:00Z"

        val V4_UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

        val EXPECTED_ROW_COUNTS = mapOf(
            "products" to 1, "clients" to 1, "ventes" to 1, "vente_items" to 1,
            "client_payments" to 1, "retour_client" to 1, "suppliers" to 1,
            "purchase_orders" to 1, "supplier_payments" to 1, "retour_fournisseur" to 1,
            "stock_movements" to 1, "product_images" to 0,
        )
    }
}
