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
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The Dashboard's windows on a real database, as of Tuesday 7 October 2026 at 14:00 in Algeria
 * (13:00 UTC): today so far — sold, « Payé à la vente », « Crédit » —, this month so far, and the latest sales.
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

    private fun vente(id: Int, client: Int, at: String, total: Double, paid: Double, cost: Double, source: String = "depot") {
        sql.execSQL(
            "INSERT INTO ventes (id, client_id, tournee_id, source, total, montant_paye, status, note, created_at, uuid) " +
                "VALUES ($id, $client, NULL, '$source', $total, $paid, 'delivered', NULL, '$at', 'u-vente-$id')"
        )
        sql.execSQL(
            "INSERT INTO vente_items (vente_id, product_id, product_name, unit_type, quantity, unit_price, total_price, uuid, " +
                "purchase_price_snapshot, cost_estimated) VALUES ($id, 1, 'P', 'carton', 1, $total, $total, 'u-line-$id', $cost, 0)"
        )
    }

    @Test
    fun todayIsItsSalesSoFarPaidAndOnCredit() = runBlocking {
        vente(1, client = 1, at = "2026-10-07T09:00:00Z", total = 1000.0, paid = 600.0, cost = 800.0)                     // 10:00, dépôt
        vente(2, client = 2, at = "2026-10-07T11:00:00Z", total = 500.0, paid = 0.0, cost = 300.0, source = "camion")     // 12:00, tournée
        vente(3, client = 3, at = "2026-10-06T22:30:00Z", total = 900.0, paid = 900.0, cost = 700.0)                      // 23:30 yesterday
        vente(4, client = 3, at = "2026-10-07T14:00:00Z", total = 700.0, paid = 700.0, cost = 500.0)                      // 15:00: later today

        val d = repository.dashboardSales(now)
        assertEquals(2, d.today.count)
        assertEquals(1500.0, d.today.total, 0.0)
        assertEquals(600.0, d.today.paid, 0.0)
        assertEquals(900.0, d.today.credit, 0.0)
        assertEquals(2, d.today.clients)
    }

    /** « Payé à la vente » counts a sale's payment up to its total: what a client overpays is an advance, not a sale. */
    @Test
    fun anOverpaymentCountsOnlyUpToTheSale() = runBlocking {
        vente(1, client = 1, at = "2026-10-07T09:00:00Z", total = 1000.0, paid = 1500.0, cost = 800.0)
        val d = repository.dashboardSales(now)
        assertEquals(1000.0, d.today.paid, 0.0)
        assertEquals(0.0, d.today.credit, 0.0)
    }

    /** A versement later in the day pays the client's account, not the sale: today's credit stays as it was given. */
    @Test
    fun aPaymentAfterTheSaleLeavesTheCardAsSold() = runBlocking {
        vente(1, client = 1, at = "2026-10-07T09:00:00Z", total = 1000.0, paid = 0.0, cost = 800.0)
        sql.execSQL(
            "INSERT INTO clients (id, name, balance, customer_type, uuid, created_at, updated_at, version) " +
                "VALUES (1, 'C', 0, 'retail', 'u-client-1', '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z', 1)"
        )
        sql.execSQL(
            "INSERT INTO client_payments (client_id, amount, note, created_at, uuid, updated_at, version) " +
                "VALUES (1, 1000, NULL, '2026-10-07T10:00:00Z', 'u-pay-1', '2026-10-07T10:00:00Z', 1)"
        )
        val d = repository.dashboardSales(now)
        assertEquals(0.0, d.today.paid, 0.0)
        assertEquals(1000.0, d.today.credit, 0.0)
    }

    @Test
    fun aDeletedSaleLeavesTheDay() = runBlocking {
        vente(1, client = 1, at = "2026-10-07T09:00:00Z", total = 1000.0, paid = 600.0, cost = 800.0)
        vente(2, client = 2, at = "2026-10-07T10:00:00Z", total = 500.0, paid = 500.0, cost = 300.0)
        sql.execSQL("DELETE FROM vente_items WHERE vente_id = 2")
        sql.execSQL("DELETE FROM ventes WHERE id = 2")
        val d = repository.dashboardSales(now)
        assertEquals(1000.0, d.today.total, 0.0)
        assertEquals(600.0, d.today.paid, 0.0)
        assertEquals(400.0, d.today.credit, 0.0)
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

    private fun product(id: Int, stock: Double, min: Double, moved: Boolean = false, deleted: Boolean = false) {
        sql.execSQL(
            "INSERT INTO products (id, name, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, " +
                "has_expiry, camion_stock, uuid, created_at, updated_at, version, deleted_at) VALUES ($id, 'P$id', 100, 80, $stock, " +
                "$min, 'carton', 0, 1, 0, 0, 'u-product-$id', '2026-01-01T00:00:00Z', 0, 1, " +
                (if (deleted) "'2026-10-01T00:00:00Z'" else "NULL") + ")"
        )
        if (moved) sql.execSQL(
            "INSERT INTO stock_movements (product_id, product_name, type, direction, quantity, emplacement, source_label, " +
                "source_type, source_id, total_value, created_at, uuid, updated_at) VALUES ($id, 'P$id', 'achat', 'in', 5, " +
                "'depot', 'Bon', 'purchase', 1, 400, '2026-09-01T00:00:00Z', 'u-move-$id', 0)"
        )
    }

    private fun client(id: Int, balance: Double) = sql.execSQL(
        "INSERT INTO clients (id, name, balance, customer_type, uuid, created_at, updated_at, version) " +
            "VALUES ($id, 'C$id', $balance, 'retail', 'u-client-$id', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', 1)"
    )

    /** The alerts are counted on their own (2026-10-20) and must say what the two reports they come from say. */
    @Test
    fun theAlertsCountWhatTheReportsShow() = runBlocking {
        product(1, stock = 0.0, min = 0.0, moved = true)    // ever stocked, now empty: out
        product(2, stock = 0.0, min = 0.0)                  // never stocked, no minimum: not followed
        product(3, stock = 2.0, min = 5.0)                  // under its minimum: low
        product(4, stock = -1.0, min = 3.0)                 // sold beyond its stock: out
        product(5, stock = 10.0, min = 5.0)                 // fine
        product(6, stock = 0.0, min = 5.0, deleted = true)  // deleted: not counted
        client(1, balance = 1000.0)
        client(2, balance = 300.0)
        vente(1, client = 1, at = "2026-06-01T09:00:00Z", total = 1000.0, paid = 0.0, cost = 800.0)  // 128 days: old
        vente(2, client = 2, at = "2026-10-01T09:00:00Z", total = 300.0, paid = 0.0, cost = 200.0)   // 6 days: recent

        val today = now.toLocalDate()
        val alerts = repository.dashboardAlerts(today, algiers)
        val stock = repository.stockReport(com.distrigo.app.data.model.report.ReportFilter(), today, algiers)
        val debts = repository.debtReport(DebtSide.CLIENTS, com.distrigo.app.data.model.report.ReportFilter(), today, algiers)
        assertEquals(DashboardAlerts(outOfStock = 2, lowStock = 1, oldClientDebt = 1000.0), alerts)
        assertEquals(stock.outOfStock, alerts.outOfStock)
        assertEquals(stock.lowStock, alerts.lowStock)
        assertEquals(debts.ages[AgeBand.OLD.ordinal], alerts.oldClientDebt, 0.0)
    }

    @Test
    fun thisMonthSoFar() = runBlocking {
        vente(1, client = 1, at = "2026-10-07T09:00:00Z", total = 1000.0, paid = 1000.0, cost = 800.0)
        vente(2, client = 1, at = "2026-09-30T23:30:00Z", total = 2000.0, paid = 2000.0, cost = 1500.0) // 00:30 on 1 October
        vente(3, client = 2, at = "2026-09-03T10:00:00Z", total = 3000.0, paid = 3000.0, cost = 2500.0) // September

        val d = repository.dashboardSales(now)
        assertEquals(3000.0, d.month.total, 0.0)
        assertEquals(700.0, d.month.margin, 0.0)
        assertEquals(700.0 / 2300.0, d.month.marginRate!!, 1e-9)
    }
}
