package com.distrigo.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.model.RetourFournisseurMotifs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A supplier return is only for goods the supplier takes back (FIN-03; owner, 2026-11-10): each motif offered lowers what
 * is owed by the goods' value and takes them out of the dépôt, with no perte; goods refused are a perte, never a return.
 */
@RunWith(AndroidJUnit4::class)
class RetourFournisseurMotifTest {

    private lateinit var db: AppDatabase
    private lateinit var products: ProductRepository
    private lateinit var retours: RetourFournisseurRepository

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        products = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        retours = RetourFournisseurRepository(db)
    }

    @After
    fun close() = db.close()

    /** 2 cartons at 95: 190 less owed, 2 fewer in the dépôt, nothing lost — for every motif offered. */
    @Test
    fun everyMotifOfferedIsCreditedAndTakesNoLoss() = runBlocking {
        for (motif in RetourFournisseurMotifs.ALL.map { it.id }) {
            val id = product()
            val supplier = supplier()

            assertNull(motif, retours.createRetour(supplier, "2026-11-10", motif, null, listOf(line(id, 2.0)))["error"])

            assertEquals(motif, -190.0, db.supplierDao().getSupplierById(supplier)!!.balance, 0.001)
            assertEquals(motif, 18.0, db.productDao().getProductById(id)!!.stock, 0.0001)
        }
        assertEquals(0L, count("SELECT COUNT(*) FROM pertes"))
    }

    /** The two « refusé (perte) » motifs are no longer offered, and a return given one is refused with nothing written. */
    @Test
    fun goodsTheSupplierRefusedAreNotAReturn() = runBlocking {
        val id = product()
        val supplier = supplier()
        assertTrue(RetourFournisseurMotifs.ALL.none { it.perteType != null })

        for (motif in RetourFournisseurMotifs.RETIRED.map { it.id }) {
            assertNotNull(motif, retours.createRetour(supplier, "2026-11-10", motif, null, listOf(line(id, 2.0)))["error"])
            // Given on a line only, too
            assertNotNull(motif, retours.createRetour(supplier, "2026-11-10", null, null, listOf(line(id, 2.0) + ("motif" to motif)))["error"])
        }

        assertTrue(db.retourFournisseurDao().getRetoursForSupplier(supplier).isEmpty())
        assertEquals(0.0, db.supplierDao().getSupplierById(supplier)!!.balance, 0.001)
        assertEquals(20.0, db.productDao().getProductById(id)!!.stock, 0.0001)
        assertEquals(0L, count("SELECT COUNT(*) FROM pertes"))
    }

    // ── helpers ──

    private var names = 0

    private suspend fun product(): Int =
        (products.addProduct(mapOf("name" to "Lait Candia 1L ${++names}", "selling_price" to 110.0, "purchase_price" to 95.0, "stock" to 20.0))["id"] as Number).toInt()

    private suspend fun supplier(): Int = (products.addSupplier(mapOf("name" to "Laiterie ${++names}"))["id"] as Number).toInt()

    private fun count(query: String): Long = db.openHelper.writableDatabase.query(query).use { it.moveToFirst(); it.getLong(0) }

    private fun line(productId: Int, quantity: Double): Map<String, Any?> = mapOf("product_id" to productId, "quantity" to quantity)
}
