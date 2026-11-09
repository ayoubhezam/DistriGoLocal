package com.distrigo.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.PurchaseOrderEntity
import com.distrigo.app.data.local.entity.VenteEntity
import com.distrigo.app.data.local.paging.PurchaseOrderListQuery
import com.distrigo.app.data.local.paging.PurchaseOrderListSql
import com.distrigo.app.data.local.paging.VenteListQuery
import com.distrigo.app.data.local.paging.VenteListSql
import com.distrigo.app.data.model.PaymentState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The « Payée / Partielle / Impayée » filters on SQLite itself, at the half-centime edges (FIN-05): the client and
 * supplier ledgers, the Ventes and Achats lists, and the Dashboard's « Activité récente ». Every filter must agree
 * with PaymentState.of row for row.
 */
@RunWith(AndroidJUnit4::class)
class PaymentStateSqlTest {

    private lateinit var db: AppDatabase

    /** (total, paid): each with the state PaymentState.of gives it. */
    private val rows = listOf(
        3 * 150.15 to 450.45,      // 450.45000000000005: settled
        0.0 to 0.0,                // nothing to pay (D2)
        1500.5 to 150050.0,        // paid ahead
        1000.0 to 999.996,         // 0,004 left: settled
        1000.0 to 0.0,
        1000.0 to 0.004,
        1000.0 to 0.005,
        1000.0 to 400.0,
    )

    private fun expected(state: PaymentState) = rows.count { (total, paid) -> PaymentState.of(total, paid) == state }

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext as Context
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        rows.forEachIndexed { i, (total, paid) ->
            val at = "2026-11-09T10:00:0${i}Z"
            db.venteDao().insertVente(
                VenteEntity(client_id = 1, tournee_id = null, source = "depot", total = total, montant_paye = paid,
                    status = "pending", note = null, created_at = at)
            )
            db.purchaseDao().insertOrder(
                PurchaseOrderEntity(supplier_id = 1, date = "2026-11-09", total = total, status = "pending", note = null,
                    montant_paye = paid, created_at = at)
            )
        }
    }

    @After
    fun tearDown() = db.close()

    private fun count(query: androidx.sqlite.db.SupportSQLiteQuery): Int =
        db.query(query).use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun theStatesAreTheOnesTheRuleGives() {
        // The table above, read by the rule itself: 4 paid, 2 unpaid, 2 partial
        assertEquals(4, expected(PaymentState.PAID))
        assertEquals(2, expected(PaymentState.UNPAID))
        assertEquals(2, expected(PaymentState.PARTIAL))
    }

    @Test
    fun ledgerFiltersAgreeWithTheRule() = runBlocking {
        val keys = mapOf(PaymentState.PAID to "PAYEE", PaymentState.PARTIAL to "PARTIELLE", PaymentState.UNPAID to "IMPAYEE")
        for ((state, key) in keys) {
            assertEquals(key, expected(state), db.venteDao().countVentesForClient(1, "", key))
            assertEquals(key, expected(state), db.purchaseDao().countOrdersForSupplier(1, "", key))
            assertEquals(key, expected(state), db.venteDao().pageVentesForClient(1, null, null, "", key, 50).size)
        }
        assertEquals(rows.size, db.venteDao().countVentesForClient(1, "", "TOUTES"))
    }

    @Test
    fun listFiltersAgreeWithTheRule() {
        for (state in PaymentState.entries) {
            val key = PaymentState.keyOf(state)
            assertEquals(key, expected(state), count(VenteListSql.count(VenteListQuery(paymentStatus = key))))
            assertEquals(key, expected(state), count(PurchaseOrderListSql.count(PurchaseOrderListQuery(paymentStatus = key))))
        }
    }

    @Test
    fun recentSalesSplitPaidFromCredit() = runBlocking {
        assertEquals(expected(PaymentState.PAID), db.reportDao().recentSales(1, 50).size)
        assertEquals(rows.size - expected(PaymentState.PAID), db.reportDao().recentSales(2, 50).size)
    }
}
