package com.distrigo.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The Dashboard's windows on a real database, as of Tuesday 7 October 2026 at 14:00 in Algeria
 * (13:00 UTC): today so far against the last day with sales to the same hour (D10), this month so far
 * against September to the 7th at the same hour, and the latest sales.
 */
@RunWith(AndroidJUnit4::class)
class DashboardReportTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val algiers = ZoneId.of("Africa/Algiers")
    private val now = ZonedDateTime.of(2026, 10, 7, 14, 0, 0, 0, algiers)
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var repository: ReportRepository

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        sql = db.openHelper.writableDatabase
        repository = ReportRepository(db.reportDao())
    }

    @After
    fun close() = db.close()

    private fun vente(id: Int, client: Int, at: String, total: Double, paid: Double, cost: Double) {
        sql.execSQL(
            "INSERT INTO ventes (id, client_id, tournee_id, source, total, montant_paye, status, note, created_at, uuid) " +
                "VALUES ($id, $client, NULL, 'depot', $total, $paid, 'delivered', NULL, '$at', 'u-vente-$id')"
        )
        sql.execSQL(
            "INSERT INTO vente_items (vente_id, product_id, product_name, unit_type, quantity, unit_price, total_price, uuid, " +
                "purchase_price_snapshot, cost_estimated) VALUES ($id, 1, 'P', 'carton', 1, $total, $total, 'u-line-$id', $cost, 0)"
        )
    }

    @Test
    fun todayAgainstYesterdayToTheSameHour() = runBlocking {
        vente(1, client = 1, at = "2026-10-07T09:00:00Z", total = 1000.0, paid = 600.0, cost = 800.0)  // 10:00 today
        vente(2, client = 2, at = "2026-10-06T08:00:00Z", total = 500.0, paid = 500.0, cost = 400.0)   // yesterday 09:00
        vente(3, client = 3, at = "2026-10-06T14:00:00Z", total = 700.0, paid = 0.0, cost = 500.0)     // yesterday 15:00: later

        val d = repository.dashboardSales(now)
        assertEquals(1, d.today.count)
        assertEquals(1000.0, d.today.total, 0.0)
        assertEquals(400.0, d.today.credit, 0.0)
        assertEquals(1, d.today.clients)
        assertEquals(LocalDate.of(2026, 10, 6), d.yesterdayDay)
        assertEquals(1, d.yesterday!!.count)
        assertEquals(500.0, d.yesterday!!.total, 0.0)
        assertEquals(1.0, change(d.today.total, d.yesterday!!.total)!!, 1e-9)
    }

    /** D10: after a day off, « hier » is the last day that had sales, to the same hour. */
    @Test
    fun yesterdayIsTheLastDayWithSales() = runBlocking {
        vente(1, client = 1, at = "2026-10-04T10:00:00Z", total = 800.0, paid = 800.0, cost = 600.0)   // Saturday 11:00
        vente(2, client = 1, at = "2026-10-04T15:00:00Z", total = 300.0, paid = 300.0, cost = 200.0)   // Saturday 16:00: later

        val d = repository.dashboardSales(now)
        assertEquals(LocalDate.of(2026, 10, 4), d.yesterdayDay)
        assertEquals(800.0, d.yesterday!!.total, 0.0)
    }

    @Test
    fun noSaleBeforeTodayHasNothingToCompare() = runBlocking {
        vente(1, client = 1, at = "2026-10-07T09:00:00Z", total = 1000.0, paid = 1000.0, cost = 800.0)
        val d = repository.dashboardSales(now)
        assertEquals(null, d.yesterdayDay)
        assertEquals(null, d.yesterday)
    }

    @Test
    fun recentSalesFilterOnWhatWasPaid() = runBlocking {
        vente(1, client = 1, at = "2026-10-07T08:00:00Z", total = 1000.0, paid = 1000.0, cost = 800.0)
        vente(2, client = 1, at = "2026-10-07T09:00:00Z", total = 1000.0, paid = 400.0, cost = 800.0)
        vente(3, client = 1, at = "2026-10-07T10:00:00Z", total = 1000.0, paid = 0.0, cost = 800.0)

        assertEquals(listOf(3, 2, 1), repository.recentSales(ActivityFilter.ALL).map { it.id })
        assertEquals(listOf(1), repository.recentSales(ActivityFilter.PAID).map { it.id })
        assertEquals(listOf(3, 2), repository.recentSales(ActivityFilter.CREDIT).map { it.id })
        assertEquals(
            listOf(PaymentState.UNPAID, PaymentState.PARTIAL, PaymentState.PAID),
            repository.recentSales(ActivityFilter.ALL).map { it.state },
        )
    }

    @Test
    fun thisMonthAgainstLastMonthToTheSameDate() = runBlocking {
        vente(1, client = 1, at = "2026-10-07T09:00:00Z", total = 1000.0, paid = 1000.0, cost = 800.0)
        vente(2, client = 1, at = "2026-09-30T23:30:00Z", total = 2000.0, paid = 2000.0, cost = 1500.0) // 00:30 on 1 October
        vente(3, client = 2, at = "2026-09-03T10:00:00Z", total = 3000.0, paid = 3000.0, cost = 2500.0)
        vente(4, client = 2, at = "2026-09-08T10:00:00Z", total = 400.0, paid = 400.0, cost = 300.0)   // after the 7th

        val d = repository.dashboardSales(now)
        assertEquals(3000.0, d.month.total, 0.0)
        assertEquals(700.0, d.month.margin, 0.0)
        assertEquals(700.0 / 2300.0, d.month.marginRate!!, 1e-9)
        assertEquals(3000.0, d.lastMonth.total, 0.0)
        assertEquals(1, d.lastMonth.count)
    }

    @Test
    fun theMonthBeforeA31stEndsOnTheLastDayOfTheShorterMonth() = runBlocking {
        vente(1, client = 1, at = "2026-02-28T09:00:00Z", total = 900.0, paid = 900.0, cost = 600.0)
        val d = repository.dashboardSales(ZonedDateTime.of(2026, 3, 31, 12, 0, 0, 0, algiers))
        assertEquals(900.0, d.lastMonth.total, 0.0)
    }

    @Test
    fun nothingBeforeIsNoChange() {
        assertEquals(null, change(100.0, 0.0))
        assertEquals(-0.25, change(75.0, 100.0)!!, 1e-9)
    }
}
