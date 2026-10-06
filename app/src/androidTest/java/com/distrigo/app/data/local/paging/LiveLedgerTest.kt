package com.distrigo.app.data.local.paging

import com.distrigo.app.data.local.entity.SupplierPaymentEntity
import com.distrigo.app.data.local.entity.ClientPaymentEntity
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
        val payment = db.clientPaymentDao().pagePaymentsForClient(client, null, null, "", 10).first().id

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
        val payment = db.clientPaymentDao().pagePaymentsForClient(client, null, null, "", 10).first().id

        val source = ClientLedgerPagingSource(db, db.venteDao(), db.clientPaymentDao(), client, FactureFilter.TOUTES, "")
        source.load(PagingSource.LoadParams.Refresh(null, 20, false))

        repository.updateClientPayment(client, payment, 650.0)
        assertTrue("the ledger reloads after an edit", becomesInvalid(source))
    }

    @Test
    fun deletingAPaymentInvalidatesTheSupplierLedger() = runBlocking {
        val supplier = (repository.addSupplier(mapOf("name" to "Laiterie Soummam"))["id"] as Number).toInt()
        repository.addSupplierPayment(supplier, 300.0, null)
        val payment = db.supplierPaymentDao().pagePaymentsForSupplier(supplier, null, null, "", 10).first().id

        val source = SupplierLedgerPagingSource(db, db.purchaseDao(), db.supplierPaymentDao(), db.supplierDao(), supplier, AchatFilter.TOUTES, "")
        source.load(PagingSource.LoadParams.Refresh(null, 20, false))

        repository.deleteSupplierPayment(supplier, payment)
        assertTrue("the ledger reloads after a delete", becomesInvalid(source))
    }

    /** A refresh keyed by getRefreshKey loads every row down to where the list was, not one page. */
    @Test
    fun aRefreshReachesBackDownToWhereTheListWas() = runBlocking {
        val client = (repository.addClient(mapOf("name" to "Épicerie El Amel"))["id"] as Number).toInt()
        repeat(45) { repository.addClientPayment(client, 100.0 + it, null) }

        val source = ClientLedgerPagingSource(db, db.venteDao(), db.clientPaymentDao(), client, FactureFilter.TOUTES, "")
        val page = source.load(PagingSource.LoadParams.Refresh(40, 20, false)) as PagingSource.LoadResult.Page
        assertEquals(40, page.data.size)
        assertEquals(40, page.nextKey)
        val next = source.load(PagingSource.LoadParams.Append(40, 20, false)) as PagingSource.LoadResult.Page
        assertEquals(5, next.data.size)
        assertEquals(null, next.nextKey)
    }

    /**
     * Rows written in the same millisecond — a burst of entries, generated or imported data — are all
     * paged, each once. With a created_at cursor alone, the rows sharing the last one's instant were
     * skipped at every page boundary.
     */
    @Test
    fun rowsWrittenInTheSameMillisecondAreAllPaged() = runBlocking {
        val client = (repository.addClient(mapOf("name" to "Épicerie El Amel"))["id"] as Number).toInt()
        val supplier = (repository.addSupplier(mapOf("name" to "Laiterie Soummam"))["id"] as Number).toInt()
        repeat(45) {
            db.clientPaymentDao().insertPayment(ClientPaymentEntity(client_id = client, amount = 10.0 + it, note = null, created_at = SAME_INSTANT))
            db.supplierPaymentDao().insertPayment(SupplierPaymentEntity(supplier_id = supplier, amount = 10.0 + it, note = null, created_at = SAME_INSTANT))
        }

        val clientIds = pageAll(ClientLedgerPagingSource(db, db.venteDao(), db.clientPaymentDao(), client, FactureFilter.TOUTES, "")).map { it.id }
        assertEquals(45, clientIds.size)
        assertEquals(45, clientIds.toSet().size)

        val supplierIds = pageAll(SupplierLedgerPagingSource(db, db.purchaseDao(), db.supplierPaymentDao(), db.supplierDao(), supplier, AchatFilter.TOUTES, ""))
            .filter { it.type == "paiement" }.map { it.id }
        assertEquals(45, supplierIds.size)
        assertEquals(45, supplierIds.toSet().size)
    }

    /** Every page of [source], 20 at a time, as the list scrolls. */
    private suspend fun <T : Any> pageAll(source: PagingSource<Int, T>): List<T> {
        val rows = mutableListOf<T>()
        var key: Int? = null
        do {
            val params: PagingSource.LoadParams<Int> =
                if (key == null) PagingSource.LoadParams.Refresh(null, 20, false) else PagingSource.LoadParams.Append(key, 20, false)
            val page = source.load(params) as PagingSource.LoadResult.Page
            rows += page.data
            key = page.nextKey
        } while (key != null)
        return rows
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

    private companion object {
        const val SAME_INSTANT = "2026-09-28T08:00:00.123Z"
    }

    /** The ledger's search matches every word of it, in any order — as the app's other searches do. */
    @Test
    fun theLedgerSearchMatchesEveryWordInAnyOrder() = runBlocking {
        val client = (repository.addClient(mapOf("name" to "Épicerie El Amel"))["id"] as Number).toInt()
        repository.addClientPayment(client, 500.0, "versement espèces mars")
        repository.addClientPayment(client, 700.0, "chèque avril")
        val dao = db.clientPaymentDao()
        assertEquals(1, dao.countPaymentsForClient(client, "mars espèces"))
        assertEquals(1, dao.countPaymentsForClient(client, "  ESP  vers "))
        assertEquals(0, dao.countPaymentsForClient(client, "mars chèque"))
        assertEquals(2, dao.countPaymentsForClient(client, ""))
        assertEquals(listOf(500.0), dao.pagePaymentsForClient(client, null, null, "vers mars", 10).map { it.amount })
    }
}
