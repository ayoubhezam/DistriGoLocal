package com.distrigo.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import com.distrigo.app.data.local.entity.ClientEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Correction mode on a tournée: a deleted sale un-visits its client, and the list leads with the latest sale. */
@RunWith(AndroidJUnit4::class)
class TourneeCorrectionTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var repo: ProductRepository
    private var clientId = 0
    private var tourneeId = 0
    private var milk = 0

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .withChangeTracking()
            .withDeviceIdentity { "7e57de71-0000-4000-8000-000000000031" }
            .build()
        repo = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        runBlocking {
            clientId = db.clientDao().insertClient(
                ClientEntity(name = "Amine", phone = null, wilaya_name = null, commune_name = null, secteur_id = null, secteur_name = null, address = null, note = null, image_uri = null, latitude = null, longitude = null)
            ).toInt()
            milk = (repo.addProduct(mapOf("name" to "Lait", "barcode" to "Lait", "selling_price" to 100.0, "purchase_price" to 80.0, "stock" to 20.0, "unit_type" to "carton"))["id"] as Number).toInt()
            repo.createChargement(null, listOf(mapOf("product_id" to milk, "quantity" to 10.0, "direction" to "vers_camion")))
            repo.createTournee("Lundi", null, null, null)
            tourneeId = db.tourneeDao().getOpenTournee()!!.id
            repo.addClientsToTournee(tourneeId, listOf(clientId))
        }
    }

    @After
    fun close() = db.close()

    private fun sell(quantity: Double): Int = runBlocking {
        repo.createVente(clientId, tourneeId, "camion", listOf(mapOf("product_id" to milk, "quantity" to quantity, "unit_price" to 100.0)), null, 0.0)
        repo.markTourneeClientVisited(tourneeId, clientId)
        db.venteDao().getVentesForTournee(tourneeId).maxOf { it.id }
    }

    private fun status() = runBlocking { db.tourneeClientDao().getForTournee(tourneeId).single().status }

    @Test
    fun deletingTheClientsOnlySaleMakesThemToVisitAgain() {
        val sale = sell(2.0)
        assertEquals("visite", status())

        runBlocking { repo.deleteVente(sale) }

        assertEquals("a_visiter", status())
        assertEquals(null, runBlocking { db.tourneeClientDao().getForTournee(tourneeId).single().visited_at })
    }

    @Test
    fun aClientWithAnotherSaleStaysVisited() {
        sell(1.0)
        val second = sell(2.0)

        runBlocking { repo.deleteVente(second) }

        assertEquals("visite", status())
    }

    @Test
    fun theLateSaleLeadsTheListByTimestamp() {
        val first = sell(1.0)
        val second = sell(1.0)
        // The first sale's timestamp made later than the second's: the list follows time, not ids.
        runBlocking {
            db.openHelper.writableDatabase.execSQL("UPDATE ventes SET created_at = '2099-01-01T00:00:00Z' WHERE id = $first")
        }
        val ids = runBlocking { repo.getTournee(tourneeId) }.ventes!!.map { it.id }
        assertEquals(listOf(first, second), ids)
    }
}
