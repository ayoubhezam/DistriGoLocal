package com.distrigo.app.data.local.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.entity.InventoryItemEntity
import com.distrigo.app.data.local.entity.TourneeClientEntity
import com.distrigo.app.data.repository.InventoryRepository
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A product is counted once per inventory session and a client planned once per tournée — refused
 * politely by the repositories, and refused outright by the database for anything that gets past them.
 *
 * In memory, so nothing touches the app's database on the device.
 */
@RunWith(AndroidJUnit4::class)
class UniqueRulesTest {

    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var repository: ProductRepository
    private lateinit var inventory: InventoryRepository

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        sql = db.openHelper.writableDatabase
        repository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        inventory = InventoryRepository(db)
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun aSecondScanOfTheSameProductIsRefused() = runBlocking {
        val product = product()
        val session = inventory.getOrCreateActiveSession().id

        assertEquals(null, inventory.recordScan(session, product, 38.0)["error"])
        assertEquals("Ce produit a déjà été scanné dans cette session", inventory.recordScan(session, product, 35.0)["error"])

        assertEquals(1L, sql.long("SELECT COUNT(*) FROM inventory_items"))
        assertEquals(38.0, db.productDao().getProductById(product)!!.stock, 0.0)
    }

    /** Two scans sent at once — the double tap the old check could not stop — record one. */
    @Test
    fun twoScansAtOnceRecordOne() = runBlocking {
        val product = product()
        val session = inventory.getOrCreateActiveSession().id

        val results = List(2) { async(Dispatchers.IO) { inventory.recordScan(session, product, 38.0) } }.awaitAll()

        assertEquals(1, results.count { it["error"] == null })
        assertEquals(1, results.count { it["error"] == "Ce produit a déjà été scanné dans cette session" })
        assertEquals(1L, sql.long("SELECT COUNT(*) FROM inventory_items"))
        assertEquals(38.0, db.productDao().getProductById(product)!!.stock, 0.0)
    }

    @Test
    fun theDatabaseRefusesADuplicateScan() = runBlocking {
        val product = product()
        val session = inventory.getOrCreateActiveSession().id
        inventory.recordScan(session, product, 38.0)

        try {
            db.inventoryDao().insertItem(
                InventoryItemEntity(
                    session_id = session, product_id = product, product_name = "Lait Candia 1L", product_image_uri = null,
                    qte_systeme = 38.0, qte_physique = 35.0, ecart = -3.0, purchase_price_snapshot = 95.0,
                    valeur_ecart = -285.0, created_at = "2026-09-17T10:00:00Z"
                )
            )
            fail("A second item for the same product in the same session should be refused")
        } catch (_: SQLiteConstraintException) {
        }
    }

    @Test
    fun theSameProductCanBeCountedInAnotherSession() = runBlocking {
        val product = product()
        val first = inventory.getOrCreateActiveSession().id
        inventory.recordScan(first, product, 38.0)
        inventory.finishSession(first)
        val second = inventory.getOrCreateActiveSession().id

        assertEquals(null, inventory.recordScan(second, product, 36.0)["error"])
        assertEquals(2L, sql.long("SELECT COUNT(*) FROM inventory_items"))
    }

    @Test
    fun aClientIsPlannedOncePerTournee() = runBlocking {
        val tournee = tournee("Souk Ahras centre")
        val client = client("Épicerie El Amel")
        val other = client("Supérette Nour")

        repository.addClientsToTournee(tournee, listOf(client, client, other))
        repository.addClientsToTournee(tournee, listOf(client))

        assertEquals(listOf(client, other), db.tourneeClientDao().getForTournee(tournee).map { it.client_id })
        assertEquals(listOf(0, 1), db.tourneeClientDao().getForTournee(tournee).map { it.order_index })
    }

    @Test
    fun addingTheSameClientsAtOnceAddsThemOnce() = runBlocking {
        val tournee = tournee("Souk Ahras centre")
        val clients = listOf(client("Épicerie El Amel"), client("Supérette Nour"))

        List(3) { async(Dispatchers.IO) { repository.addClientsToTournee(tournee, clients) } }.awaitAll()

        assertEquals(clients, db.tourneeClientDao().getForTournee(tournee).map { it.client_id })
    }

    @Test
    fun theDatabaseRefusesADuplicateTourneeClientButNotAcrossTournees() = runBlocking {
        val first = tournee("Souk Ahras centre")
        val client = client("Épicerie El Amel")
        repository.addClientsToTournee(first, listOf(client))

        try {
            db.tourneeClientDao().insertAll(
                listOf(TourneeClientEntity(tournee_id = first, client_id = client, status = "a_visiter", order_index = 5, visited_at = null))
            )
            fail("The same client twice in one tournée should be refused")
        } catch (_: SQLiteConstraintException) {
        }

        repository.closeTournee(first)
        val second = tournee("Souk Ahras nord")
        repository.addClientsToTournee(second, listOf(client))
        assertEquals(2L, sql.long("SELECT COUNT(*) FROM tournee_clients WHERE client_id = $client"))
    }

    // ── helpers ──

    private suspend fun product(): Int =
        (repository.addProduct(mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0, "purchase_price" to 95.0, "stock" to 40.0))["id"] as Number).toInt()

    private suspend fun client(name: String): Int = (repository.addClient(mapOf("name" to name))["id"] as Number).toInt()

    private suspend fun tournee(name: String): Int {
        repository.createTournee(name, "Souk Ahras", "Souk Ahras", null)
        return db.tourneeDao().getOpenTournee()!!.id
    }

    private fun SupportSQLiteDatabase.long(query: String): Long =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getLong(0) }
}
