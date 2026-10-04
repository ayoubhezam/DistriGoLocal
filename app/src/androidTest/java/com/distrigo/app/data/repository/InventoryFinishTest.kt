package com.distrigo.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.entity.mouvement.StockMovementEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Closing a count, with the products it did not reach put to zero or kept. On a database with the
 * app's triggers, so stock is what the ledger makes it: product 1 holds 10 — 7 at the dépôt, 3 on a
 * camion — and is not counted; product 2 holds 5 and is counted at 4; product 3 holds nothing.
 */
@RunWith(AndroidJUnit4::class)
class InventoryFinishTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var repository: InventoryRepository

    @Before
    fun open(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        repository = InventoryRepository(db)
        val sql = db.openHelper.writableDatabase
        listOf(1, 2, 3).forEach { id ->
            sql.execSQL(
                "INSERT INTO products (id, name, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, " +
                    "has_expiry, camion_stock, uuid) VALUES ($id, 'P$id', 120.0, 100.0, 0, 0, 'pièce', 0, 1, 0, 0, 'u-p-$id')"
            )
        }
        stock(1, 7.0, "depot"); stock(1, 3.0, "camion")
        stock(2, 5.0, "depot")
        Unit
    }

    @After
    fun close() = db.close()

    private suspend fun stock(product: Int, quantity: Double, where: String) = db.stockMovementDao().insert(
        StockMovementEntity(
            product_id = product, product_name = "P$product", type = "achat", direction = "entree", quantity = quantity,
            emplacement = where, source_label = "test", source_type = "purchase_order", source_id = 0,
            unit_price = 100.0, total_value = quantity * 100.0, user_name = null, note = null,
            created_at = "2026-10-01T09:00:00Z"
        )
    )

    private suspend fun stockOf(id: Int) = db.productDao().getProductById(id)!!.let { it.stock to it.camion_stock }

    @Test
    fun zeroingEmptiesTheDepotOfWhatWasNotCountedAndLeavesTheCamions(): Unit = runBlocking {
        assertEquals(10.0 to 3.0, stockOf(1))
        val session = repository.getOrCreateActiveSession()
        repository.recordScan(session.id, 2, 4.0)

        val result = repository.finishSession(session.id, zeroUncounted = true)

        assertEquals(1, result["zeroed"])
        assertEquals(3.0 to 3.0, stockOf(1))          // the dépôt's 7 gone, the camion's 3 kept
        val line = db.inventoryDao().getItemForSessionAndProduct(session.id, 1)!!
        assertEquals(listOf(10.0, 3.0, -7.0, -700.0), listOf(line.qte_systeme, line.qte_physique, line.ecart, line.valeur_ecart))
        assertEquals(4.0 to 0.0, stockOf(2))          // counted: its own count stands
        assertNull(db.inventoryDao().getItemForSessionAndProduct(session.id, 3))   // nothing to zero
        assertEquals("completed", db.inventoryDao().getSessionById(session.id)!!.status)
    }

    @Test
    fun keepingLeavesWhatWasNotCountedAsItWas(): Unit = runBlocking {
        val session = repository.getOrCreateActiveSession()
        repository.recordScan(session.id, 2, 4.0)

        // Dated as picked on the summary, not as confirmed.
        val result = repository.finishSession(session.id, zeroUncounted = false, at = "2026-09-30T10:00:00Z")

        assertEquals(0, result["zeroed"])
        assertEquals("2026-09-30T10:00:00Z", db.inventoryDao().getSessionById(session.id)!!.completed_at)
        assertEquals(10.0 to 3.0, stockOf(1))
        assertNull(db.inventoryDao().getItemForSessionAndProduct(session.id, 1))
        assertEquals("completed", db.inventoryDao().getSessionById(session.id)!!.status)
    }
}
