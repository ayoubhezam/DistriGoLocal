package com.distrigo.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import com.distrigo.app.data.local.entity.ClientEntity
import com.distrigo.app.data.local.entity.TourneeVenteDraftEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deleting a tournée (DATA-02, audit 2026-11-09): one with sales is refused and keeps its drafts; one without goes with
 * its drafts and its client plan, which nothing else would ever reach again.
 */
@RunWith(AndroidJUnit4::class)
class TourneeDeleteTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var repo: ProductRepository
    private var clientId = 0
    private var tourneeId = 0

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .withChangeTracking()
            .withDeviceIdentity { "7e57de71-0000-4000-8000-000000000032" }
            .build()
        repo = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        runBlocking {
            clientId = db.clientDao().insertClient(
                ClientEntity(name = "Amine", phone = null, wilaya_name = null, commune_name = null, secteur_id = null, secteur_name = null, address = null, note = null, image_uri = null, latitude = null, longitude = null)
            ).toInt()
            repo.createTournee("Lundi", null, null, null)
            tourneeId = db.tourneeDao().getOpenTournee()!!.id
            repo.addClientsToTournee(tourneeId, listOf(clientId))
            draft()
        }
    }

    @After
    fun close() = db.close()

    /** Refused for its sale: the tournée, its draft and its client plan all stay. */
    @Test
    fun aTourneeWithSalesKeepsItsDrafts() = runBlocking {
        db.recordedSale(clientId, "camion").venteId.let { sale ->
            db.openHelper.writableDatabase.execSQL("UPDATE ventes SET tournee_id = ? WHERE id = ?", arrayOf<Any>(tourneeId, sale))
        }

        assertNotNull(repo.deleteTournee(tourneeId)["error"])

        assertNotNull(db.tourneeDao().getTourneeById(tourneeId))
        assertEquals(1, db.tourneeVenteDraftDao().observeCountForTournee(tourneeId).first())
        assertEquals(listOf(clientId), db.tourneeClientDao().getClientIdsForTournee(tourneeId))
    }

    /** Deleted: its drafts and its client plan go with it. */
    @Test
    fun aTourneeWithoutSalesGoesWithItsDraftsAndClients() = runBlocking {
        assertNull(repo.deleteTournee(tourneeId)["error"])

        assertNull(db.tourneeDao().getTourneeById(tourneeId))
        assertEquals(0, db.tourneeVenteDraftDao().observeCountForTournee(tourneeId).first())
        assertEquals(emptyList<Int>(), db.tourneeClientDao().getClientIdsForTournee(tourneeId))
    }

    private suspend fun draft() {
        val now = java.time.Instant.now().toString()
        db.tourneeVenteDraftDao().insert(
            TourneeVenteDraftEntity(
                tournee_id = tourneeId, client_id = clientId, client_name = "Amine", items_json = "[]", note = "",
                montant_paye = "", item_count = 0, total = 0.0, last_step = "products", created_at = now, updated_at = now
            )
        )
    }
}
