package com.distrigo.app.data.repository

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.entity.BusinessSettingsEntity
import com.distrigo.app.data.model.CostMethod
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A product's cost follows the bons received as the setting says (FIN-04; owner, 2026-11-10): weighted average by
 * default, the last purchase price, or only by hand. A change of setting applies from the next receipt; undoing a
 * receipt puts back the cost it moved, unless something moved it since.
 */
@RunWith(AndroidJUnit4::class)
class PurchaseCostTest {

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
    fun close() = db.close()

    /** The default: 100 held at 50, 100 received at 60 → 55. */
    @Test
    fun byDefaultTheCostIsTheWeightedAverage() = runBlocking {
        val id = product(stock = 100.0, cost = 50.0)
        receive(bon(id to (100.0 to 60.0)))
        assertEquals(55.0, cost(id), 0.0)
    }

    /** A product on two lines of a bon: the second averaged on top of the first. */
    @Test
    fun twoLinesOfOneProductAverageInTurn() = runBlocking {
        val id = product(stock = 100.0, cost = 50.0)
        receive(bon(id to (100.0 to 60.0), id to (200.0 to 70.0)))
        // 100 at 50 + 100 at 60 = 200 at 55; + 200 at 70 = 400 at 62,50
        assertEquals(62.5, cost(id), 0.0)
        assertEquals(400.0, db.productDao().getProductById(id)!!.stock, 0.0001)
    }

    /** Last purchase price, then by hand; a change of setting leaves a cost already given alone. */
    @Test
    fun eachMethodFromTheNextReceiptOn() = runBlocking {
        val id = product(stock = 100.0, cost = 50.0)
        receive(bon(id to (100.0 to 60.0)))
        assertEquals(55.0, cost(id), 0.0)

        method(CostMethod.LAST_PURCHASE)
        assertEquals(55.0, cost(id), 0.0)
        receive(bon(id to (10.0 to 64.0)))
        assertEquals(64.0, cost(id), 0.0)

        method(CostMethod.MANUAL)
        receive(bon(id to (10.0 to 80.0)))
        assertEquals(64.0, cost(id), 0.0)

        // By hand, whatever the method
        repository.updateProduct(id, mapOf("purchase_price" to 66.0))
        assertEquals(66.0, cost(id), 0.0)
    }

    /** Undone, reopened or deleted: the receipt's cost goes back, and receiving again gives it again. */
    @Test
    fun undoingAReceiptPutsTheCostBack() = runBlocking {
        val id = product(stock = 100.0, cost = 50.0)

        val swiped = bon(id to (100.0 to 60.0))
        receive(swiped)
        repository.undoReceivePurchaseOrder(swiped)
        assertEquals(50.0, cost(id), 0.0)

        receive(swiped)
        assertEquals(55.0, cost(id), 0.0)
        repository.reopenPurchaseOrder(swiped)
        assertEquals(50.0, cost(id), 0.0)

        receive(swiped)
        repository.deletePurchaseOrder(swiped)
        assertEquals(50.0, cost(id), 0.0)
        assertEquals(100.0, db.productDao().getProductById(id)!!.stock, 0.0001)
    }

    /** Two bons received, the first reopened: the cost the second gave stays; a cost edited by hand stays too. */
    @Test
    fun undoLeavesACostMovedSince() = runBlocking {
        val id = product(stock = 100.0, cost = 50.0)
        val first = bon(id to (100.0 to 60.0))
        receive(first)
        receive(bon(id to (200.0 to 70.0)))
        assertEquals(62.5, cost(id), 0.0)
        repository.reopenPurchaseOrder(first)
        assertEquals(62.5, cost(id), 0.0)

        val other = product(stock = 10.0, cost = 100.0)
        val third = bon(other to (10.0 to 120.0))
        receive(third)
        repository.updateProduct(other, mapOf("purchase_price" to 99.0))
        repository.undoReceivePurchaseOrder(third)
        assertEquals(99.0, cost(other), 0.0)
    }

    // ── helpers ──

    private var names = 0

    private suspend fun product(stock: Double, cost: Double): Int =
        (repository.addProduct(mapOf("name" to "Lait Candia 1L ${++names}", "selling_price" to 110.0, "purchase_price" to cost, "stock" to stock))["id"] as Number).toInt()

    private suspend fun cost(id: Int): Double = db.productDao().getProductById(id)!!.purchase_price

    private suspend fun method(method: CostMethod) {
        db.businessSettingsDao().insertIfAbsent(BusinessSettingsEntity(business_name = null, business_phone = null, logo_ref = null))
        db.businessSettingsDao().updateCostMethod(method.key)
    }

    /** A pending bon of [lines] (product to (quantity to unit cost)); its id. */
    private suspend fun bon(vararg lines: Pair<Int, Pair<Double, Double>>): Int {
        val supplier = (repository.addSupplier(mapOf("name" to "Laiterie ${++names}"))["id"] as Number).toInt()
        repository.createPurchaseOrder(
            mapOf(
                "supplier_id" to supplier, "montant_paye" to 0.0,
                "items" to lines.map { (id, line) -> mapOf("product_id" to id, "quantity" to line.first, "unit_cost" to line.second) }
            )
        )
        return query("SELECT MAX(id) FROM purchase_orders").toInt()
    }

    private suspend fun receive(bon: Int) {
        repository.receivePurchaseOrder(bon)
    }

    private fun query(q: String): Long = sql.query(q).use { it.moveToFirst(); it.getLong(0) }
}
