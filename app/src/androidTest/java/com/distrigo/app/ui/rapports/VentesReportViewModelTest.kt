package com.distrigo.app.ui.rapports

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.dao.ClientPlaceSalesRow
import com.distrigo.app.data.local.dao.ReportDao
import com.distrigo.app.data.local.dao.SalesBySource
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportFilterStore
import com.distrigo.app.data.model.report.ReportPeriod
import com.distrigo.app.data.model.report.ReportSource
import com.distrigo.app.data.repository.NO_QUARTIER
import com.distrigo.app.data.repository.NO_SECTEUR
import com.distrigo.app.data.repository.PlaceLevel
import com.distrigo.app.data.repository.ReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * « Répartition des ventes »: its total is the report's chiffre d'affaires, moving through its places queries nothing
 * again, and another source starts again from the wilayas.
 *
 * 1 and 2 bought in Souk Ahras › Souk Ahras › Centre › Gare (1 dépôt, 2 camion), 3 there did not; 4, in Sedrata
 * without a secteur, bought from the dépôt.
 */
@RunWith(AndroidJUnit4::class)
class VentesReportViewModelTest {

    /** The real queries, counting those the sales and their places start with. */
    private class CountingDao(private val inner: ReportDao) : ReportDao by inner {
        var salesQueries = 0
        var placeQueries = 0

        override suspend fun salesBySource(start: String, end: String, source: String?): List<SalesBySource> {
            salesQueries++
            return inner.salesBySource(start, end, source)
        }

        override suspend fun salesByClientPlace(start: String, end: String, source: String?): List<ClientPlaceSalesRow> {
            placeQueries++
            return inner.salesByClientPlace(start, end, source)
        }
    }

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var dao: CountingDao

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        sql = db.openHelper.writableDatabase
        dao = CountingDao(db.reportDao())

        sql.execSQL("INSERT INTO secteurs (id, nom, commune_name, wilaya_name, created_at, uuid) VALUES (1, 'Centre', 'Souk Ahras', 'Souk Ahras', '2026-01-01T00:00:00Z', 'u-s1')")
        sql.execSQL("INSERT INTO quartiers (id, nom, secteur_id, uuid) VALUES (5, 'Gare', 1, 'u-q5')")
        client(1, "Amine", "Souk Ahras", 1, 5)
        client(2, "Bilal", "Souk Ahras", 1, 5)
        client(3, "Chafik", "Souk Ahras", 1, 5)
        client(4, "Ghani", "Sedrata", null, null)
        // Midday, so the two days hold them in any time zone.
        vente(1, client = 1, "depot", "2026-10-01T10:00:00Z", 1000.0)
        vente(2, client = 2, "camion", "2026-10-02T10:00:00Z", 600.0)
        vente(3, client = 4, "depot", "2026-10-01T12:00:00Z", 250.0)
    }

    @After
    fun close() = db.close()

    private fun client(id: Int, name: String, commune: String, sector: Int?, quartier: Int?) = sql.execSQL(
        "INSERT INTO clients (id, name, wilaya_name, commune_name, secteur_id, quartier_id, balance, customer_type, created_at, uuid) " +
            "VALUES ($id, '$name', 'Souk Ahras', '$commune', ${sector ?: "NULL"}, ${quartier ?: "NULL"}, 0, 'retail', '2026-01-01T00:00:00Z', 'u-c$id')"
    )

    private fun vente(id: Int, client: Int, source: String, at: String, total: Double) = sql.execSQL(
        "INSERT INTO ventes (id, client_id, source, total, montant_paye, status, created_at, uuid) " +
            "VALUES ($id, $client, '$source', $total, $total, 'delivered', '$at', 'u-v$id')"
    )

    private fun filter(source: ReportSource) =
        ReportFilter(ReportPeriod.PERSONNALISE, source, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02"))

    @Test
    fun theSalesByPlaceAddUpAndMovingThroughThemQueriesNothing() = runBlocking {
        val store = ReportFilterStore().apply { update { filter(ReportSource.TOUT) } }
        val vm = withContext(Dispatchers.Main) { VentesReportViewModel(ReportRepository(dao), store) }

        val all = withTimeout(10_000) { vm.state.first { !it.loading && it.report != null && it.places != null } }
        assertEquals(1850.0, all.report!!.all.total, 0.0)
        assertEquals(all.report!!.all.total, all.places!!.total, 0.0)
        val wilaya = all.place!!.children.single()
        assertEquals("Souk Ahras", wilaya.name)
        assertEquals(listOf("Souk Ahras" to 1600.0, "Sedrata" to 250.0), wilaya.children.map { it.name to it.total })
        val sedrata = wilaya.children.last()
        assertEquals(NO_SECTEUR, sedrata.children.single().name)
        assertEquals(NO_QUARTIER, sedrata.children.single().children.single().name)
        val salesQueries = dao.salesQueries
        val placeQueries = dao.placeQueries

        // Wilaya › commune › secteur › quartier picked: its clients, the most first
        withContext(Dispatchers.Main) { vm.openPlace(wilaya) }
        val commune = withTimeout(10_000) { vm.state.first { it.place?.level == PlaceLevel.WILAYA } }.place!!.children.first()
        withContext(Dispatchers.Main) { vm.openPlace(commune) }
        val secteur = withTimeout(10_000) { vm.state.first { it.place?.level == PlaceLevel.COMMUNE } }.place!!.children.single()
        withContext(Dispatchers.Main) { vm.openPlace(secteur) }
        val quartier = withTimeout(10_000) { vm.state.first { it.place?.level == PlaceLevel.SECTEUR } }.place!!.children.single()
        withContext(Dispatchers.Main) { vm.openPlace(quartier) }
        val picked = withTimeout(10_000) { vm.state.first { it.pickedQuartier != null } }
        assertEquals("Gare", picked.pickedQuartier!!.name)
        assertEquals(listOf("Amine" to 1000.0, "Bilal" to 600.0), picked.pickedQuartier!!.children.map { it.name to it.total })
        assertEquals(listOf("Wilayas", "Souk Ahras", "Souk Ahras", "Centre"), picked.placeTrail.map { it.name })
        // Not queried again, the report itself untouched
        assertSame(all.report, picked.report)
        assertEquals(salesQueries, dao.salesQueries)
        assertEquals(placeQueries, dao.placeQueries)

        // Up: the quartier put down, then the breadcrumb to the wilaya
        withContext(Dispatchers.Main) { vm.placeUp() }
        withTimeout(10_000) { vm.state.first { it.pickedQuartier == null && it.place?.level == PlaceLevel.SECTEUR } }
        withContext(Dispatchers.Main) { vm.placeTo(1) }
        withTimeout(10_000) { vm.state.first { it.place?.level == PlaceLevel.WILAYA } }

        // The dépôt alone: its sales only, from the wilayas again
        store.update { filter(ReportSource.DEPOT) }
        // Both loaded for it: the sales and their places reload side by side
        val depot = withTimeout(10_000) {
            vm.state.first { it.filter.source == ReportSource.DEPOT && !it.loading && it.report?.all?.total == 1250.0 && it.places?.total == 1250.0 }
        }
        assertEquals(PlaceLevel.ALL, depot.place!!.level)
        assertEquals(depot.report!!.all.total, depot.places!!.total, 0.0)
    }
}
