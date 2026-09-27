package com.distrigo.app.data.repository

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.entity.BusinessSettingsEntity
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
 * "Autoriser le stock négatif" off: no operation leaves a product's dépôt stock below zero, and each
 * refusal leaves the stock exactly as it was. On — the default — the dépôt may still go negative.
 *
 * Every operation that can take stock out of the dépôt is driven through its real repository. A
 * product already below zero (from before strict stock) must still be restockable, and editable
 * without being made worse.
 *
 * In memory, so nothing touches the app's database on the device.
 */
@RunWith(AndroidJUnit4::class)
class StrictStockTest {

    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var repository: ProductRepository
    private var products = 0

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
    fun negativeStockIsAllowedByDefault() = runBlocking {
        val id = product(3.0)
        repository.createVente(client(), null, "depot", listOf(line(id, 5.0)), null, 0.0)
        assertDepot(id, -2.0)
    }

    @Test
    fun aStrictDepotSaleCannotExceedTheDepot() = runBlocking {
        strict()
        val id = product(20.0)
        val client = client()

        refused { repository.createVente(client, null, "depot", listOf(line(id, 21.0)), null, 0.0) }
        assertDepot(id, 20.0)
        assertEquals(0L, sql.long("SELECT COUNT(*) FROM ventes"))

        repository.createVente(client, null, "depot", listOf(line(id, 20.0)), null, 0.0)
        assertDepot(id, 0.0)
    }

    /** Two lines of the same product are one demand on the dépôt. */
    @Test
    fun aStrictSaleCountsRepeatedLinesTogether() = runBlocking {
        strict()
        val id = product(10.0)
        refused { repository.createVente(client(), null, "depot", listOf(line(id, 6.0), line(id, 6.0)), null, 0.0) }
        assertDepot(id, 10.0)
    }

    /** An edit is judged by its result: the sale's own quantity is back in the dépôt while it is rewritten. */
    @Test
    fun aStrictSaleEditIsCheckedOnItsNetEffect() = runBlocking {
        strict()
        val id = product(10.0)
        val client = client()
        repository.createVente(client, null, "depot", listOf(line(id, 10.0)), null, 0.0)
        val vente = sql.long("SELECT id FROM ventes").toInt()

        repository.updateVente(vente, client, listOf(line(id, 10.0)), "prix corrigé", 0.0)
        assertDepot(id, 0.0)

        refused { repository.updateVente(vente, client, listOf(line(id, 11.0)), null, 0.0) }
        assertDepot(id, 0.0)
        assertEquals(10.0, sql.double("SELECT quantity FROM vente_items WHERE vente_id = $vente"), 0.0)

        repository.updateVente(vente, client, listOf(line(id, 4.0)), null, 0.0)
        assertDepot(id, 6.0)
    }

    /** A product already below zero can be restocked, but nothing can take it lower. */
    @Test
    fun aProductAlreadyNegativeCanBeRestockedButNotLowered() = runBlocking {
        val id = product(2.0)
        val client = client()
        repository.createVente(client, null, "depot", listOf(line(id, 5.0)), null, 0.0)
        assertDepot(id, -3.0)
        strict()

        refused { repository.createVente(client, null, "depot", listOf(line(id, 1.0)), null, 0.0) }
        assertDepot(id, -3.0)

        // Taking a line off the old sale only gives stock back.
        val vente = sql.long("SELECT id FROM ventes").toInt()
        repository.updateVente(vente, client, listOf(line(id, 4.0)), null, 0.0)
        assertDepot(id, -2.0)

        receivedBon(id, 12.0)
        assertDepot(id, 10.0)
    }

    @Test
    fun aStrictChargementCannotLoadMoreThanTheDepot() = runBlocking {
        strict()
        val id = product(10.0)

        refused { repository.createChargement(null, listOf(transfer(id, 11.0, "vers_camion"))) }
        assertDepot(id, 10.0)

        repository.createChargement(null, listOf(transfer(id, 10.0, "vers_camion")))
        assertDepot(id, 0.0)
        repository.createChargement(null, listOf(transfer(id, 4.0, "vers_depot")))
        assertDepot(id, 4.0)
    }

