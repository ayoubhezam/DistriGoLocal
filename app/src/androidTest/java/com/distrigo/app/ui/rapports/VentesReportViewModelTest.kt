package com.distrigo.app.ui.rapports

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.dao.DistributionTotalsRow
import com.distrigo.app.data.local.dao.ReportDao
import com.distrigo.app.data.local.dao.SalesBySource
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportFilterStore
import com.distrigo.app.data.model.report.ReportPeriod
import com.distrigo.app.data.model.report.ReportSource
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
 * The commune chosen in "Meilleure distribution" narrows the distribution alone: the sales above it —
 * the chiffre d'affaires, the cards, the chart — stay the whole business's, and are not even queried
 * again.
 *
 * 1 and 2 bought in Souk Ahras's Centre, 3 there did not; 4, in Sedrata without a sector, bought.
 */
@RunWith(AndroidJUnit4::class)
class VentesReportViewModelTest {

    /** The real queries, counting those the sales and the distribution start with. */
    private class CountingDao(private val inner: ReportDao) : ReportDao by inner {
        var salesQueries = 0
        var distributionQueries = 0

        override suspend fun salesBySource(start: String, end: String, source: String?): List<SalesBySource> {
            salesQueries++
            return inner.salesBySource(start, end, source)
        }

        override suspend fun distributionTotals(start: String, end: String, source: String?, commune: String?): DistributionTotalsRow {
            distributionQueries++
            return inner.distributionTotals(start, end, source, commune)
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
        sql.execSQL("INSERT INTO secteurs (id, nom, commune_name, wilaya_name, created_at, uuid) VALUES (2, 'Vide', 'Sedrata', 'Souk Ahras', '2026-01-01T00:00:00Z', 'u-s2')")
        client(1, "Amine", "Souk Ahras", 1)
        client(2, "Bilal", "Souk Ahras", 1)
        client(3, "Chafik", "Souk Ahras", 1)
        client(4, "Ghani", "Sedrata", null)
        // Midday, so the two days hold them in any time zone.
        vente(1, client = 1, "depot", "2026-10-01T10:00:00Z", 1000.0)
        vente(2, client = 2, "camion", "2026-10-02T10:00:00Z", 600.0)
        vente(3, client = 4, "depot", "2026-10-01T12:00:00Z", 250.0)
    }

    @After
    fun close() = db.close()

    private fun client(id: Int, name: String, commune: String, sector: Int?) = sql.execSQL(
        "INSERT INTO clients (id, name, commune_name, secteur_id, balance, customer_type, created_at, uuid) " +
            "VALUES ($id, '$name', '$commune', ${sector ?: "NULL"}, 0, 'retail', '2026-01-01T00:00:00Z', 'u-c$id')"
    )

    private fun vente(id: Int, client: Int, source: String, at: String, total: Double) = sql.execSQL(
        "INSERT INTO ventes (id, client_id, source, total, montant_paye, status, created_at, uuid) " +
            "VALUES ($id, $client, '$source', $total, $total, 'delivered', '$at', 'u-v$id')"
    )

    @Test
    fun aCommuneNarrowsTheDistributionAndLeavesTheSalesAlone() = runBlocking {
        val store = ReportFilterStore().apply {
            update { ReportFilter(ReportPeriod.PERSONNALISE, ReportSource.TOUT, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02")) }
        }
        val vm = withContext(Dispatchers.Main) { VentesReportViewModel(ReportRepository(dao), store) }

        val all = withTimeout(10_000) { vm.state.first { !it.loading && it.report != null && it.distribution != null } }
        assertEquals(1850.0, all.report!!.all.total, 0.0)
        assertEquals(4, all.distribution!!.clients)
        assertEquals(3, all.distribution!!.served)
        assertEquals(listOf("Sedrata", "Souk Ahras"), all.distribution!!.communeNames)
        val salesQueries = dao.salesQueries
        val distributionQueries = dao.distributionQueries

        withContext(Dispatchers.Main) { vm.setCommune("Souk Ahras") }
        val souk = withTimeout(10_000) { vm.state.first { it.commune == "Souk Ahras" && it.distribution?.clients == 3 } }
        // The distribution's figures and sectors narrowed; its communes did not.
        assertEquals(2, souk.distribution!!.served)
        assertEquals(listOf("Centre"), souk.distribution!!.topSectors.map { it.name })
        assertEquals(all.distribution!!.communes, souk.distribution!!.communes)
        // The sales: the very same report, not queried again.
        assertSame(all.report, souk.report)
        assertEquals(salesQueries, dao.salesQueries)
        assertEquals(distributionQueries + 1, dao.distributionQueries)

        withContext(Dispatchers.Main) { vm.setCommune(null) }
        val back = withTimeout(10_000) { vm.state.first { it.commune == null && it.distribution?.clients == 4 } }
        assertSame(all.report, back.report)
        assertEquals(salesQueries, dao.salesQueries)
    }
}
