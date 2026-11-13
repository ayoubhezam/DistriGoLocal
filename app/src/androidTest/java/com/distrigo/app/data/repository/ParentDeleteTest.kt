package com.distrigo.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.core.text.UserFacingException
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What a deleted document leaves behind (DB-01, audit 2026-11-09): nothing only it used, and nothing others rely on. */
@RunWith(AndroidJUnit4::class)
class ParentDeleteTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ProductRepository

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        repo = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
    }

    @After
    fun close() = db.close()

    /** A bon's prices: one per line, replaced when it is edited, gone when it is deleted — another bon's stay. */
    @Test
    fun aBonsPricesGoWithIt() = runBlocking {
        val milk = (repo.addProduct(mapOf("name" to "Lait", "selling_price" to 120.0, "purchase_price" to 90.0))["id"] as Number).toInt()
        val supplier = (repo.addSupplier(mapOf("name" to "Soummam"))["id"] as Number).toInt()
        val kept = bon(supplier, milk, 95.0)
        val gone = bon(supplier, milk, 100.0)
        assertEquals(listOf(95.0, 100.0), prices(milk).sorted())

        // Edited twice: still one price for it, the last one
        repeat(2) { n ->
            repo.updatePurchaseOrder(gone, mapOf("montant_paye" to 0.0, "items" to listOf(line(milk, 101.0 + n))))
        }
        assertEquals(listOf(95.0, 102.0).sorted(), prices(milk).sorted())

        repo.deletePurchaseOrder(gone)
        assertEquals(listOf(95.0), prices(milk))
        assertEquals(listOf(kept), db.purchaseDao().getPriceHistoryForProduct(milk).map { it.purchase_order_id })
    }

    /** A sale goods were returned from is not deleted: its returns would lose their sale. */
    @Test
    fun aSaleWithReturnsIsKept() = runBlocking {
        val milk = (repo.addProduct(mapOf("name" to "Lait", "selling_price" to 120.0, "purchase_price" to 90.0, "stock" to 20.0))["id"] as Number).toInt()
        val client = (repo.addClient(mapOf("name" to "MOSTAFA"))["id"] as Number).toInt()
        val sold = db.recordedSale(client, "depot", milk to 3.0)
        RetourClientRepository(db).createRetour(client, sold.venteId, "depot", "2026-11-13", "Client insatisfait", null, listOf(sold.returning(0, 1.0)))

        try {
            repo.deleteVente(sold.venteId)
            fail("a sale with returns was deleted")
        } catch (e: UserFacingException) {
            // refused, as a tournée with sales is
        }
        assertNotNull(db.venteDao().getVenteById(sold.venteId))
        assertTrue(db.retourClientDao().getRetoursForClient(client).isNotEmpty())
    }

    private suspend fun bon(supplier: Int, product: Int, cost: Double): Int {
        repo.createPurchaseOrder(mapOf("supplier_id" to supplier, "montant_paye" to 0.0, "items" to listOf(line(product, cost))))
        return db.purchaseDao().getAllOrders().maxOf { it.id }
    }

    private fun line(product: Int, cost: Double) = mapOf("product_id" to product, "quantity" to 1.0, "unit_cost" to cost)

    private suspend fun prices(product: Int): List<Double> = db.purchaseDao().getPriceHistoryForProduct(product).map { it.unit_cost }
}