    /** Undoing a transfer back to the dépôt puts that stock in the camion again. */
    @Test
    fun deletingAnUnloadIsRefusedWhenTheDepotHasSoldIt() = runBlocking {
        strict()
        val id = product(10.0)
        repository.createChargement(null, listOf(transfer(id, 10.0, "vers_camion")))
        repository.createChargement(null, listOf(transfer(id, 5.0, "vers_depot")))
        val unload = sql.long("SELECT MAX(id) FROM chargements").toInt()
        repository.createVente(client(), null, "depot", listOf(line(id, 5.0)), null, 0.0)
        assertDepot(id, 0.0)

        refused { repository.deleteChargement(unload) }
        assertDepot(id, 0.0)
    }

    @Test
    fun aStrictDepotPerteCannotExceedTheDepot() = runBlocking {
        strict()
        val pertes = PerteRepository(db)
        pertes.seedDefaultPerteTypesIfNeeded()
        val type = db.perteDao().getAllPerteTypes().first().id
        val id = product(5.0)

        assertNotNull(pertes.addPerte(type, id, 6.0, "depot", NOW, null, null)["error"])
        assertDepot(id, 5.0)
        assertEquals(0L, sql.long("SELECT COUNT(*) FROM pertes"))

        assertNull(pertes.addPerte(type, id, 5.0, "depot", NOW, null, null)["error"])
        assertDepot(id, 0.0)

        val perte = sql.long("SELECT id FROM pertes").toInt()
        assertNotNull(pertes.updatePerte(perte, id, 6.0, "depot", NOW, null, null)["error"])
        assertDepot(id, 0.0)
        assertNull(pertes.updatePerte(perte, id, 5.0, "depot", NOW, "motif corrigé", null)["error"])
        assertDepot(id, 0.0)
    }

    /** A camion perte takes from the camion, not the dépôt: strict stock does not stop it. */
    @Test
    fun aCamionPerteIsNotADepotOperation() = runBlocking {
        strict()
        val pertes = PerteRepository(db)
        pertes.seedDefaultPerteTypesIfNeeded()
        val type = db.perteDao().getAllPerteTypes().first().id
        val id = product(10.0)
        repository.createChargement(null, listOf(transfer(id, 10.0, "vers_camion")))

        assertNull(pertes.addPerte(type, id, 4.0, "camion", NOW, null, null)["error"])
        assertDepot(id, 0.0)
    }

    @Test
    fun aStrictSupplierReturnCannotExceedTheDepot() = runBlocking {
        strict()
        val retours = RetourFournisseurRepository(db)
        val id = product(5.0)
        val supplier = repository.addSupplier(mapOf("name" to "Laiterie Soummam")).newId()

        val refusal = retours.createRetour(supplier, "2026-09-27", "Produit défectueux — refusé (perte)", null, listOf(line(id, 6.0)))
        assertNotNull(refusal["error"])
        assertDepot(id, 5.0)
        assertEquals(0L, sql.long("SELECT COUNT(*) FROM retour_fournisseur"))
        assertEquals(0L, sql.long("SELECT COUNT(*) FROM pertes"))

        assertNull(retours.createRetour(supplier, "2026-09-27", "Produit défectueux — refusé (perte)", null, listOf(line(id, 5.0)))["error"])
        assertDepot(id, 0.0)
    }

    /** Taking a received bon's goods back out — reopening or deleting it — is refused once they are sold. */
    @Test
    fun aSoldBonCannotBeReopenedOrDeleted() = runBlocking {
        strict()
        val id = product(0.0)
        val order = receivedBon(id, 10.0)
        repository.createVente(client(), null, "depot", listOf(line(id, 8.0)), null, 0.0)

        refused { repository.reopenPurchaseOrder(order) }
        refused { repository.deletePurchaseOrder(order) }
        assertDepot(id, 2.0)
        assertEquals("received", db.purchaseDao().getOrderById(order)!!.status)
    }

