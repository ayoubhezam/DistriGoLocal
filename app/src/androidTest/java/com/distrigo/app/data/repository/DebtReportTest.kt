package com.distrigo.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportPeriod
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Créances et dettes SQL on a real database, on 3 October 2026 in Algeria, "Ce mois" running from
 * 2026-09-30T23:00Z. Balances are set as recomputeBalance would leave them.
 */
@RunWith(AndroidJUnit4::class)
class DebtReportTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.parse("2026-10-03")
    private val algiers = ZoneId.of("Africa/Algiers")
    private val month = ReportFilter(ReportPeriod.CE_MOIS)
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        sql = db.openHelper.writableDatabase
    }

    @After
    fun close() = db.close()

    private fun client(id: Int, name: String, balance: Double, deletedAt: Long? = null) = sql.execSQL(
        "INSERT INTO clients (id, name, phone, wilaya_name, commune_name, secteur_id, secteur_name, address, note, balance, " +
            "customer_type, image_uri, latitude, longitude, uuid, deleted_at) VALUES ($id, '$name', NULL, NULL, NULL, NULL, NULL, " +
            "NULL, NULL, $balance, 'retail', NULL, NULL, NULL, 'u-client-$id', ${deletedAt ?: "NULL"})"
    )

    private var venteId = 0
    private fun vente(client: Int, at: String, total: Double, paid: Double) = sql.execSQL(
        "INSERT INTO ventes (id, client_id, tournee_id, source, total, montant_paye, status, note, created_at, uuid) " +
            "VALUES (${++venteId}, $client, NULL, 'depot', $total, $paid, 'pending', NULL, '$at', 'u-vente-$venteId')"
    )

    private var paymentId = 0
    private fun clientPayment(client: Int, at: String, amount: Double) = sql.execSQL(
        "INSERT INTO client_payments (id, client_id, amount, note, created_at, uuid) VALUES (${++paymentId}, $client, $amount, NULL, '$at', 'u-cp-$paymentId')"
    )

    private fun supplier(id: Int, name: String, balance: Double, initial: Double) = sql.execSQL(
        "INSERT INTO suppliers (id, name, phone, address, note, balance, initial_balance, latitude, longitude, wilaya_name, " +
            "commune_name, created_at, uuid) VALUES ($id, '$name', NULL, NULL, NULL, $balance, $initial, NULL, NULL, NULL, NULL, " +
            "'2026-01-01T00:00:00Z', 'u-supplier-$id')"
    )

    private var bonId = 0
    private fun bon(supplier: Int, date: String, total: Double, paid: Double) = sql.execSQL(
        "INSERT INTO purchase_orders (id, supplier_id, date, total, status, note, montant_paye, created_at, uuid) " +
            "VALUES (${++bonId}, $supplier, '$date', $total, 'received', NULL, $paid, '${date}T09:00:00Z', 'u-bon-$bonId')"
    )

    @Test
    fun clientsOweTheirNewestCreditAndTheMonthsFlowsAreCounted() = runBlocking {
        // Sofiane: 10 000 on credit today, then paid in full — settled, not a debtor.
        client(1, "Epicerie Sofiane", 0.0)
        vente(1, "2026-10-02T09:00:00Z", 10_000.0, 0.0)
        clientPayment(1, "2026-10-02T15:00:00Z", 10_000.0)
        // Karim: 3 000 on credit in June, 2 000 in September, 1 000 this month; paid 2 500 since. Owes 3 500.
        client(2, "Superette Karim", 3500.0)
        vente(2, "2026-06-10T09:00:00Z", 3000.0, 0.0)
        vente(2, "2026-09-15T09:00:00Z", 2000.0, 0.0)
        vente(2, "2026-10-01T09:00:00Z", 1500.0, 500.0)
        clientPayment(2, "2026-09-20T10:00:00Z", 2500.0)
        // A binned client is left out, whatever its row says.
        client(3, "Ancien client", 900.0, deletedAt = 1000)

        val report = db.reportDao().debtReport(DebtSide.CLIENTS, month, today, algiers)

        assertEquals(3500.0, report.outstanding, 0.0)
        assertEquals(listOf("Superette Karim"), report.debtors.map { it.name })
        // Newest first: 1 000 this month and 2 000 in September (both within 30 days), then 500 of June's.
        assertEquals(listOf(3000.0, 0.0, 0.0, 500.0), report.debtors.single().ages)
        assertEquals(AgeBand.OLD, report.debtors.single().oldest)
        assertEquals(LocalDate.parse("2026-09-20"), report.debtors.single().lastPayment)
        // October's flows: Sofiane's 10 000 and Karim's 1 000 left on credit; Sofiane's 10 000 paid.
        assertEquals(11_000.0, report.credit, 0.0)
        assertEquals(ReturnFigures(1, 10_000.0), report.payments)
        assertEquals(1000.0, report.change, 0.0)
    }

    @Test
    fun aSuppliersOpeningBalanceIsItsOldestDebt() = runBlocking {
        supplier(1, "Grossiste Sétif", balance = 5000.0, initial = 2000.0)
        bon(1, "2026-09-20", 4000.0, 1000.0)
        bon(1, "2026-10-01", 1000.0, 1000.0)   // paid in full: leaves no credit

        val report = db.reportDao().debtReport(DebtSide.FOURNISSEURS, month, today, algiers)

        assertEquals(5000.0, report.outstanding, 0.0)
        assertEquals(listOf(3000.0, 0.0, 0.0, 2000.0), report.debtors.single().ages)
        assertEquals(0.0, report.credit, 0.0)   // October's bon was paid at once
    }
}
