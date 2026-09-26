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
import com.distrigo.app.ui.mouvements.MovementFilters
import com.distrigo.app.ui.mouvements.MovementParty
import com.distrigo.app.ui.mouvements.MovementType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What the Mouvements filters keep: the kind of movement, where it happened, and whom it was with. */
@RunWith(AndroidJUnit4::class)
class MovementFilterQueryTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var repo: ProductRepository
    private var product = 0
    private var amine = 0
    private var nadir = 0
    private var manar = 0

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .withChangeTracking()
            .withDeviceIdentity { "7e57de71-0000-4000-8000-000000000055" }
            .build()
        repo = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        runBlocking {
            amine = client("Épicerie Amine")
            nadir = client("Superette Nadir")
            manar = db.supplierDao().insertSupplier(
                SupplierEntity(name = "Sarl El Manar", phone = null, address = null, note = null, balance = 0.0, latitude = null, longitude = null, wilaya_name = null, commune_name = null)
            ).toInt()
            // Created with stock, which the repository records as an opening "ajustement".
            product = (repo.addProduct(
                mapOf("name" to "Sucre 1kg", "selling_price" to 95.0, "purchase_price" to 85.0, "stock" to 100.0)
            )["id"] as Number).toInt()
        }
    }

    @After
    fun close() = db.close()

    private fun client(name: String): Int = runBlocking {
        db.clientDao().insertClient(
            ClientEntity(name = name, phone = null, wilaya_name = null, commune_name = null, secteur_id = null, secteur_name = null, address = null, note = null, image_uri = null, latitude = null, longitude = null)
        ).toInt()
    }

    /** A bon received, which is what writes the « achat » movement. */
    private fun achat(quantity: Double) = runBlocking {
        repo.createPurchaseOrder(
            mapOf("supplier_id" to manar, "items" to listOf(mapOf("product_id" to product, "quantity" to quantity, "unit_cost" to 85.0)))
        )
        repo.receivePurchaseOrder(db.purchaseDao().getAllOrders().first { it.status != "received" }.id)
    }

    private fun vente(clientId: Int, quantity: Double) = runBlocking {
        repo.createVente(clientId, null, "depot", listOf(mapOf("product_id" to product, "quantity" to quantity, "unit_price" to 95.0)), null, 0.0)
    }

    private fun filtered(filters: MovementFilters) = runBlocking {
        repo.getFilteredMovements(
            productId   = product,
            dateFrom    = filters.dateFrom,
            dateTo      = filters.dateTo,
            direction   = filters.direction,
            emplacement = filters.emplacement,
            types       = filters.types.map { it.key },
            party       = filters.party?.key,
            partyId     = filters.partyId,
        )
    }

    private fun count(filters: MovementFilters) = runBlocking {
        repo.countMovements(repo.movementQuery(
            productId   = product,
            dateFrom    = filters.dateFrom,
            dateTo      = filters.dateTo,
            direction   = filters.direction,
            emplacement = filters.emplacement,
            types       = filters.types.map { it.key },
            party       = filters.party?.key,
            partyId     = filters.partyId,
        ))
    }

    @Test
    fun everyKindIsKeptWhenNothingIsAsked() {
        achat(10.0)
        vente(amine, 2.0)
        val all = filtered(MovementFilters())
        // The opening adjustment, the purchase and the sale.
        assertEquals(listOf("vente", "achat", "ajustement"), all.map { it.type })
        assertEquals(3, count(MovementFilters()))
    }

    @Test
    fun theTypeKeepsSeveralKindsAtOnce() {
        achat(10.0)
        vente(amine, 2.0)
        val filters = MovementFilters(
            types = setOf(
                MovementType.ACHAT,
                MovementType.VENTE,
            )
        )
        assertEquals(listOf("vente", "achat"), filtered(filters).map { it.type })
        assertEquals(2, count(filters))
    }

    @Test
    fun thePartyKeepsWhatWasTradedWithThatSide() {
        achat(10.0)
        vente(amine, 2.0)
        vente(nadir, 3.0)

        val withClients = MovementFilters(party = MovementParty.CLIENT)
        assertEquals(2, count(withClients))
        assertEquals(listOf("vente", "vente"), filtered(withClients).map { it.type })

        val withSuppliers = MovementFilters(party = MovementParty.FOURNISSEUR)
        assertEquals(listOf("achat"), filtered(withSuppliers).map { it.type })

        // One client of the two: the opening adjustment, with nobody, is never among them.
        val amineOnly = withClients.copy(partyId = amine)
        assertEquals(1, count(amineOnly))
        assertEquals(2.0, filtered(amineOnly).single().quantity, 0.0)

        val manarOnly = withSuppliers.copy(partyId = manar)
        assertEquals(listOf("achat"), filtered(manarOnly).map { it.type })
    }

    @Test
    fun theOtherFiltersNarrowTheSameList() {
        achat(10.0)
        vente(amine, 2.0)

        val entrees = MovementFilters(direction = "entree")
        assertEquals(listOf("achat", "ajustement"), filtered(entrees).map { it.type })

        val depot = MovementFilters(emplacement = "depot")
        assertEquals(3, count(depot))
        assertEquals(0, count(MovementFilters(emplacement = "camion")))

        // A day before anything happened keeps nothing; today keeps it all.
        val today = java.time.LocalDate.now().toString()
        assertEquals(0, count(MovementFilters(dateTo = "2020-01-01")))
        assertEquals(3, count(MovementFilters(dateFrom = today, dateTo = today)))
    }

    @Test
    fun theFilterOffersOnlyThePartiesThisProductMovedWith() {
        achat(10.0)
        vente(amine, 2.0)

        assertEquals(listOf("Épicerie Amine"), runBlocking { repo.clientsForProductMovements(product) }.map { it.name })
        assertEquals(listOf("Sarl El Manar"), runBlocking { repo.suppliersForProductMovements(product) }.map { it.name })
    }
}