    /** The count is of the total stock, booked at the dépôt: a count below what the camion holds is refused. */
    @Test
    fun aStrictCountCannotGoBelowTheCamion() = runBlocking {
        strict()
        val inventory = InventoryRepository(db)
        val id = product(10.0)
        repository.createChargement(null, listOf(transfer(id, 6.0, "vers_camion")))
        val session = inventory.getOrCreateActiveSession().id

        assertNotNull(inventory.recordScan(session, id, 5.0)["error"])
        assertDepot(id, 4.0)
        assertTrue(db.inventoryDao().getItemsForSession(session).isEmpty())

        assertNull(inventory.recordScan(session, id, 6.0)["error"])
        assertDepot(id, 0.0)
        val item = db.inventoryDao().getItemsForSession(session).single().id
        assertNotNull(inventory.updateScan(item, 5.0)["error"])
        assertDepot(id, 0.0)
    }

    @Test
    fun aStrictTypedStockCannotGoNegative() = runBlocking {
        strict()
        val id = product(5.0)
        repository.createChargement(null, listOf(transfer(id, 5.0, "vers_camion")))
        refused { repository.updateProduct(id, mapOf("stock" to 4.0)) }
        assertDepot(id, 0.0)
    }

    // ── helpers ──

    private suspend fun strict() {
        db.businessSettingsDao().insertIfAbsent(
            BusinessSettingsEntity(business_name = null, business_phone = null, logo_ref = null, allow_negative_stock = false)
        )
        db.businessSettingsDao().updateAllowNegativeStock(false)
    }

    private suspend fun product(stock: Double): Int =
        repository.addProduct(
            mapOf("name" to "Lait Candia 1L n°${++products}", "selling_price" to 110.0, "purchase_price" to 95.0, "stock" to stock)
        ).newId()

    private suspend fun client(): Int = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()

    private suspend fun receivedBon(productId: Int, quantity: Double): Int {
        val supplier = repository.addSupplier(mapOf("name" to "Fournisseur ${++products}")).newId()
        repository.createPurchaseOrder(
            mapOf(
                "supplier_id" to supplier, "montant_paye" to 0.0,
                "items" to listOf(mapOf("product_id" to productId, "quantity" to quantity, "unit_cost" to 95.0)),
            )
        )
        val order = sql.long("SELECT MAX(id) FROM purchase_orders").toInt()
        repository.receivePurchaseOrder(order)
        return order
    }

    private fun line(productId: Int, quantity: Double) =
        mapOf("product_id" to productId, "quantity" to quantity, "unit_price" to 110.0)

    private fun transfer(productId: Int, quantity: Double, direction: String) =
        mapOf("product_id" to productId, "quantity" to quantity, "direction" to direction)

    /** A refusal from strict stock, and nothing else. */
    private suspend fun refused(block: suspend () -> Unit) {
        try {
            block()
            fail("expected strict stock to refuse this")
        } catch (e: DepotStockException) {
            assertTrue(e.message, e.message!!.contains("Stock dépôt insuffisant"))
        }
    }

    private suspend fun assertDepot(productId: Int, expected: Double) {
        val product = db.productDao().getProductByIdIncludingBin(productId)!!
        assertEquals("dépôt stock", expected, product.stock - product.camion_stock, 0.0001)
    }

    private fun SupportSQLiteDatabase.long(query: String): Long =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getLong(0) }

    private fun SupportSQLiteDatabase.double(query: String): Double =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getDouble(0) }

    private fun Map<String, Any>.newId(): Int = (this["id"] as Number).toInt()

    private companion object {
        const val NOW = "2026-09-27T10:00:00Z"
    }
}
