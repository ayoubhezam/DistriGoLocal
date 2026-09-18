package com.distrigo.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import com.distrigo.app.data.local.entity.ClientEntity
import com.distrigo.app.data.local.entity.SupplierEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What a sale, a bon or a payment must satisfy before it is written, on an in-memory database with the app's triggers. */
@RunWith(AndroidJUnit4::class)
class DocumentRulesTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var repo: ProductRepository
    private var clientId = 0
    private var supplierId = 0

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .withChangeTracking()
            .withDeviceIdentity { "7e57de71-0000-4000-8000-000000000014" }
            .build()
        repo = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        runBlocking {
            clientId = db.clientDao().insertClient(
                ClientEntity(name = "Amine", phone = null, wilaya_name = null, commune_name = null, secteur_id = null, secteur_name = null, address = null, note = null, image_uri = null, latitude = null, longitude = null)
            ).toInt()
            supplierId = db.supplierDao().insertSupplier(
                SupplierEntity(name = "Cevital", phone = null, address = null, note = null, balance = 0.0, latitude = null, longitude = null, wilaya_name = null, commune_name = null)
            ).toInt()
        }
    }

    @After
    fun close() = db.close()

    private fun product(name: String, stock: Double = 0.0): Int = runBlocking {
        (repo.addProduct(mapOf("name" to name, "barcode" to name, "selling_price" to 100.0, "purchase_price" to 80.0, "stock" to stock, "unit_type" to "carton"))["id"] as Number).toInt()
    }

    private fun line(productId: Int, quantity: Double, price: Double = 100.0) = mapOf("product_id" to productId, "quantity" to quantity, "unit_price" to price)

    private fun sale(items: List<Map<String, Any?>>, paid: Double = 0.0, source: String = "depot") = runBlocking {
        repo.createVente(clientId, null, source, items, null, paid)
    }

    private fun toCamion(productId: Int, quantity: Double) = runBlocking {
        repo.createChargement(null, listOf(mapOf("product_id" to productId, "quantity" to quantity, "direction" to "vers_camion")))
    }

    private inline fun refused(contains: String, block: () -> Unit) {
        try {
            block()
            fail("should be refused")
        } catch (e: IllegalStateException) {
            assertTrue(e.message ?: "", (e.message ?: "").contains(contains))
        }
    }

    private fun stockOf(id: Int) = runBlocking { db.productDao().getProductByIdIncludingBin(id)!! }

    // ── Receiving a bon ──

    @Test
    fun receivingABonWhoseProductIsInTheBinStillCountsItsGoods() {
        val milk = product("Lait")
        runBlocking {
            repo.createPurchaseOrder(mapOf("supplier_id" to supplierId, "items" to listOf(mapOf("product_id" to milk, "quantity" to 12.0, "unit_cost" to 80.0))))
            repo.deleteProduct(milk)
        }
        val order = runBlocking { db.purchaseDao().getAllOrders() }.single()
        runBlocking { repo.receivePurchaseOrder(order.id) }

        assertEquals("received", runBlocking { db.purchaseDao().getOrderById(order.id) }!!.status)
        // The goods went into the binned product's stock, ready for when it is restored.
        assertEquals(12.0, stockOf(milk).stock, 0.0)
        assertEquals(1, runBlocking { db.stockMovementDao().getMovementsForProduct(milk) }.size)
    }

    // ── The camion check ──

    @Test
    fun theCamionCheckAddsUpTheLinesOfOneProduct() {
        val soda = product("Selecto", stock = 10.0)
        toCamion(soda, 10.0)
        assertEquals(10.0, stockOf(soda).camion_stock, 0.0)

        refused("Stock insuffisant") { sale(listOf(line(soda, 8.0), line(soda, 8.0)), source = "camion") }
        // Nothing was written by the refused sale.
        assertEquals(10.0, stockOf(soda).camion_stock, 0.0)
        assertTrue(runBlocking { db.venteDao().getVentesWithDetails(null) }.isEmpty())

        sale(listOf(line(soda, 5.0), line(soda, 5.0)), source = "camion")
        assertEquals(0.0, stockOf(soda).camion_stock, 0.0)
    }

    @Test
    fun aSaleFromTheDepotMayGoNegative() {
        val soda = product("Selecto", stock = 1.0)
        sale(listOf(line(soda, 3.0)))
        assertEquals(-2.0, stockOf(soda).stock, 0.0)
    }

    // ── Amounts ──

    @Test
    fun refusesAmountsThatMakeNoSense() {
        val soda = product("Selecto", stock = 10.0)
        refused("supérieure à zéro") { sale(listOf(line(soda, 0.0))) }
        refused("négatif") { sale(listOf(line(soda, 1.0, price = -5.0))) }
        refused("négatif") { sale(listOf(line(soda, 1.0)), paid = -1.0) }
        refused("supérieur à zéro") { runBlocking { repo.addClientPayment(clientId, 0.0, null) } }
        refused("supérieur à zéro") { runBlocking { repo.addSupplierPayment(supplierId, -5.0, null) } }
        assertTrue(runBlocking { db.venteDao().getVentesWithDetails(null) }.isEmpty())
        assertTrue(runBlocking { db.purchaseDao().getAllOrders() }.isEmpty())

    }

    @Test
    fun payingMoreThanTheTotalIsAnAdvance() {
        val soda = product("Selecto", stock = 10.0)
        sale(listOf(line(soda, 2.0)), paid = 250.0)
        // Billed 200, paid 250: the client is 50 in advance, which the balance carries as a negative solde.
        assertEquals(-50.0, runBlocking { db.clientDao().getClientById(clientId) }!!.balance, 0.0)
        runBlocking { repo.createPurchaseOrder(mapOf("supplier_id" to supplierId, "montant_paye" to 1000.0, "items" to listOf(mapOf("product_id" to soda, "quantity" to 1.0, "unit_cost" to 80.0)))) }
        assertEquals(-920.0, runBlocking { db.supplierDao().getSupplierById(supplierId) }!!.balance, 0.0)
    }

    // ── Editing a sale after its product went to the bin ──

    @Test
    fun aSaleStaysEditableAfterItsProductIsBinned() {
        val soda = product("Selecto", stock = 10.0)
        sale(listOf(line(soda, 2.0)))
        val vente = runBlocking { db.venteDao().getVentesWithDetails(null) }.single().vente
        runBlocking { repo.deleteProduct(soda) }

        runBlocking { repo.updateVente(vente.id, clientId, listOf(line(soda, 3.0)), "corrigé", 0.0) }

        val updated = runBlocking { db.venteDao().getVenteById(vente.id) }!!
        assertEquals("corrigé", updated.note)
        assertEquals(300.0, updated.total, 0.0)
        assertEquals(7.0, stockOf(soda).stock, 0.0)
    }
}
