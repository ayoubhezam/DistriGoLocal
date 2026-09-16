package com.distrigo.app.data.local.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A row keeps the UUID it was created with through the app's real write paths.
 *
 * Entities take `uuid` as a constructor default, so identity survives only while every update
 * copies the row it read. These go through the repository, not the DAOs, because that is where the
 * rule is followed or broken: an edit, a stock change made by a sale, a balance recompute.
 *
 * In memory, so nothing touches the app's database on the device.
 */
@RunWith(AndroidJUnit4::class)
class RowUuidTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ProductRepository

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // With the app's triggers: stock only follows the ledger with them installed.
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        repository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun newRowsGetDistinctV4Uuids() = runBlocking {
        val first = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        val second = repository.addClient(mapOf("name" to "Supérette Nour")).newId()

        val a = db.clientDao().getClientById(first)!!.uuid
        val b = db.clientDao().getClientById(second)!!.uuid
        assertTrue(a, V4_UUID.matches(a))
        assertTrue(b, V4_UUID.matches(b))
        assertNotEquals(a, b)
    }

    @Test
    fun editingAClientKeepsItsUuid() = runBlocking {
        val id = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        val before = db.clientDao().getClientById(id)!!.uuid

        repository.updateClient(id, mapOf("name" to "Épicerie El Amel 2", "phone" to "0550 12 34 56"))

        val after = db.clientDao().getClientById(id)!!
        assertEquals("Épicerie El Amel 2", after.name)
        assertEquals(before, after.uuid)
    }

    @Test
    fun editingASupplierKeepsItsUuid() = runBlocking {
        val id = repository.addSupplier(mapOf("name" to "Laiterie Soummam", "initial_balance" to 200.0)).newId()
        val before = db.supplierDao().getSupplierById(id)!!.uuid

        repository.updateSupplier(id, mapOf("initial_balance" to 500.0))

        val after = db.supplierDao().getSupplierById(id)!!
        assertEquals(500.0, after.balance, 0.001)
        assertEquals(before, after.uuid)
    }

    /**
     * A sale rewrites the product row (stock) and the client row (balance), and editing the sale
     * rewrites both again. None of that may change who they are, nor the vente's own identity.
     */
    @Test
    fun salesAndTheirEditsKeepProductClientAndVenteUuids() = runBlocking {
        val productId = repository.addProduct(
            mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0, "stock" to 40.0)
        ).newId()
        val clientId = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        val productUuid = db.productDao().getProductById(productId)!!.uuid
        val clientUuid = db.clientDao().getClientById(clientId)!!.uuid

        repository.createVente(
            clientId = clientId, tourneeId = null, source = "depot",
            items = listOf(mapOf("product_id" to productId, "quantity" to 10.0, "unit_price" to 110.0)),
            note = null, montantPaye = 500.0
        )
        val vente = db.venteDao().getVentesWithDetails(clientId).single().vente

        repository.updateVente(
            id = vente.id, clientId = clientId,
            items = listOf(mapOf("product_id" to productId, "quantity" to 12.0, "unit_price" to 110.0)),
            note = "corrigée", montantPaye = 500.0
        )

        val product = db.productDao().getProductById(productId)!!
        val client = db.clientDao().getClientById(clientId)!!
        assertEquals(28.0, product.stock, 0.001)
        assertEquals(820.0, client.balance, 0.001)
        assertEquals(productUuid, product.uuid)
        assertEquals(clientUuid, client.uuid)
        assertEquals(vente.uuid, db.venteDao().getVenteById(vente.id)!!.uuid)
    }

    private fun Map<String, Any>.newId(): Int = (this["id"] as Number).toInt()

    private companion object {
        val V4_UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
