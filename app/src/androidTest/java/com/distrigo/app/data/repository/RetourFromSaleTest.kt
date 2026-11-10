package com.distrigo.app.data.repository

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.core.text.UserFacingException
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
 * A client return made from a sale (FIN-02, STK-02, UX-01; owner, 2026-11-10): each line at its sale line's price and
 * cost, at most what was sold less what came back, into the place chosen — the sale's source by default.
 */
@RunWith(AndroidJUnit4::class)
class RetourFromSaleTest {

    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var products: ProductRepository
    private lateinit var retours: RetourClientRepository

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        sql = db.openHelper.writableDatabase
        products = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        retours = RetourClientRepository(db)
    }

    @After
    fun close() = db.close()

    /** Sold at 80 (a price changed in the cart) with a cost of 60: credited 80, not today's 110; the cost kept is 60. */
    @Test
    fun aReturnedLineTakesItsSalesPriceAndCost() = runBlocking {
        val id = product(20.0)
        val client = client()
        val sold = db.recordedSale(client, "depot", id to 5.0, unitPrice = 80.0, cost = 60.0)
        db.clientDao().recomputeBalance(client)
        assertEquals(400.0, balance(client), 0.001)
        products.updateProduct(id, mapOf("purchase_price" to 99.0))

        assertNull(retours.createRetour(client, sold.venteId, "depot", "2026-11-10", "Client insatisfait", null, listOf(sold.returning(0, 2.0)))["error"])

        val retour = db.retourClientDao().getRetoursForClient(client).single()
        assertEquals(160.0, retour.total, 0.001)
        assertEquals(sold.venteId, retour.vente_id)
        val line = db.retourClientDao().getItemsForRetour(retour.id).single()
        assertEquals(listOf(80.0, 160.0, 60.0), listOf(line.unit_price, line.total_price, line.purchase_price_snapshot))
        assertEquals(sold.itemIds[0], line.vente_item_id)
        assertEquals(240.0, balance(client), 0.001)

        // The Résultat takes back what the goods cost when sold, not today's 99
        val report = db.reportDao().returnsWithCost("2026-11-01", "2026-11-30")
        assertEquals(160.0, report.total, 0.001)
        assertEquals(120.0, report.cost, 0.001)
    }

    /** Of a sale line, no more than was sold less what earlier returns took back — whatever motifs it is split across. */
    @Test
    fun aReturnIsCappedAtWhatIsLeftOfTheSaleLine() = runBlocking {
        val id = product(20.0)
        val client = client()
        val sold = db.recordedSale(client, "depot", id to 5.0)

        assertNull(retours.createRetour(client, sold.venteId, "depot", "2026-11-10", "Client insatisfait", null, listOf(sold.returning(0, 3.0)))["error"])
        assertNotNull(retours.createRetour(client, sold.venteId, "depot", "2026-11-10", "Client insatisfait", null, listOf(sold.returning(0, 3.0)))["error"])
        assertNotNull(
            retours.createRetour(
                client, sold.venteId, "depot", "2026-11-10", null, null,
                listOf(sold.returning(0, 1.5, "Client insatisfait"), sold.returning(0, 1.0, "Produit périmé"))
            )["error"]
        )
        assertEquals(1, db.retourClientDao().getRetoursForClient(client).size)

        assertNull(retours.createRetour(client, sold.venteId, "depot", "2026-11-10", "Client insatisfait", null, listOf(sold.returning(0, 2.0)))["error"])
        // Returned in full: the sale is no longer offered
        assertTrue(db.retourClientDao().getReturnableSales(client).isEmpty())
    }

    /** The goods come back where the user chose: a truck sale brought back to the dépôt raises the dépôt only. */
    @Test
    fun theGoodsComeBackToThePlaceChosen() = runBlocking {
        val id = product(20.0)
        val client = client()
        val sold = db.recordedSale(client, "camion", id to 5.0)

        retours.createRetour(client, sold.venteId, "depot", "2026-11-10", "Client insatisfait", null, listOf(sold.returning(0, 2.0)))
        assertStock(id, total = 22.0, camion = 0.0)
        assertEquals("depot", db.retourClientDao().getRetoursForClient(client).single().emplacement)

        retours.createRetour(client, sold.venteId, "camion", "2026-11-10", "Client insatisfait", null, listOf(sold.returning(0, 1.0)))
        assertStock(id, total = 23.0, camion = 1.0)
    }

    /** A defective line returned to the dépôt comes in and goes out again there as a perte: no stock changes. */
    @Test
    fun aDefectiveLineReturnedToTheDepotIsAPerteThere() = runBlocking {
        val id = product(20.0)
        val client = client()
        val sold = db.recordedSale(client, "depot", id to 5.0)

        retours.createRetour(client, sold.venteId, "depot", "2026-11-10", "Produit défectueux", null, listOf(sold.returning(0, 2.0)))

        assertStock(id, total = 20.0, camion = 0.0)
        val retour = db.retourClientDao().getRetoursForClient(client).single().id
        assertEquals(listOf("depot"), db.perteDao().getPertesBySource("retour_client", retour).map { it.source })

        retours.deleteRetour(retour)
        assertStock(id, total = 20.0, camion = 0.0)
    }

    /** Every sale can be returned from, not only the delivered ones (UX-01); newest first. */
    @Test
    fun anySaleCanBeReturnedFromNewestFirst() = runBlocking {
        val id = product(20.0)
        val client = client()
        products.createVente(client, null, "depot", listOf(mapOf("product_id" to id, "quantity" to 2.0, "unit_price" to 110.0)), null, 0.0)
        val first = sql.long("SELECT MAX(id) FROM ventes").toInt()
        assertEquals("pending", db.venteDao().getVenteById(first)!!.status)
        val second = db.recordedSale(client, "camion", id to 1.0).venteId

        val sales = db.retourClientDao().getReturnableSales(client)
        assertEquals(listOf(second, first), sales.map { it.id })
        assertEquals(listOf("camion", "depot"), sales.map { it.source })
    }

    /** A sale of another client, or a line of another sale, is refused. */
    @Test
    fun aReturnIsOnlyFromTheClientsOwnSale() = runBlocking {
        val id = product(20.0)
        val client = client()
        val other = client()
        val theirs = db.recordedSale(other, "depot", id to 5.0)
        val mine = db.recordedSale(client, "depot", id to 5.0)

        refused { retours.createRetour(client, theirs.venteId, "depot", "2026-11-10", "Client insatisfait", null, listOf(theirs.returning(0, 1.0))) }
        refused { retours.createRetour(client, mine.venteId, "depot", "2026-11-10", "Client insatisfait", null, listOf(theirs.returning(0, 1.0))) }
        assertTrue(db.retourClientDao().getRetoursForClient(client).isEmpty())
        assertStock(id, total = 20.0, camion = 0.0)
    }

    // ── helpers ──

    private var names = 0

    private suspend fun product(stock: Double): Int =
        (products.addProduct(mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0, "purchase_price" to 95.0, "stock" to stock))["id"] as Number).toInt()

    private suspend fun client(): Int = (products.addClient(mapOf("name" to "Client ${++names}"))["id"] as Number).toInt()

    private suspend fun balance(client: Int) = db.clientDao().getClientById(client)!!.balance

    private suspend fun assertStock(productId: Int, total: Double, camion: Double) {
        val product = db.productDao().getProductById(productId)!!
        assertEquals("stock", total, product.stock, 0.0001)
        assertEquals("camion_stock", camion, product.camion_stock, 0.0001)
    }

    private suspend fun refused(block: suspend () -> Map<String, Any>) {
        val result = try { block() } catch (e: UserFacingException) { return }
        if (result["error"] == null) fail("not refused: $result")
    }

    private fun SupportSQLiteDatabase.long(query: String): Long =
        query(query).use { it.moveToFirst(); it.getLong(0) }
}
