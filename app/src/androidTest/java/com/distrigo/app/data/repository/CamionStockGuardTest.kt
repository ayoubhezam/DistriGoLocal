package com.distrigo.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The truck never ends below zero and lower than it started (STK-04, audit 2026-11-09), whatever « Autoriser le stock
 * négatif » says (it is on here, the default): taking truck stock back is refused once it has left the truck.
 */
@RunWith(AndroidJUnit4::class)
class CamionStockGuardTest {

    private lateinit var db: AppDatabase
    private lateinit var products: ProductRepository
    private lateinit var retours: RetourClientRepository

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        products = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        retours = RetourClientRepository(db)
    }

    @After
    fun close() = db.close()

    /** A return of 2 into the camion, the truck unloaded, then the return deleted: refused, nothing changes. */
    @Test
    fun deletingAReturnTheTruckNoLongerHoldsIsRefused() = runBlocking {
        val id = product(10.0)
        val client = (products.addClient(mapOf("name" to "Épicerie El Amel"))["id"] as Number).toInt()
        val sold = db.recordedSale(client, "camion", id to 2.0)
        assertNull(retours.createRetour(client, sold.venteId, "camion", "2026-11-11", "Client insatisfait", null, listOf(sold.returning(0, 2.0)))["error"])
        assertStock(id, total = 12.0, camion = 2.0)
        transfer(id, 2.0, "vers_depot")
        assertStock(id, total = 12.0, camion = 0.0)

        val retour = db.retourClientDao().getRetoursForClient(client).single().id
        assertNotNull(retours.deleteRetour(retour)["error"])
        assertStock(id, total = 12.0, camion = 0.0)
        assertNotNull(db.retourClientDao().getRetourById(retour))

        // Back in the truck, the return can go
        transfer(id, 2.0, "vers_camion")
        assertNull(retours.deleteRetour(retour)["error"])
        assertStock(id, total = 10.0, camion = 0.0)
    }

    /** 3 loaded, 2 sold from the truck, then the load deleted: refused, it would leave the truck at −2. */
    @Test
    fun deletingALoadAlreadySoldFromIsRefused() = runBlocking {
        val id = product(10.0)
        val client = (products.addClient(mapOf("name" to "Épicerie El Amel"))["id"] as Number).toInt()
        transfer(id, 3.0, "vers_camion")
        products.createVente(client, null, "camion", listOf(mapOf("product_id" to id, "quantity" to 2.0, "unit_price" to 110.0)), null, 0.0)
        assertStock(id, total = 8.0, camion = 1.0)

        val load = db.chargementDao().getAllChargements().maxOf { it.id }
        val refused = runCatching { products.deleteChargement(load) }
        assertNotNull(refused.exceptionOrNull() as? CamionStockException)
        assertStock(id, total = 8.0, camion = 1.0)
    }

    private suspend fun product(stock: Double): Int =
        (products.addProduct(mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0, "purchase_price" to 95.0, "stock" to stock))["id"] as Number).toInt()

    private suspend fun transfer(id: Int, quantity: Double, direction: String) {
        products.createChargement(null, listOf(mapOf("product_id" to id, "quantity" to quantity, "direction" to direction)))
    }

    private suspend fun assertStock(id: Int, total: Double, camion: Double) {
        val product = db.productDao().getProductById(id)!!
        assertEquals("stock", total, product.stock, 0.0001)
        assertEquals("camion_stock", camion, product.camion_stock, 0.0001)
    }
}
