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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Ventes report's SQL on a real database: "Ce mois" on 2 October 2026 in Algeria, which runs from
 * 2026-09-30T23:00Z to 2026-10-02T23:00Z. A sale a second before or exactly at a bound shows where it falls.
 */
@RunWith(AndroidJUnit4::class)
class ReportRepositoryTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.parse("2026-10-02")
    private val algiers = ZoneId.of("Africa/Algiers")
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var repository: ReportRepository

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        sql = db.openHelper.writableDatabase
        repository = ReportRepository(db.reportDao())

        vente(1, "depot", client = 1, at = "2026-09-30T23:30:00Z", total = 1000.0, paid = 1000.0)   // 00:30 on 1 October
        line(1, quantity = 10.0, price = 100.0, cost = 70.0)
        vente(2, "camion", client = 2, at = "2026-10-02T10:15:00.5Z", total = 600.0, paid = 200.0)
        line(2, quantity = 3.0, price = 200.0, cost = 150.0, estimated = true)
        vente(3, "depot", client = 1, at = "2026-10-01T12:00:00Z", total = 800.0, paid = 900.0)     // paid over: counts 800
        line(3, quantity = 4.0, price = 200.0, cost = 100.0)
        vente(4, "depot", client = 3, at = "2026-09-30T22:59:59Z", total = 5000.0, paid = 0.0)     // 23:59:59 on 30 September
        line(4, quantity = 1.0, price = 5000.0, cost = 1.0)
        vente(5, "camion", client = 3, at = "2026-10-02T23:00:00Z", total = 7000.0, paid = 0.0)    // 00:00 on 3 October
        line(5, quantity = 1.0, price = 7000.0, cost = 1.0)

        retour(1, date = "2026-10-01", total = 150.0)
        retour(2, date = "2026-09-30", total = 999.0)
    }

    @After
    fun close() = db.close()

    private var inventoryItem = 0

    /** A count [status] at [completedAt], with lines of these signed écart values. */
    private fun inventory(id: Int, status: String, completedAt: String?, vararg gaps: Double) {
        sql.execSQL(
            "INSERT INTO inventory_sessions (id, status, started_at, completed_at, uuid, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?, 0, 1)",
            arrayOf<Any?>(id, status, "2026-09-01T08:00:00Z", completedAt, "u-inv-$id", "2026-09-01T08:00:00Z")
        )
        gaps.forEach { value ->
            val item = ++inventoryItem
            sql.execSQL(
                "INSERT INTO inventory_items (id, session_id, product_id, product_name, qte_systeme, qte_physique, ecart, purchase_price_snapshot, " +
                    "valeur_ecart, created_at, uuid, updated_at) VALUES (?, ?, ?, 'P', 0, 0, 0, 100, ?, '2026-09-01T08:00:00Z', ?, 0)",
                arrayOf<Any?>(item, id, item, value, "u-item-$item")   // one line per product in a count
            )
        }
    }

    /** REP-01: the counts completed in the period, signed — a shortage of 300 and a surplus of 100 are −200. */
    @Test
    fun theResultCountsThePeriodsInventoryGaps() = runBlocking {
        inventory(1, "completed", "2026-10-01T09:00:00Z", -300.0, 100.0)
        inventory(2, "completed", "2026-09-30T22:59:00Z", -5000.0)   // 23:59 on 30 September: another month
        inventory(3, "draft", null, -700.0)                          // still being counted
        val report = repository.profitReport(ReportFilter(ReportPeriod.CE_MOIS, ReportSource.TOUT), today, algiers)
        assertEquals(-200.0, report.inventoryGaps, 1e-9)
    }

    private fun vente(id: Int, source: String, client: Int, at: String, total: Double, paid: Double) = sql.execSQL(
        "INSERT INTO ventes (id, client_id, tournee_id, source, total, montant_paye, status, note, created_at, uuid) " +
            "VALUES ($id, $client, NULL, '$source', $total, $paid, 'pending', NULL, '$at', 'u-vente-$id')"
    )

    private fun line(vente: Int, quantity: Double, price: Double, cost: Double, estimated: Boolean = false) = sql.execSQL(
        "INSERT INTO vente_items (vente_id, product_id, product_name, unit_type, quantity, unit_price, total_price, uuid, " +
            "purchase_price_snapshot, cost_estimated) VALUES ($vente, 1, 'P', 'carton', $quantity, $price, ${quantity * price}, " +
            "'u-line-$vente', $cost, ${if (estimated) 1 else 0})"
    )

    private fun retour(id: Int, date: String, total: Double) = sql.execSQL(
        "INSERT INTO retour_client (id, client_id, tournee_id, date, motif, note, total, created_at, uuid) " +
            "VALUES ($id, 1, NULL, '$date', NULL, NULL, $total, '${date}T09:00:00Z', 'u-retour-$id')"
    )

    @Test
    fun theWholePeriodFromBothPlaces() = runBlocking {
        val report = repository.salesReport(ReportFilter(ReportPeriod.CE_MOIS, ReportSource.TOUT), today, algiers)

        assertEquals(SalesFigures(2, 1800.0, 1800.0), report.depot)
        assertEquals(SalesFigures(1, 600.0, 200.0), report.camion)
        assertEquals(400.0, report.all.credit, 0.0)
        assertEquals(2, report.clientsServed)
        assertEquals(700.0 + 450.0 + 400.0, report.cost, 0.0)
        assertEquals(450.0, report.estimatedCost, 0.0)
        assertTrue(report.isMarginEstimated)
        assertEquals(2400.0 - 1550.0, report.grossMargin, 0.0)
        assertEquals(ReturnFigures(1, 150.0), report.returns)
        assertEquals(2250.0, report.netTotal!!, 0.0)

        assertEquals(listOf("2026-10-01", "2026-10-02"), report.days.map { it.day.toString() })
        assertEquals(SalesFigures(2, 1800.0, 1800.0), report.days[0].depot)
        assertEquals(SalesFigures(1, 600.0, 200.0), report.days[1].camion)
    }

    @Test
    fun theCamionAloneLeavesTheReturnsOut() = runBlocking {
        val report = repository.salesReport(ReportFilter(ReportPeriod.CE_MOIS, ReportSource.CAMION), today, algiers)

        assertEquals(SalesFigures.ZERO, report.depot)
        assertEquals(SalesFigures(1, 600.0, 200.0), report.camion)
        assertEquals(1, report.clientsServed)
        assertEquals(450.0, report.cost, 0.0)
        assertNull(report.returns)
        assertNull(report.netTotal)
    }

    @Test
    fun anEmptyPeriodIsAllZeros() = runBlocking {
        val report = repository.salesReport(
            ReportFilter(ReportPeriod.PERSONNALISE, customFrom = LocalDate.parse("2026-08-01"), customTo = LocalDate.parse("2026-08-03")),
            today, algiers,
        )

        assertEquals(SalesFigures.ZERO, report.all)
        assertEquals(0.0, report.cost, 0.0)
        assertNull(report.marginRate)
        assertEquals(ReturnFigures(0, 0.0), report.returns)
        assertEquals(3, report.days.size)
    }
}
