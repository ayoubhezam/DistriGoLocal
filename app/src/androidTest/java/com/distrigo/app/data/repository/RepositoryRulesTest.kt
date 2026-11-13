package com.distrigo.app.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.core.text.UserFacingException
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.entity.mouvement.StockMovementEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Rules the repositories hold themselves, whatever a screen does (FIN-06, audit 2026-11-09): what the screens only
 * avoided until now is refused at the write.
 */
@RunWith(AndroidJUnit4::class)
class RepositoryRulesTest {

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

    /** A sale edited with another client's id is refused: the sale, and both balances, stay as they were. */
    @Test
    fun aSaleKeepsItsClient() = runBlocking {
        val milk = product(stock = 20.0)
        val owner = client("MOSTAFA")
        val other = client("AHMED")
        repo.createVente(owner, null, "depot", listOf(line(milk, 2.0)), null, 0.0)
        val sale = db.venteDao().getVentesWithDetails(owner).single().vente.id

        refused { repo.updateVente(sale, other, listOf(line(milk, 3.0)), null, 0.0) }
        assertEquals(owner, db.venteDao().getVenteById(sale)!!.client_id)
        assertEquals(240.0, db.venteDao().getVenteById(sale)!!.total, 0.001)
        assertEquals(240.0, db.clientDao().getClientById(owner)!!.balance, 0.001)
        assertEquals(0.0, db.clientDao().getClientById(other)!!.balance, 0.001)
    }

    /** A received bon is not rewritten: it is reopened first, or its reception's stock would no longer match its lines. */
    @Test
    fun aReceivedBonIsNotEdited() = runBlocking {
        val milk = product(stock = 0.0)
        val supplier = (repo.addSupplier(mapOf("name" to "Soummam"))["id"] as Number).toInt()
        repo.createPurchaseOrder(mapOf("supplier_id" to supplier, "montant_paye" to 0.0, "items" to listOf(mapOf("product_id" to milk, "quantity" to 5.0, "unit_cost" to 90.0))))
        val bon = db.purchaseDao().getAllOrders().single().id
        repo.receivePurchaseOrder(bon)

        refused { repo.updatePurchaseOrder(bon, mapOf("montant_paye" to 0.0, "items" to listOf(mapOf("product_id" to milk, "quantity" to 9.0, "unit_cost" to 90.0)))) }
        assertEquals(listOf(5.0), db.purchaseDao().getItemsForOrder(bon).map { it.quantity })
        assertEquals(5.0, db.productDao().getProductById(milk)!!.stock, 0.0001)

        // Reopened, it can be
        repo.reopenPurchaseOrder(bon)
        repo.updatePurchaseOrder(bon, mapOf("montant_paye" to 0.0, "items" to listOf(mapOf("product_id" to milk, "quantity" to 9.0, "unit_cost" to 90.0))))
        assertEquals(listOf(9.0), db.purchaseDao().getItemsForOrder(bon).map { it.quantity })
    }

    /**
     * A defective return whose perte is refused is refused whole. The truck already short by one, the carton coming back
     * leaves it at 0, so the perte of that carton is refused: before, the return was saved without it.
     */
    @Test
    fun aReturnWhosePerteIsRefusedIsNotSaved() = runBlocking {
        val milk = product(stock = 20.0)
        val client = client("MOSTAFA")
        val sold = db.recordedSale(client, "camion", milk to 1.0)
        // The camion already at −1, as stock the app allowed before
        db.stockMovementDao().insert(
            StockMovementEntity(
                product_id = milk, product_name = "Lait", type = "ajustement", direction = "sortie", quantity = 1.0,
                emplacement = "camion", source_label = "test", source_type = "test", source_id = 0, unit_price = 90.0,
                total_value = 90.0, user_name = null, note = null, created_at = "2026-11-13T08:00:00Z"
            )
        )
        assertEquals(-1.0, db.productDao().getProductById(milk)!!.camion_stock, 0.0001)

        refused {
            RetourClientRepository(db).createRetour(client, sold.venteId, "camion", "2026-11-13", "Produit défectueux", null, listOf(sold.returning(0, 1.0)))
        }
        assertTrue(db.retourClientDao().getRetoursForClient(client).isEmpty())
        assertEquals(0, db.perteDao().getPertesBySource("retour_client", 1).size)
        assertEquals(-1.0, db.productDao().getProductById(milk)!!.camion_stock, 0.0001)
    }

    /**
     * A stock refusal inside a transaction already open is thrown, not returned as an entry the caller could ignore while
     * its transaction is rolled back; at the top it is still the entry the screens read.
     */
    @Test
    fun aRefusalInsideATransactionIsThrown() = runBlocking {
        strict()
        val milk = product(stock = 2.0)
        val pertes = PerteRepository(db)
        pertes.seedDefaultPerteTypesIfNeeded()
        val type = pertes.getPerteTypes().first().id

        // At the top: the entry
        assertNotNull(pertes.addPerte(type, milk, 5.0, "depot", "2026-11-13T08:00:00Z", null, null)["error"])

        // Inside: thrown, and nothing of the transaction stays
        try {
            db.withTransaction {
                repo.updateProduct(milk, mapOf("purchase_price" to 99.0))
                pertes.addPerte(type, milk, 5.0, "depot", "2026-11-13T08:00:00Z", null, null)
                fail("the refusal was returned, not thrown")
            }
        } catch (e: DepotStockException) {
            // thrown, as it must be
        }
        assertEquals(90.0, db.productDao().getProductById(milk)!!.purchase_price, 0.0)
        assertEquals(2.0, db.productDao().getProductById(milk)!!.stock, 0.0001)
    }

    // ── helpers ──

    private suspend fun product(stock: Double): Int =
        (repo.addProduct(mapOf("name" to "Lait", "selling_price" to 120.0, "purchase_price" to 90.0, "stock" to stock))["id"] as Number).toInt()

    private suspend fun client(name: String): Int = (repo.addClient(mapOf("name" to name))["id"] as Number).toInt()

    private fun line(product: Int, quantity: Double) = mapOf("product_id" to product, "quantity" to quantity, "unit_price" to 120.0)

    private fun strict() = db.openHelper.writableDatabase.execSQL(
        "INSERT OR REPLACE INTO business_settings (id, business_name, uuid, created_at, updated_at, version, allow_negative_stock) " +
            "VALUES (1, 'T', 'u-s', '2026-10-01T00:00:00Z', 0, 1, 0)"
    )

    /** [block] is refused: thrown in the app's words, or returned as an error entry. */
    private suspend fun refused(block: suspend () -> Map<String, Any>) {
        val result = try { block() } catch (e: UserFacingException) { return } catch (e: IllegalStateException) { return }
        if (result["error"] == null) fail("not refused: $result")
    }
}
