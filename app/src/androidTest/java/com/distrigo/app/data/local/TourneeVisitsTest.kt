package com.distrigo.app.data.local

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.TourneeClientEntity
import com.distrigo.app.data.local.entity.TourneeEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A tournée card's « client(s) visité(s) »: every client visited, with a sale or not — not only those who bought.
 *
 * Tournée 1 plans 1 to 4: 1 visited and bought, 2 visited without buying, 3 bought without being marked visited,
 * 4 not visited yet; 5, not on its list, bought on it all the same (twice). Visited: 1, 2, 3 and 5.
 */
@RunWith(AndroidJUnit4::class)
class TourneeVisitsTest {

    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase

    @Before
    fun open() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        sql = db.openHelper.writableDatabase
        (1..5).forEach { id ->
            sql.execSQL(
                "INSERT INTO clients (id, name, balance, customer_type, created_at, uuid) " +
                    "VALUES ($id, 'C$id', 0, 'retail', '2026-01-01T00:00:00Z', 'u-c$id')"
            )
        }
        val tournee = db.tourneeDao().insertTournee(
            TourneeEntity(status = "en_cours", date_debut = "2026-10-26", date_fin = null, note = null, nom = "Souk Ahras",
                wilaya_name = null, commune_name = null, created_at = "2026-10-26T08:00:00Z")
        ).toInt()
        db.tourneeClientDao().insertAll(
            listOf(
                TourneeClientEntity(tournee_id = tournee, client_id = 1, status = "visite", visited_at = "2026-10-26T09:00:00Z"),
                TourneeClientEntity(tournee_id = tournee, client_id = 2, status = "visite", visited_at = "2026-10-26T09:30:00Z"),
                TourneeClientEntity(tournee_id = tournee, client_id = 3, status = "a_visiter", visited_at = null),
                TourneeClientEntity(tournee_id = tournee, client_id = 4, status = "a_visiter", visited_at = null),
            )
        )
        vente(1, client = 1, tournee = tournee)
        vente(2, client = 3, tournee = tournee)
        vente(3, client = 5, tournee = tournee)
        vente(4, client = 5, tournee = tournee)
    }

    @After
    fun close() = db.close()

    private fun vente(id: Int, client: Int, tournee: Int) = sql.execSQL(
        "INSERT INTO ventes (id, client_id, tournee_id, source, total, montant_paye, status, created_at, uuid) " +
            "VALUES ($id, $client, $tournee, 'camion', 100, 100, 'delivered', '2026-10-26T10:00:00Z', 'u-v$id')"
    )

    @Test
    fun theCardCountsTheClientsVisitedWithASaleOrNot() = runBlocking {
        val summary = db.tourneeDao().getAllTourneeSummaries().single()
        assertEquals(4, summary.clients_count)
        // Its clients: the four on its list and 5, who bought on it off the list
        assertEquals(5, summary.clients_total)
        assertEquals(4, summary.ventes_count)
        assertEquals(summary.clients_count, db.tourneeDao().getTourneeSummary(summary.tournee.id)!!.clients_count)
    }

    @Test
    fun aTourneeWithNothingVisitedCountsNone() = runBlocking {
        val empty = db.tourneeDao().insertTournee(
            TourneeEntity(status = "en_cours", date_debut = "2026-10-26", date_fin = null, note = null, nom = "Guelma",
                wilaya_name = null, commune_name = null, created_at = "2026-10-26T08:00:00Z")
        ).toInt()
        db.tourneeClientDao().insertAll(listOf(TourneeClientEntity(tournee_id = empty, client_id = 1, status = "a_visiter", visited_at = null)))
        assertEquals(0, db.tourneeDao().getTourneeSummary(empty)!!.clients_count)
        assertEquals(1, db.tourneeDao().getTourneeSummary(empty)!!.clients_total)
    }
}
