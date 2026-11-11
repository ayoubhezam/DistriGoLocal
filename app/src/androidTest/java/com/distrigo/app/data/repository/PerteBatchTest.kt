package com.distrigo.app.data.repository

import com.distrigo.app.core.text.appText
import com.distrigo.app.R
import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.entity.mouvement.StockMovementEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A new perte's lines are recorded together, at the dépôt, or not at all. Product 1 holds 10 at the
 * dépôt, product 2 holds 3. With the app's triggers, so stock is what the ledger makes it.
 */
@RunWith(AndroidJUnit4::class)
class PerteBatchTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var repository: PerteRepository

    @Before
    fun open(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        repository = PerteRepository(db)
        repository.seedDefaultPerteTypesIfNeeded()
        val sql = db.openHelper.writableDatabase
        listOf(1 to 10.0, 2 to 3.0).forEach { (id, qty) ->
            sql.execSQL(
                "INSERT INTO products (id, name, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, " +
                    "has_expiry, camion_stock, uuid) VALUES ($id, 'P$id', 120.0, 100.0, 0, 0, 'pièce', 0, 1, 0, 0, 'u-p-$id')"
            )
            db.stockMovementDao().insert(
                StockMovementEntity(
                    product_id = id, product_name = "P$id", type = "achat", direction = "entree", quantity = qty,
                    emplacement = "depot", source_label = "test", source_type = "purchase_order", source_id = 0,
                    unit_price = 100.0, total_value = qty * 100.0, user_name = null, note = null, created_at = "2026-10-01T09:00:00Z"
                )
            )
        }
        Unit
    }

    @After
    fun close() = db.close()

    private suspend fun stock(id: Int) = db.productDao().getProductById(id)!!.stock
    private fun strict(on: Boolean) = db.openHelper.writableDatabase.execSQL(
        "INSERT OR REPLACE INTO business_settings (id, business_name, uuid, created_at, updated_at, version, allow_negative_stock) " +
            "VALUES (1, 'T', 'u-s', '2026-10-01T00:00:00Z', 0, 1, ${if (on) 0 else 1})"
    )

    @Test
    fun everyLineIsRecordedAtTheDepotWithItsDate(): Unit = runBlocking {
        strict(true)
        val type = repository.getPerteTypes().first().id
        val result = repository.addPertes(
            listOf(PerteRepository.PerteLine(1, type, 4.0), PerteRepository.PerteLine(2, type, 3.0)),
            dateTime = "2026-10-03T10:00:00Z"
        )
        assertEquals(2, result["count"])
        assertEquals(6.0, stock(1), 0.0)
        assertEquals(0.0, stock(2), 0.0)
        val pertes = db.perteDao().getPertesForType(type)
        assertEquals(listOf("depot", "depot"), pertes.map { it.source })
        assertTrue(pertes.all { it.date_time == "2026-10-03T10:00:00Z" })
    }

    /**
     * A loss in the camion comes out of the camion (STK-03): the dépôt keeps what it had, and under strict stock a
     * dépôt at zero does not refuse it.
     */
    @Test
    fun aLossInTheCamionComesOutOfTheCamion(): Unit = runBlocking {
        strict(true)
        load(2, 3.0)   // all of P2 in the camion: the dépôt holds none
        val type = repository.getPerteTypes().first().id
        val result = repository.addPertes(listOf(PerteRepository.PerteLine(2, type, 2.0, source = "camion")), dateTime = "2026-10-03T10:00:00Z")
        assertEquals(1, result["count"])
        val product = db.productDao().getProductById(2)!!
        assertEquals(listOf(1.0, 1.0, 0.0), listOf(product.stock, product.camion_stock, product.stock - product.camion_stock))
        assertEquals(listOf("camion"), db.perteDao().getPertesForType(type).map { it.source })
    }

    /** The camion never gives more than it holds, even with negative stock allowed; nothing is written. */
    @Test
    fun aLossInTheCamionIsCappedAtWhatItHolds(): Unit = runBlocking {
        strict(false)
        load(1, 4.0)
        val type = repository.getPerteTypes().first().id
        val result = repository.addPertes(listOf(PerteRepository.PerteLine(1, type, 5.0, source = "camion")), dateTime = "2026-10-03T10:00:00Z")
        assertTrue(result.containsKey("error"))
        assertEquals(4.0, db.productDao().getProductById(1)!!.camion_stock, 0.0)
        assertEquals(0, db.perteDao().getPertesForType(type).size)
    }

    /** [quantity] of product [id] loaded from the dépôt into the camion. */
    private suspend fun load(id: Int, quantity: Double) {
        ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
            .createChargement(null, listOf(mapOf("product_id" to id, "quantity" to quantity, "direction" to "vers_camion")))
    }

    @Test
    fun underStrictStockOneLineTooManyRecordsNone(): Unit = runBlocking {
        strict(true)
        val type = repository.getPerteTypes().first().id
        val result = repository.addPertes(
            listOf(PerteRepository.PerteLine(1, type, 4.0), PerteRepository.PerteLine(2, type, 5.0)),
            dateTime = "2026-10-03T10:00:00Z"
        )
        assertTrue(result.containsKey("error"))
        assertEquals(10.0, stock(1), 0.0)   // the first line rolled back with the second
        assertEquals(3.0, stock(2), 0.0)
        assertEquals(0, db.perteDao().getPertesForType(type).size)
    }

    @Test
    fun withNegativeStockAllowedALossCanExceedTheStock(): Unit = runBlocking {
        strict(false)
        val type = repository.getPerteTypes().first().id
        val result = repository.addPertes(listOf(PerteRepository.PerteLine(2, type, 5.0)), dateTime = "2026-10-03T10:00:00Z")
        assertEquals(1, result["count"])
        assertEquals(-2.0, stock(2), 0.0)
    }

    @Test
    fun aNewTypeIsCreatedOnceByName(): Unit = runBlocking {
        val before = repository.getPerteTypes().size
        val (created, error) = repository.createPerteType("  Rongeurs  ")
        assertEquals(null, error)
        assertEquals("Rongeurs", created!!.name)
        assertEquals(before + 1, repository.getPerteTypes().size)
        assertEquals("Ce type existe déjà", repository.createPerteType("rongeurs").second)
        assertEquals("Ce type existe déjà", repository.createPerteType("CASSE").second)
        // The shared « enter a name » text, as Listes et catégories says it
        assertEquals(appText(R.string.lists_enter_name), repository.createPerteType("   ").second)
        assertEquals(before + 1, repository.getPerteTypes().size)
    }
}
