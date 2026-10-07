package com.distrigo.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportPeriod
import com.distrigo.app.data.model.report.ReportSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Ventes report's distribution on a real database: "Ce mois" on 2 October 2026 in Algeria, from
 * 2026-09-30T23:00Z to 2026-10-02T23:00Z.
 *
 * Souk Ahras has two sectors, Centre and Cité; Sedrata one, Vide, without a client.
 * - Centre: 1 bought at the dépôt, 2 from the camion, 3 not this month (last on 15 August);
 * - Cité: 4 bought but is deleted; 5 was created after the month and never bought; 6 was created after
 *   the month but bought in September — there all along; 9 bought exactly at the month's end, the
 *   next month's first instant;
 * - no sector: 7 in Sedrata bought at the dépôt; 8 has no commune and bought nothing.
 * Counted: 1, 2, 3, 6, 7, 8, 9 — served: 1, 2, 7.
 */
@RunWith(AndroidJUnit4::class)
class DistributionReportTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.parse("2026-10-02")
    private val algiers = ZoneId.of("Africa/Algiers")
    private val month = ReportFilter(ReportPeriod.CE_MOIS, ReportSource.TOUT)
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var repository: ReportRepository

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        sql = db.openHelper.writableDatabase
        repository = ReportRepository(db.reportDao())

        secteur(1, "Centre", "Souk Ahras")
        secteur(2, "Cité", "Souk Ahras")
        secteur(3, "Vide", "Sedrata")

        client(1, "Amine", "Souk Ahras", sector = 1)
        client(2, "Bilal", "Souk Ahras", sector = 1)
        client(3, "Chafik", "Souk Ahras", sector = 1)
        client(4, "Djamel", "Souk Ahras", sector = 2, deleted = true)
        client(5, "Elias", "Souk Ahras", sector = 2, created = "2026-10-05T08:00:00Z")
        client(6, "Farid", "Souk Ahras", sector = 2, created = "2026-10-05T08:00:00Z")
        client(7, "Ghani", "Sedrata", sector = null)
        client(8, "Hakim", null, sector = null)
        client(9, "Ilyes", "Souk Ahras", sector = 2)

        vente(1, client = 1, "depot", at = "2026-10-01T09:00:00Z", total = 1000.0)
        vente(2, client = 2, "camion", at = "2026-10-02T10:00:00Z", total = 600.0)
        vente(3, client = 3, "depot", at = "2026-08-15T09:00:00Z", total = 300.0)
        vente(4, client = 4, "depot", at = "2026-10-01T11:00:00Z", total = 900.0)
        vente(5, client = 6, "depot", at = "2026-09-01T09:00:00Z", total = 400.0)
        vente(6, client = 7, "depot", at = "2026-10-01T15:00:00Z", total = 250.0)
        vente(7, client = 9, "depot", at = "2026-10-02T23:00:00Z", total = 800.0)

        retour(1, client = 1, date = "2026-10-01", total = 100.0)
        retour(2, client = 7, date = "2026-10-01", total = 50.0)
    }

    @After
    fun close() = db.close()

    private fun secteur(id: Int, name: String, commune: String) = sql.execSQL(
        "INSERT INTO secteurs (id, nom, commune_name, wilaya_name, created_at, uuid) " +
            "VALUES ($id, '$name', '$commune', 'Souk Ahras', '2026-01-01T00:00:00Z', 'u-secteur-$id')"
    )

    private fun client(
        id: Int, name: String, commune: String?, sector: Int?,
        created: String = "2026-01-01T00:00:00Z", deleted: Boolean = false,
    ) = sql.execSQL(
        "INSERT INTO clients (id, name, commune_name, secteur_id, secteur_name, balance, customer_type, created_at, deleted_at, uuid) " +
            "VALUES ($id, '$name', ${commune?.let { "'$it'" } ?: "NULL"}, ${sector ?: "NULL"}, NULL, 0, 'retail', '$created', " +
            "${if (deleted) 1 else "NULL"}, 'u-client-$id')"
    )

    private fun vente(id: Int, client: Int, source: String, at: String, total: Double) = sql.execSQL(
        "INSERT INTO ventes (id, client_id, tournee_id, source, total, montant_paye, status, note, created_at, uuid) " +
            "VALUES ($id, $client, NULL, '$source', $total, $total, 'delivered', NULL, '$at', 'u-vente-$id')"
    )

    private fun retour(id: Int, client: Int, date: String, total: Double) = sql.execSQL(
        "INSERT INTO retour_client (id, client_id, tournee_id, date, motif, note, total, created_at, uuid) " +
            "VALUES ($id, $client, NULL, '$date', NULL, NULL, $total, '${date}T09:00:00Z', 'u-retour-$id')"
    )

    @Test
    fun theClientsThereAndThoseWhoBought() = runBlocking {
        val d = repository.distribution(month, null, today, algiers)
        assertEquals(7, d.clients)
        assertEquals(3, d.served)
        assertEquals(3.0 / 7, d.rate, 1e-9)
        assertEquals(2, d.unsectored)
    }

    @Test
    fun theSectorsLargestFirstAndTheEmptyOneAtZero() = runBlocking {
        val sectors = repository.distribution(month, null, today, algiers).sectors
        assertEquals(listOf("Centre", "Cité", "Vide"), sectors.map { it.name })
        with(sectors[0]) { assertEquals(3, clients); assertEquals(2, served); assertEquals(1, withoutSale); assertEquals(2.0 / 3, rate, 1e-9) }
        with(sectors[1]) { assertEquals(2, clients); assertEquals(0, served); assertEquals(0.0, rate, 0.0) }
        // No client: a rate of 0, never a division by zero.
        with(sectors[2]) { assertEquals(0, clients); assertEquals(0, served); assertEquals(0.0, rate, 0.0) }
    }

    @Test
    fun theCommunesLargestFirstAndThoseWithoutOneLast() = runBlocking {
        val communes = repository.distribution(month, null, today, algiers).communes
        assertEquals(listOf("Souk Ahras", "Sedrata", null), communes.map { it.name })
        with(communes[0]) { assertEquals(5, clients); assertEquals(2, served); assertEquals(2, sectors) }
        with(communes[1]) { assertEquals(1, clients); assertEquals(1, served); assertEquals(1, sectors) }
        with(communes[2]) { assertEquals(1, clients); assertEquals(0, served); assertEquals(0, sectors) }
    }

    @Test
    fun theDepotAloneServesFewer() = runBlocking {
        val d = repository.distribution(month.copy(source = ReportSource.DEPOT), null, today, algiers)
        assertEquals(7, d.clients)
        assertEquals(2, d.served)
        assertEquals(1, d.sectors.first { it.name == "Centre" }.served)
    }

    @Test
    fun aCommuneNarrowsEverything() = runBlocking {
        val souk = CommuneFilter("Souk Ahras")
        val d = repository.distribution(month, souk, today, algiers)
        assertEquals(5, d.clients)
        assertEquals(2, d.served)
        assertEquals(0, d.unsectored)
        assertEquals(listOf("Centre", "Cité"), d.sectors.map { it.name })
        assertEquals(listOf("Souk Ahras"), d.communes.map { it.name })

        // The sales and returns of Souk Ahras's clients only: 1, 2 and 4 this month, 9 falling after it.
        // A deleted client is no longer counted among the clients, but its sales were made: they stay
        // in the commune's chiffre d'affaires, so the communes still add up to the whole.
        val sales = repository.salesReport(month, today, algiers, commune = souk)
        assertEquals(2500.0, sales.all.total, 0.0)
        assertEquals(3, sales.all.count)
        assertEquals(3, sales.clientsServed)
        assertEquals(100.0, sales.returns!!.total, 0.0)
        val sedrata = repository.salesReport(month, today, algiers, commune = CommuneFilter("Sedrata"))
        assertEquals(250.0, sedrata.all.total, 0.0)
        assertEquals(50.0, sedrata.returns!!.total, 0.0)
    }

    @Test
    fun theClientsWithoutACommuneAreACommuneToo() = runBlocking {
        val none = CommuneFilter("")
        val d = repository.distribution(month, none, today, algiers)
        assertEquals(1, d.clients)
        assertEquals(0, d.served)
        assertEquals(emptyList<String>(), d.sectors.map { it.name })
        assertEquals(listOf<String?>(null), d.communes.map { it.name })
        assertEquals(0, repository.salesReport(month, today, algiers, commune = none).all.count)
    }

    @Test
    fun aSectorsClientsWithoutASale() = runBlocking {
        val centre = repository.unservedClients(month, 1, today, algiers)
        assertEquals(listOf("Chafik"), centre.map { it.name })
        assertEquals(LocalDate.parse("2026-08-15"), centre.single().lastSale)

        val cite = repository.unservedClients(month, 2, today, algiers)
        assertEquals(listOf("Farid", "Ilyes"), cite.map { it.name })
        // Ilyes bought at 00:00 on 3 October, Algiers time: after the month, and his last sale.
        assertEquals(LocalDate.parse("2026-10-03"), cite[1].lastSale)
        assertNull(repository.unservedClients(month, 3, today, algiers).firstOrNull())
    }
}
