package com.distrigo.app.data.local.database

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.repository.InventoryRepository
import com.distrigo.app.data.repository.PerteRepository
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.RetourClientRepository
import com.distrigo.app.data.repository.RetourFournisseurRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `stock` and `camion_stock` follow the ledger through every way the app moves stock.
 *
 * Each case drives the real repositories, checks the numbers a user would see, and ends by checking
 * the invariant itself — for every product, both columns equal what the movements and transfer lines
 * add up to — so a path that changes stock without recording why fails here.
 *
 * In memory, so nothing touches the app's database on the device.
 */
@RunWith(AndroidJUnit4::class)
class StockLedgerTest {

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
        assertLedgerHolds()
        db.close()
    }

    @Test
    fun ledgerTriggersAreInstalled() {
        for ((name, expected) in StockLedgerTriggers.triggers()) {
            assertEquals(name, expected, storedTriggerSql(sql, name))
        }
    }

    @Test
    fun aProductCreatedWithStockGetsItAsAnAdjustment() = runBlocking {
        val withStock = product(40.0)
        val without = repository.addProduct(mapOf("name" to "Lben Soummam", "selling_price" to 90.0)).newId()

        assertStock(withStock, total = 40.0, camion = 0.0)
        assertStock(without, total = 0.0, camion = 0.0)
        assertEquals(
            listOf(Triple("ajustement", "entree", 40.0)),
            movements(withStock).map { Triple(it.first, it.second, it.third) }
        )
        assertEquals("Stock initial", sql.text("SELECT source_label FROM stock_movements WHERE product_id = $withStock"))
        assertTrue(movements(without).isEmpty())
    }

    @Test
    fun typedStockBecomesAnAdjustment() = runBlocking {
        val id = product(40.0)

        repository.updateProduct(id, mapOf("stock" to 35.0))
        assertStock(id, total = 35.0, camion = 0.0)
        assertEquals(Triple("ajustement", "sortie", 5.0), movements(id).last())

        repository.updateProduct(id, mapOf("name" to "Lait Candia 1L demi-écrémé"))
        assertStock(id, total = 35.0, camion = 0.0)
        assertEquals(2, movements(id).size)
    }

    /** A product row read before a sale and saved after it no longer puts the sale's stock back. */
    @Test
    fun aStaleOrDirectWriteCannotChangeStock() = runBlocking {
        val id = product(40.0)
        val client = client()
        val stale = db.productDao().getProductById(id)!!

        sale(client, id, 10.0, "depot")
        db.productDao().updateProduct(stale.copy(name = "Lait Candia 1L (renamed)"))
        assertStock(id, total = 30.0, camion = 0.0)
        assertEquals("Lait Candia 1L (renamed)", db.productDao().getProductById(id)!!.name)

        db.productDao().updateProduct(stale.copy(stock = 999.0, camion_stock = 999.0))
        assertStock(id, total = 30.0, camion = 0.0)
    }

    @Test
    fun receivingReopeningAndDeletingABon() = runBlocking {
        val id = product(0.0)
        val supplier = repository.addSupplier(mapOf("name" to "Laiterie Soummam")).newId()
        repository.createPurchaseOrder(
            mapOf(
                "supplier_id" to supplier, "montant_paye" to 0.0,
                "items" to listOf(mapOf(
                    "product_id" to id, "quantity" to 24.0, "unit_cost" to 95.0,
                    "has_expiry" to true, "expiry_date" to "2026-12-31",
                )),
            )
        )
        val order = db.purchaseDao().getAllOrders().single().id

        repository.receivePurchaseOrder(order)
        assertStock(id, total = 24.0, camion = 0.0)
        assertEquals("2026-12-31", db.productDao().getProductById(id)!!.expiry_date)

        repository.reopenPurchaseOrder(order)
        assertStock(id, total = 0.0, camion = 0.0)

        repository.receivePurchaseOrder(order)
        repository.deletePurchaseOrder(order)
        assertStock(id, total = 0.0, camion = 0.0)
    }

    /** A chargement moves stock between the dépôt and the camion without changing the total, and records no movement. */
    @Test
    fun chargementsMoveOnlyTheCamionShare() = runBlocking {
        val id = product(40.0)

        repository.createChargement(null, listOf(mapOf("product_id" to id, "quantity" to 10.0, "direction" to "vers_camion")))
        assertStock(id, total = 40.0, camion = 10.0)

        repository.createChargement(null, listOf(mapOf("product_id" to id, "quantity" to 3.0, "direction" to "vers_depot")))
        assertStock(id, total = 40.0, camion = 7.0)

        repository.deleteChargement(db.chargementDao().getAllChargements().maxBy { it.id }.id)
        assertStock(id, total = 40.0, camion = 10.0)
        assertEquals(1, movements(id).size)   // only the initial stock
    }

    @Test
    fun camionSalesTheirEditsAndTheCamionCheck() = runBlocking {
        val id = product(40.0)
        val client = client()
        repository.createChargement(null, listOf(mapOf("product_id" to id, "quantity" to 10.0, "direction" to "vers_camion")))

        val vente = sale(client, id, 4.0, "camion")
        assertStock(id, total = 36.0, camion = 6.0)

        repository.updateVente(vente, client, listOf(line(id, 6.0)), null, 0.0)
        assertStock(id, total = 34.0, camion = 4.0)

        // With this sale's own 6 put back the camion holds 10, so 11 is refused, and nothing changes.
        try {
            repository.updateVente(vente, client, listOf(line(id, 11.0)), null, 0.0)
            fail("A camion sale larger than the camion's stock should be refused")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().startsWith("Stock insuffisant"))
        }
        assertStock(id, total = 34.0, camion = 4.0)

        repository.updateVente(vente, client, listOf(line(id, 10.0)), null, 0.0)
        assertStock(id, total = 30.0, camion = 0.0)

        repository.deleteVente(vente)
        assertStock(id, total = 40.0, camion = 10.0)
    }

    @Test
    fun aDepotSaleMayTakeStockBelowZero() = runBlocking {
        val id = product(2.0)
        sale(client(), id, 5.0, "depot")
        assertStock(id, total = -3.0, camion = 0.0)
    }

    @Test
    fun pertesTheirEditsAndDeletes() = runBlocking {
        val pertes = PerteRepository(db)
        pertes.seedDefaultPerteTypesIfNeeded()
        val type = db.perteDao().getAllPerteTypes().first().id
        val id = product(40.0)
        repository.createChargement(null, listOf(mapOf("product_id" to id, "quantity" to 10.0, "direction" to "vers_camion")))

        pertes.addPerte(type, id, 2.0, "camion", NOW, null, null)
        assertStock(id, total = 38.0, camion = 8.0)
        val perte = sql.long("SELECT id FROM pertes").toInt()

        val moved = pertes.updatePerte(perte, id, 3.0, "depot", NOW, null, null)
        assertEquals(moved.toString(), null, moved["error"])
        assertStock(id, total = 37.0, camion = 10.0)

        pertes.deletePerte(perte)
        assertStock(id, total = 40.0, camion = 10.0)
    }

    /**
     * A defective return comes back into the camion and straight out again as a perte; deleting it
     * used to throw, because it went through deletePerte, which refuses linked pertes.
     */
    @Test
    fun clientReturnsIncludingDefectiveOnesCanBeDeleted() = runBlocking {
        val retours = RetourClientRepository(db)
        val id = product(40.0)
        val client = client()

        retours.createRetour(client, null, "2026-09-16", "Client insatisfait", null, listOf(line(id, 2.0)))
        assertStock(id, total = 42.0, camion = 2.0)

        retours.createRetour(client, null, "2026-09-16", "Produit défectueux", null, listOf(line(id, 3.0)))
        assertStock(id, total = 42.0, camion = 2.0)
        val defective = db.retourClientDao().getRetoursForClient(client).single { it.motif == "Produit défectueux" }.id
        assertEquals(1, db.perteDao().getPertesBySource("retour_client", defective).size)

        retours.deleteRetour(defective)
        assertStock(id, total = 42.0, camion = 2.0)
        assertTrue(db.perteDao().getPertesBySource("retour_client", defective).isEmpty())

        retours.deleteRetour(db.retourClientDao().getRetoursForClient(client).single().id)
        assertStock(id, total = 40.0, camion = 0.0)
    }

    @Test
    fun supplierReturnsIncludingRefusedOnes() = runBlocking {
        val retours = RetourFournisseurRepository(db)
        val id = product(40.0)
        val supplier = repository.addSupplier(mapOf("name" to "Laiterie Soummam")).newId()

        retours.createRetour(supplier, "2026-09-16", "Produit défectueux — refusé (perte)", null, listOf(line(id, 5.0)))
        assertStock(id, total = 35.0, camion = 0.0)
        val retour = db.retourFournisseurDao().getRetoursForSupplier(supplier).single().id
        assertEquals(1, db.perteDao().getPertesBySource("retour_fournisseur", retour).size)

        retours.deleteRetour(retour)
        assertStock(id, total = 40.0, camion = 0.0)
    }

    /**
     * A scan brings stock to the count. Correcting or deleting it later keeps whatever moved since —
     * here a sale of 5 made after the scan — where the old absolute write erased it.
     */
    @Test
    fun inventoryScansAreAdjustmentsNotOverwrites() = runBlocking {
        val inventory = InventoryRepository(db)
        val id = product(40.0)
        val session = inventory.getOrCreateActiveSession().id

        inventory.recordScan(session, id, 38.0)
        assertStock(id, total = 38.0, camion = 0.0)

        sale(client(), id, 5.0, "depot")
        assertStock(id, total = 33.0, camion = 0.0)

        val item = db.inventoryDao().getItemsForSession(session).single().id
        inventory.updateScan(item, 35.0)
        assertStock(id, total = 30.0, camion = 0.0)

        inventory.deleteScan(item)
        assertStock(id, total = 35.0, camion = 0.0)
    }

    // ── helpers ──

    private suspend fun product(stock: Double): Int =
        repository.addProduct(
            mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0, "purchase_price" to 95.0, "stock" to stock)
        ).newId()

    private suspend fun client(): Int = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()

    private fun line(productId: Int, quantity: Double) =
        mapOf("product_id" to productId, "quantity" to quantity, "unit_price" to 110.0)

    private suspend fun sale(client: Int, productId: Int, quantity: Double, source: String): Int {
        repository.createVente(client, null, source, listOf(line(productId, quantity)), null, 0.0)
        return sql.long("SELECT MAX(id) FROM ventes").toInt()
    }

    private suspend fun assertStock(productId: Int, total: Double, camion: Double) {
        val product = db.productDao().getProductById(productId)!!
        assertEquals("stock", total, product.stock, 0.0001)
        assertEquals("camion_stock", camion, product.camion_stock, 0.0001)
    }

    private fun movements(productId: Int): List<Triple<String, String, Double>> =
        sql.query("SELECT type, direction, quantity FROM stock_movements WHERE product_id = $productId ORDER BY id").use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1), c.getDouble(2))) }
        }

    /** For every product, both columns equal their ledger. */
    private fun assertLedgerHolds() {
        sql.query(
            "SELECT p.id, p.stock, ${StockLedgerTriggers.totalSql("p.id")}, " +
                "p.camion_stock, ${StockLedgerTriggers.camionSql("p.id")} FROM products p"
        ).use { c ->
            while (c.moveToNext()) {
                assertEquals("stock of product ${c.getInt(0)}", c.getDouble(2), c.getDouble(1), 0.0)
                assertEquals("camion_stock of product ${c.getInt(0)}", c.getDouble(4), c.getDouble(3), 0.0)
            }
        }
    }

    private fun SupportSQLiteDatabase.long(query: String): Long =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getLong(0) }

    private fun SupportSQLiteDatabase.text(query: String): String =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getString(0) }

    private fun Map<String, Any>.newId(): Int = (this["id"] as Number).toInt()

    private companion object {
        const val NOW = "2026-09-16T10:00:00Z"
    }
}
