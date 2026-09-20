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
import com.distrigo.app.data.model.PriceMovementKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A product's price history, read from the bons and the ventes it appears on. */
@RunWith(AndroidJUnit4::class)
class PriceMovementsTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var repo: ProductRepository
    private var clientId = 0
    private var supplierId = 0
    private var product = 0

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .withChangeTracking()
            .withDeviceIdentity { "7e57de71-0000-4000-8000-000000000054" }
            .build()
        repo = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        runBlocking {
            clientId = db.clientDao().insertClient(
                ClientEntity(name = "Épicerie El Amel", phone = null, wilaya_name = null, commune_name = null, secteur_id = null, secteur_name = null, address = null, note = null, image_uri = null, latitude = null, longitude = null)
            ).toInt()
            supplierId = db.supplierDao().insertSupplier(
                SupplierEntity(name = "Sarl El Manar", phone = null, address = null, note = null, balance = 0.0, latitude = null, longitude = null, wilaya_name = null, commune_name = null)
            ).toInt()
            product = (repo.addProduct(
                mapOf("name" to "Sucre 1kg", "barcodes" to listOf("111"), "selling_price" to 95.0, "purchase_price" to 85.0, "stock" to 100.0)
            )["id"] as Number).toInt()
        }
    }

    @After
    fun close() = db.close()

    private fun bon(unitCost: Double) = runBlocking {
        repo.createPurchaseOrder(
            mapOf(
                "supplier_id" to supplierId,
                "items" to listOf(mapOf("product_id" to product, "quantity" to 10.0, "unit_cost" to unitCost)),
            )
        )
    }

    private fun vente(unitPrice: Double) = runBlocking {
        repo.createVente(clientId, null, "depot", listOf(mapOf("product_id" to product, "quantity" to 2.0, "unit_price" to unitPrice)), null, 0.0)
    }

    @Test
    fun bothSidesAreReadFromTheirDocuments() {
        bon(80.0)
        vente(92.0)
        bon(85.0)
        vente(95.0)

        val movements = runBlocking { repo.getPriceMovements(product) }
        assertEquals(4, movements.size)
        // Newest first, whatever the kind.
        assertEquals(
            listOf(95.0, 85.0, 92.0, 80.0),
            movements.sortedByDescending { it.date }.map { it.unitPrice }
        )
        val achats = movements.filter { it.kind == PriceMovementKind.ACHAT }
        val ventes = movements.filter { it.kind == PriceMovementKind.VENTE }
        assertEquals(listOf("Sarl El Manar", "Sarl El Manar"), achats.map { it.party })
        assertEquals(listOf("Épicerie El Amel", "Épicerie El Amel"), ventes.map { it.party })
        // Each entry names the document it came from.
        assertTrue(achats.map { it.documentLabel }.all { it.startsWith("BA-") || it.startsWith("#") })
        assertTrue(ventes.map { it.documentLabel }.all { it.startsWith("V-") || it.startsWith("#") })
    }

    @Test
    fun aPriceIsMeasuredAgainstTheOneBeforeItOfItsOwnKind() {
        bon(80.0)
        vente(92.0)
        bon(85.0)
        vente(95.0)

        val byPrice = runBlocking { repo.getPriceMovements(product) }.associateBy { it.kind to it.unitPrice }
        assertNull(byPrice.getValue(PriceMovementKind.ACHAT to 80.0).delta)
        assertNull(byPrice.getValue(PriceMovementKind.VENTE to 92.0).delta)
        // 85 follows the 80 bought before it, not the 92 sold in between.
        assertEquals(5.0, byPrice.getValue(PriceMovementKind.ACHAT to 85.0).delta!!, 0.0)
        assertEquals(3.0, byPrice.getValue(PriceMovementKind.VENTE to 95.0).delta!!, 0.0)
    }

    @Test
    fun editingABonCorrectsItsEntryInsteadOfAddingOne() {
        bon(80.0)
        // createPurchaseOrder reports only a message, so the bon is found by its row.
        val id = runBlocking { db.purchaseDao().getAllOrders() }.single().id
        runBlocking {
            repo.updatePurchaseOrder(
                id,
                mapOf(
                    "supplier_id" to supplierId,
                    "items" to listOf(mapOf("product_id" to product, "quantity" to 10.0, "unit_cost" to 78.0)),
                )
            )
        }

        val movements = runBlocking { repo.getPriceMovements(product) }
        assertEquals(listOf(78.0), movements.map { it.unitPrice })
    }

    @Test
    fun aProductNeverBoughtNorSoldHasNoHistory() {
        assertEquals(emptyList<Any>(), runBlocking { repo.getPriceMovements(product) })
    }
}
