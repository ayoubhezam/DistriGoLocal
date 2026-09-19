package com.distrigo.app.data.local.database

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * `updated_at` moves when a user changes a row, and only then, whatever path wrote it.
 *
 * Writes go through the repository, the way the app makes them. Before each edit the row's
 * `updated_at` is pinned to a known old value, so "stamped" and "left alone" are exact comparisons
 * rather than races against the clock.
 *
 * In memory, so nothing touches the app's database on the device.
 */
@RunWith(AndroidJUnit4::class)
class ChangeTrackingTest {

    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var repository: ProductRepository

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        sql = db.openHelper.writableDatabase
        repository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
    }

    @After
    fun close() {
        db.close()
    }

    /** Every business table has its trigger, comparing every column but the ids, the stamp and the caches. */
    @Test
    fun everyBusinessTableIsTracked() {
        val tracked = UpdatedAtTriggers.trackedTables(sql)
        assertEquals(37, tracked.size)
        assertTrue(tracked.none { it.endsWith("_drafts") })

        for (table in tracked) {
            val compared = UpdatedAtTriggers.comparedColumns(sql, table)
            val expected = columns(table) - setOf("id", "uuid", "updated_at", "version", "origin_device_id", "numero") -
                UpdatedAtTriggers.DERIVED_COLUMNS[table].orEmpty()
            assertEquals(table, expected, compared)
            assertEquals(
                table,
                UpdatedAtTriggers.triggerSql(table, compared, versioned = "version" in columns(table)),
                storedTriggerSql(sql, UpdatedAtTriggers.triggerName(table)),
            )
        }
    }

    /** Opening again finds every trigger already current and rewrites none of them. */
    @Test
    fun reinstallingChangesNothing() {
        val before = schemaVersion()
        UpdatedAtTriggers.install(sql)
        TombstoneTriggers.install(sql)
        assertEquals(before, schemaVersion())
    }

    @Test
    fun editingAClientStampsIt() = runBlocking {
        val id = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        pin("clients", id, OLD)

        repository.updateClient(id, mapOf("name" to "Épicerie El Amel 2"))

        val stamped = db.clientDao().getClientById(id)!!.updated_at
        assertTrue("$stamped is not close to now", abs(stamped - System.currentTimeMillis()) < 60_000)
    }

    @Test
    fun savingAClientUnchangedLeavesItAlone() = runBlocking {
        val id = repository.addClient(mapOf("name" to "Épicerie El Amel", "phone" to "0550 12 34 56")).newId()
        pin("clients", id, OLD)

        repository.updateClient(id, mapOf("name" to "Épicerie El Amel", "phone" to "0550 12 34 56"))

        assertEquals(OLD, db.clientDao().getClientById(id)!!.updated_at)
    }

    /**
     * A sale moves the product's stock and the client's balance, and a payment moves the balance
     * again. Those are caches, not edits: neither row is stamped. The sale itself is a new row.
     */
    @Test
    fun stockAndBalanceChangesAreNotEdits() = runBlocking {
        val productId = repository.addProduct(
            mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0, "stock" to 40.0)
        ).newId()
        val clientId = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        pin("products", productId, OLD)
        pin("clients", clientId, OLD)

        repository.createVente(
            clientId = clientId, tourneeId = null, source = "depot",
            items = listOf(mapOf("product_id" to productId, "quantity" to 10.0, "unit_price" to 110.0)),
            note = null, montantPaye = 0.0
        )
        repository.addClientPayment(clientId, 300.0, null)

        val product = db.productDao().getProductById(productId)!!
        val client = db.clientDao().getClientById(clientId)!!
        assertEquals(30.0, product.stock, 0.001)
        assertEquals(800.0, client.balance, 0.001)
        assertEquals(OLD, product.updated_at)
        assertEquals(OLD, client.updated_at)

        val vente = db.venteDao().getVentesWithDetails(clientId).single().vente
        assertTrue(abs(vente.updated_at - System.currentTimeMillis()) < 60_000)
    }

    /** A real product edit, next to the stock it carries, is stamped. */
    @Test
    fun editingAProductPriceStampsIt() = runBlocking {
        val id = repository.addProduct(mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0)).newId()
        pin("products", id, OLD)

        repository.updateProduct(id, mapOf("selling_price" to 115.0))

        assertNotEquals(OLD, db.productDao().getProductById(id)!!.updated_at)
    }

    /** `@Query` UPDATEs are covered too, not only `@Update` on an entity: closing a tournée. */
    @Test
    fun queryUpdatesAreStamped() = runBlocking {
        repository.createTournee("Souk Ahras centre", "Souk Ahras", "Souk Ahras", null)
        val id = db.tourneeDao().getOpenTournee()!!.id
        pin("tournees", id, OLD)

        repository.closeTournee(id)

        assertNotEquals(OLD, db.tourneeDao().getTourneeById(id)!!.updated_at)
    }

    /** A phone whose clock is behind the last stamp still moves `updated_at` forward. */
    @Test
    fun stampNeverGoesBackwards() = runBlocking {
        val id = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        val future = System.currentTimeMillis() + 86_400_000L
        pin("clients", id, future)

        repository.updateClient(id, mapOf("name" to "Épicerie El Amel 2"))

        assertEquals(future + 1, db.clientDao().getClientById(id)!!.updated_at)
    }

    /** A write that sets `updated_at` itself keeps its value, as a sync applying a remote row will. */
    @Test
    fun explicitUpdatedAtWins() = runBlocking {
        val id = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        pin("clients", id, OLD)

        sql.execSQL("UPDATE clients SET name = 'Supérette Nour', updated_at = 5000 WHERE id = $id")

        val client = db.clientDao().getClientById(id)!!
        assertEquals("Supérette Nour", client.name)
        assertEquals(5000L, client.updated_at)
    }

    /** Sets `updated_at` directly; the trigger leaves a write that changes it alone. */
    private fun pin(table: String, id: Int, value: Long) {
        sql.execSQL("UPDATE `$table` SET updated_at = $value WHERE id = $id")
    }

    private fun columns(table: String): List<String> =
        sql.query("PRAGMA table_info(`$table`)").use { c ->
            val name = c.getColumnIndexOrThrow("name")
            buildList { while (c.moveToNext()) add(c.getString(name)) }
        }

    private fun schemaVersion(): Int =
        sql.query("PRAGMA schema_version").use { it.moveToFirst(); it.getInt(0) }

    private fun Map<String, Any>.newId(): Int = (this["id"] as Number).toInt()

    private companion object {
        const val OLD = 1_000L
    }
}
