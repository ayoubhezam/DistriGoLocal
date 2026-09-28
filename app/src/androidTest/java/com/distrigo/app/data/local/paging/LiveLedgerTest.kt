package com.distrigo.app.data.local.paging

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.model.AchatFilter
import com.distrigo.app.data.model.FactureFilter
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The client and supplier ledgers ("Factures & Paiements", "Achats & Paiements") follow the database:
 * once a page is loaded, deleting or editing a payment anywhere invalidates the source — a deleted
 * versement used to stay on screen — and a refresh reaches back down to where the list was.
 *
 * In memory, so nothing touches the app's database on the device.
 */
@RunWith(AndroidJUnit4::class)
class LiveLedgerTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ProductRepository

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        repository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
    }

    @After
    fun close() = db.close()

    @Test
    fun deletingAVersementInvalidatesTheClientLedger() = runBlocking {
        val client = (repository.addClient(mapOf("name" to "Épicerie El Amel"))["id"] as Number).toInt()
        repository.addClientPayment(client, 500.0, null)
        repository.addClientPayment(client, 700.0, null)
        val payment = db.clientPaymentDao().pagePaymentsForClient(client, null, "", 10).first().id

        val source = ClientLedgerPagingSource(db, db.venteDao(), db.clientPaymentDao(), client, FactureFilter.TOUTES, "")
        val page = source.load(PagingSource.LoadParams.Refresh(null, 20, false)) as PagingSource.LoadResult.Page
        assertEquals(2, page.data.size)
        assertFalse(source.invalid)

        repository.deleteClientPayment(client, payment)
        assertTrue("the ledger reloads after a delete", becomesInvalid(source))
    }

    @Test
    fun editingAVersementInvalidatesTheClientLedger() = runBlocking {
        val client = (repository.addClient(mapOf("name" to "Épicerie El Amel"))["id"] as Number).toInt()
        repository.addClientPayment(client, 500.0, null)
        val payment = db.clientPaymentDao().pagePaymentsForClient(client, null, "", 10).first().id

        val source = ClientLedgerPagingSource(db, db.venteDao(), db.clientPaymentDao(), client, FactureFilter.TOUTES, "")
        source.load(PagingSource.LoadParams.Refresh(null, 20, false))

        repository.updateClientPayment(client, payment, 650.0)
        assertTrue("the ledger reloads after an edit", becomesInvalid(source))
    }

    @Test
    fun deletingAPaymentInvalidatesTheSupplierLedger() = runBlocking {
        val supplier = (repository.addSupplier(mapOf("name" to "Laiterie Soummam"))["id"] as Number).toInt()
        repository.addSupplierPayment(supplier, 300.0, null)
        val payment = db.supplierPaymentDao().pagePaymentsForSupplier(supplier, null, "", 10).first().id

        val source = SupplierLedgerPagingSource(db, db.purchaseDao(), db.supplierPaymentDao(), db.supplierDao(), supplier, AchatFilter.TOUTES, "")
        source.load(PagingSource.LoadParams.Refresh(null, 20, false))

        repository.deleteSupplierPayment(supplier, payment)
        assertTrue("the ledger reloads after a delete", becomesInvalid(source))
    }

    /** A refresh keyed by getRefreshKey loads every row down to where the list was, not one page. */
    @Test
    fun aRefreshReachesBackDownToWhereTheListWas() = runBlocking {
        val client = (repository.addClient(mapOf("name" to "Épicerie El Amel"))["id"] as Number).toInt()
        // Apart by a few milliseconds: the ledger's cursor is created_at (see the DAO).
        repeat(45) { repository.addClientPayment(client, 100.0 + it, null); delay(3) }

        val source = ClientLedgerPagingSource(db, db.venteDao(), db.clientPaymentDao(), client, FactureFilter.TOUTES, "")
        val page = source.load(PagingSource.LoadParams.Refresh(40, 20, false)) as PagingSource.LoadResult.Page
        assertEquals(40, page.data.size)
        assertEquals(40, page.nextKey)
        val next = source.load(PagingSource.LoadParams.Append(40, 20, false)) as PagingSource.LoadResult.Page
        assertEquals(5, next.data.size)
        assertEquals(null, next.nextKey)
    }

    /** The result count's signal: it fires once at start, then on a write to the ledger's tables. */
    @Test
    fun theCountIsToldWhenTheLedgerChanges() = runBlocking {
        val client = (repository.addClient(mapOf("name" to "Épicerie El Amel"))["id"] as Number).toInt()
        val emissions = mutableListOf<Set<String>>()
        val job = launch { repository.clientLedgerChanges().take(2).toList(emissions) }
        delay(300)
        repository.addClientPayment(client, 500.0, null)
        withTimeout(3_000) { job.join() }
        assertEquals(2, emissions.size)
        assertTrue(emissions.last().contains("client_payments"))
    }

    private suspend fun becomesInvalid(source: PagingSource<*, *>): Boolean {
        repeat(40) {
            if (source.invalid) return true
            delay(50)
        }
        return source.invalid
    }
}
