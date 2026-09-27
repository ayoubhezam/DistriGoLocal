package com.distrigo.app.data.repository

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A carton product moves by fractions — half a carton bought, loaded, sold, lost, sent back — and a
 * pièce product by whole units only: every write path refuses part of a piece, whatever a form sends.
 *
 * In memory, so nothing touches the app's database on the device.
 */
@RunWith(AndroidJUnit4::class)
class FractionalQuantityTest {

    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var repository: ProductRepository
    private var names = 0

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        sql = db.openHelper.writableDatabase
        repository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
    }

    @After
    fun close() = db.close()

    @Test
    fun halfACartonMovesThroughEveryFlow() = runBlocking {
        val id = product("carton", 0.0)
        val supplier = supplier()

        // Bought: 2.5 cartons.
        repository.createPurchaseOrder(
            mapOf("supplier_id" to supplier, "montant_paye" to 0.0,
                "items" to listOf(mapOf("product_id" to id, "quantity" to 2.5, "unit_cost" to 900.0, "nb_colis" to 2.5)))
        )
        repository.receivePurchaseOrder(sql.long("SELECT MAX(id) FROM purchase_orders").toInt())
        assertStock(id, total = 2.5, camion = 0.0)

        // Half a carton loaded, then sold from the camion.
        repository.createChargement(null, listOf(mapOf("product_id" to id, "quantity" to 0.5, "direction" to "vers_camion")))
        assertStock(id, total = 2.5, camion = 0.5)
        repository.createVente(client(), null, "camion", listOf(line(id, 0.5)), null, 0.0)
        assertStock(id, total = 2.0, camion = 0.0)

        // Half sold from the dépôt, half lost, half sent back.
        repository.createVente(client(), null, "depot", listOf(line(id, 0.5)), null, 0.0)
        val pertes = PerteRepository(db)
        pertes.seedDefaultPerteTypesIfNeeded()
        assertNull(pertes.addPerte(db.perteDao().getAllPerteTypes().first().id, id, 0.5, "depot", NOW, null, null)["error"])
        assertNull(RetourFournisseurRepository(db).createRetour(supplier, "2026-09-27", "Produit défectueux — refusé (perte)", null, listOf(line(id, 0.5)))["error"])
        assertStock(id, total = 0.5, camion = 0.0)
    }

    @Test
    fun partOfAPieceIsRefusedEverywhere() = runBlocking {
        val id = product("pièce", 10.0)
        val client = client()

        refused { repository.createVente(client, null, "depot", listOf(line(id, 1.5)), null, 0.0) }
        refused { repository.createChargement(null, listOf(mapOf("product_id" to id, "quantity" to 0.5, "direction" to "vers_camion"))) }
        refused {
            repository.createPurchaseOrder(
                mapOf("supplier_id" to supplier(), "montant_paye" to 0.0,
                    "items" to listOf(mapOf("product_id" to id, "quantity" to 12.5, "unit_cost" to 30.0, "nb_colis" to 0.5, "unite_par_colis" to 25)))
            )
        }

        val pertes = PerteRepository(db)
        pertes.seedDefaultPerteTypesIfNeeded()
        assertNotNull(pertes.addPerte(db.perteDao().getAllPerteTypes().first().id, id, 0.5, "depot", NOW, null, null)["error"])
        assertNotNull(RetourClientRepository(db).createRetour(client, null, "2026-09-27", "Client insatisfait", null, listOf(line(id, 0.5)))["error"])
        assertNotNull(RetourFournisseurRepository(db).createRetour(supplier(), "2026-09-27", "Produit défectueux — refusé (perte)", null, listOf(line(id, 0.5)))["error"])
        val inventory = InventoryRepository(db)
        assertNotNull(inventory.recordScan(inventory.getOrCreateActiveSession().id, id, 2.5)["error"])

        assertStock(id, total = 10.0, camion = 0.0)
        assertEquals(0L, sql.long("SELECT COUNT(*) FROM ventes"))

        // Whole units still go through, and half a colis that makes whole pieces too.
        repository.createVente(client, null, "depot", listOf(line(id, 2.0)), null, 0.0)
        repository.createPurchaseOrder(
            mapOf("supplier_id" to supplier(), "montant_paye" to 0.0,
                "items" to listOf(mapOf("product_id" to id, "quantity" to 15.0, "unit_cost" to 30.0, "nb_colis" to 0.5, "unite_par_colis" to 30)))
        )
        assertEquals(0.5, sql.double("SELECT nb_colis FROM purchase_order_items ORDER BY id DESC LIMIT 1"), 0.0)
        assertStock(id, total = 8.0, camion = 0.0)
    }

    /** A kg product is weighed to the gram, and its minimum can be a fraction too. */
    @Test
    fun kgIsWeighedToTheGram() = runBlocking {
        val id = product("kg", 20.0)
        repository.updateProduct(id, mapOf("min_stock" to 0.5))
        assertEquals(0.5, db.productDao().getProductById(id)!!.min_stock, 0.0)
        assertEquals("real", sql.text("SELECT typeof(min_stock) FROM products WHERE id = $id"))

        repository.createVente(client(), null, "depot", listOf(line(id, 1.25)), null, 0.0)
        repository.createVente(client(), null, "depot", listOf(line(id, 3.0005)), null, 0.0)   // to the gram
        assertStock(id, total = 15.749, camion = 0.0)
    }

    // ── helpers ──

    private suspend fun product(unit: String, stock: Double): Int =
        (repository.addProduct(
            mapOf("name" to "Produit ${++names}", "selling_price" to 100.0, "purchase_price" to 90.0, "stock" to stock, "unit_type" to unit)
        )["id"] as Number).toInt()

    private suspend fun client(): Int = (repository.addClient(mapOf("name" to "Client ${++names}"))["id"] as Number).toInt()

    private suspend fun supplier(): Int = (repository.addSupplier(mapOf("name" to "Fournisseur ${++names}"))["id"] as Number).toInt()

    private fun line(productId: Int, quantity: Double) =
        mapOf("product_id" to productId, "quantity" to quantity, "unit_price" to 100.0)

    private suspend fun refused(block: suspend () -> Unit) {
        try {
            block()
            fail("expected part of a piece to be refused")
        } catch (e: IllegalStateException) {
            assertTrue(e.message, e.message!!.contains("se compte à la pièce"))
        }
    }

    private suspend fun assertStock(productId: Int, total: Double, camion: Double) {
        val product = db.productDao().getProductById(productId)!!
        assertEquals("stock", total, product.stock, 0.0)
        assertEquals("camion_stock", camion, product.camion_stock, 0.0)
    }

    private fun SupportSQLiteDatabase.long(query: String): Long =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getLong(0) }

    private fun SupportSQLiteDatabase.text(query: String): String =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getString(0) }

    private fun SupportSQLiteDatabase.double(query: String): Double =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getDouble(0) }

    private companion object {
        const val NOW = "2026-09-27T10:00:00Z"
    }
}
