package com.distrigo.app.data.local.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.ui.common.extractErrorMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The license's write gate (docs/license_l5_enforcement.md §3): closed, the gated tables refuse every write, the
 * others don't, and only [LicenseWriteGate.allowing] lets the D4 writes through.
 *
 * A named file database, so it can be reopened; not the app's — [TEST_DB] is deleted before and after every test.
 */
@RunWith(AndroidJUnit4::class)
class LicenseWriteGateTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var repository: ProductRepository

    /** What the license allows, as the app's database reads it when it opens. */
    private var licenseAllows = true

    @Before
    fun startClean() {
        assertNotEquals("distrigo", TEST_DB)
        deleteTestDatabase()
        open()
    }

    @After
    fun deleteTestDatabase() {
        if (::db.isInitialized && db.isOpen) db.close()
        context.deleteDatabase(TEST_DB)
        File(context.getDatabasePath(TEST_DB).path + ".lck").delete()
    }

    private fun open() {
        if (::db.isInitialized && db.isOpen) db.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .withMigrationPolicy()
            .withChangeTracking()
            .withLicenseGate { licenseAllows }
            .build()
        sql = db.openHelper.writableDatabase
        repository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
    }

    // ── The triggers ──

    @Test
    fun everyGatedTableHasItsThreeTriggers() {
        for (table in LicenseWriteGate.GATED_TABLES) {
            for (event in listOf("insert", "update", "delete")) {
                val name = LicenseWriteGate.triggerName(table, event)
                assertEquals(name, LicenseWriteGate.triggerSql(table, event), storedTriggerSql(sql, name))
            }
        }
    }

    @Test
    fun theOtherTablesHaveNone() {
        for (table in LicenseWriteGate.NOT_GATED.keys) {
            assertEquals(table, 0L, sql.long("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name GLOB 'trg_${table}_license_*'"))
        }
    }

    @Test
    fun aTableTakenOffTheListLosesItsTriggers() {
        sql.execSQL(LicenseWriteGate.triggerSql("price_history", "insert"))
        assertTrue(storedTriggerSql(sql, LicenseWriteGate.triggerName("price_history", "insert")) != null)
        LicenseWriteGate.install(sql)
        assertNull(storedTriggerSql(sql, LicenseWriteGate.triggerName("price_history", "insert")))
    }

    // ── Who writes the value ──

    @Test
    fun theDatabaseOpensWithWhatTheLicenseAllows() {
        assertFalse(LicenseWriteGate.isClosed(sql))
        licenseAllows = false
        open()
        assertTrue(LicenseWriteGate.isClosed(sql))
        licenseAllows = true
        open()
        assertFalse(LicenseWriteGate.isClosed(sql))
    }

    // ── Closed ──

    /** BEFORE INSERT fires before the column constraints, so an empty row is enough to meet the gate. */
    @Test
    fun closedEveryGatedTableRefusesAnInsert() {
        close()
        for (table in LicenseWriteGate.GATED_TABLES) {
            assertRefused(table) { sql.execSQL("INSERT INTO `$table` DEFAULT VALUES") }
        }
    }

    @Test
    fun closedADocumentCannotBeChangedOrDeleted() = runBlocking {
        val product = product()
        val client = client()
        repository.createVente(client, null, "depot", listOf(line(product, 2.0)), null, 0.0)
        val vente = sql.long("SELECT MAX(id) FROM ventes")
        close()

        for (table in listOf("ventes", "vente_items", "stock_movements", "products", "clients")) {
            assertRefused(table) { sql.execSQL("UPDATE `$table` SET `updated_at` = `updated_at` + 1") }
            assertRefused(table) { sql.execSQL("DELETE FROM `$table`") }
        }
        assertEquals(1L, sql.long("SELECT COUNT(*) FROM ventes WHERE id = $vente"))
        assertEquals(1L, sql.long("SELECT COUNT(*) FROM stock_movements WHERE source_type = 'vente'"))
    }

    @Test
    fun closedTheRepositoriesAreRefusedAndSayWhyInFrench() = runBlocking {
        val product = product()
        val client = client()
        close()

        val e = runCatching { repository.createVente(client, null, "depot", listOf(line(product, 1.0)), null, 0.0) }.exceptionOrNull()
        assertTrue("$e", e != null && LicenseWriteGate.isRefusal(e))
        assertEquals(LicenseWriteGate.MESSAGE, extractErrorMessage(e as Exception))
        assertEquals(0L, sql.long("SELECT COUNT(*) FROM ventes"))
        assertRefused("products") { runBlocking { repository.addProduct(mapOf("name" to "Lben Soummam", "selling_price" to 90.0)) } }
    }

    @Test
    fun closedDraftsSettingsAndMetaAreStillWritten() {
        close()
        sql.execSQL(
            "INSERT INTO vente_drafts (items_json, note, montant_paye, user_name, item_count, total, last_step, created_at, updated_at) " +
                "VALUES ('[]', '', '0', '', 0, 0, 'produits', '2026-10-19', '2026-10-19')"
        )
        sql.execSQL(
            "INSERT INTO chargement_drafts (items_json, note, user_name, item_count, created_at, updated_at) " +
                "VALUES ('[]', '', '', 0, '2026-10-19', '2026-10-19')"
        )
        sql.execSQL("DELETE FROM vente_drafts")
        sql.execSQL("INSERT OR REPLACE INTO business_settings (id, business_name) VALUES (1, 'DISTRIGO')")
        sql.execSQL("UPDATE business_settings SET allow_negative_stock = 0")
        sql.execSQL("INSERT OR REPLACE INTO app_meta (`key`, `value`) VALUES ('backup.last_at', '2026-10-19T08:00:00Z')")
        assertEquals(1L, sql.long("SELECT COUNT(*) FROM chargement_drafts"))
        assertTrue(LicenseWriteGate.isClosed(sql))
    }

    // ── D4: what goes through a closed gate ──

    @Test
    fun closedTheOpenTourneeStillCloses() = runBlocking {
        repository.createTournee("Tournée Bab Ezzouar", null, null, null)
        val tournee = db.tourneeDao().getOpenTournee()!!.id
        close()

        repository.closeTournee(tournee)

        assertEquals("fermée", db.tourneeDao().getTourneeById(tournee)!!.status)
        assertTrue("the gate closes again", LicenseWriteGate.isClosed(sql))
        assertRefused("tournees") { runBlocking { repository.createTournee("Tournée Kouba", null, null, null) } }
    }

    @Test
    fun closedTheTruckUnloadsButDoesNotLoad() = runBlocking {
        val product = product(stock = 40.0)
        repository.createChargement(null, listOf(transfer(product, 10.0, "vers_camion")))
        close()

        repository.createChargement(null, listOf(transfer(product, 4.0, "vers_depot")))
        assertEquals(6.0, db.productDao().getProductById(product)!!.camion_stock, 0.0001)
        assertTrue("the gate closes again", LicenseWriteGate.isClosed(sql))

        assertRefused("chargements") { runBlocking { repository.createChargement(null, listOf(transfer(product, 1.0, "vers_camion"))) } }
        // A mixed chargement loads too: refused as a whole
        assertRefused("chargements") {
            runBlocking { repository.createChargement(null, listOf(transfer(product, 1.0, "vers_depot"), transfer(product, 1.0, "vers_camion"))) }
        }
        assertEquals(6.0, db.productDao().getProductById(product)!!.camion_stock, 0.0001)
    }

    @Test
    fun allowingLetsOnlyItsOwnWriteThroughEvenWhenItFails() = runBlocking {
        val product = product()
        close()

        runCatching {
            LicenseWriteGate.allowing(db, LicenseWriteGate.Allowance.TOURNEE_CLOSE) {
                sql.execSQL("UPDATE products SET name = 'Renamed' WHERE id = $product")
                error("fails after writing")
            }
        }
        assertTrue(LicenseWriteGate.isClosed(sql))
        assertEquals("Lait Candia 1L", db.productDao().getProductById(product)!!.name)

        // No allowance: a plain transaction, and the gate decides
        assertRefused("products") {
            runBlocking { LicenseWriteGate.allowing(db, null) { sql.execSQL("UPDATE products SET name = 'Renamed' WHERE id = $product") } }
        }
    }

    // ── Migrations ──

    @Test
    fun aMigrationRunsThroughAClosedGate() = runBlocking {
        val product = product()
        close()
        val migration = LicenseWriteGate.lifting(object : Migration(62, 63) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE products SET name = 'Migrated' WHERE id = $product")
            }
        })
        assertEquals(62, migration.startVersion)
        assertEquals(63, migration.endVersion)

        migration.migrate(sql)

        assertEquals("Migrated", db.productDao().getProductById(product)!!.name)
        // The next open builds them again; until then the gate has no triggers
        assertEquals(0L, sql.long("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name GLOB 'trg_*_license_*'"))
        LicenseWriteGate.install(sql)
        assertRefused("products") { sql.execSQL("UPDATE products SET name = 'Again' WHERE id = $product") }
    }

    // ── Helpers ──

    private fun close() = LicenseWriteGate.write(sql, open = false)

    private fun assertRefused(what: String, write: () -> Unit) {
        try {
            write()
            fail("$what: the write went through a closed gate")
        } catch (e: Exception) {
            assertTrue("$what: $e", LicenseWriteGate.isRefusal(e))
            if (e is SQLiteConstraintException) assertTrue("$what: ${e.message}", e.message!!.contains(LicenseWriteGate.REFUSAL))
        }
    }

    private suspend fun product(stock: Double = 0.0): Int =
        repository.addProduct(
            mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0, "purchase_price" to 95.0, "stock" to stock)
        ).newId()

    private suspend fun client(): Int = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()

    private fun line(productId: Int, quantity: Double) =
        mapOf("product_id" to productId, "quantity" to quantity, "unit_price" to 110.0)

    private fun transfer(productId: Int, quantity: Double, direction: String): Map<String, Any?> =
        mapOf("product_id" to productId, "quantity" to quantity, "direction" to direction)

    private fun SupportSQLiteDatabase.long(query: String): Long =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getLong(0) }

    private fun Map<String, Any>.newId(): Int = (this["id"] as Number).toInt()

    private companion object {
        const val TEST_DB = "license-write-gate-test"
    }
}
