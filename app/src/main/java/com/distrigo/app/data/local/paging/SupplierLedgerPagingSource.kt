package com.distrigo.app.data.local.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.distrigo.app.data.local.dao.PurchaseDao
import com.distrigo.app.data.local.dao.SupplierDao
import com.distrigo.app.data.local.dao.SupplierPaymentDao
import com.distrigo.app.data.local.entity.PurchaseOrderEntity
import com.distrigo.app.data.local.entity.SupplierPaymentEntity
import com.distrigo.app.data.model.AchatFilter
import com.distrigo.app.data.model.SupplierTransaction

/**
 * Merges `purchase_orders`, `supplier_payments` and a synthetic one-shot
 * "solde_initial" row (the supplier's starting balance, not backed by any
 * table) into a single chronological, searchable, status-filtered feed —
 * the supplier-side counterpart of ClientLedgerPagingSource.
 *
 * The solde_initial row only ever appears under AchatFilter.TOUTES with a
 * blank search (it has no invoice/payment identity to filter or search on),
 * and is emitted at most once per PagingSource instance.
 */
class SupplierLedgerPagingSource(
    private val purchaseDao: PurchaseDao,
    private val paymentDao: SupplierPaymentDao,
    private val supplierDao: SupplierDao,
    private val supplierId: Int,
    private val filter: AchatFilter,
    private val search: String
) : PagingSource<Int, SupplierTransaction>() {

    private val includeOrders = filter != AchatFilter.VERSEMENT
    private val includePaiements = filter == AchatFilter.TOUTES || filter == AchatFilter.VERSEMENT
    private val includeSoldeInitial = filter == AchatFilter.TOUTES && search.isBlank()
    private val statusFilter = if (filter == AchatFilter.VERSEMENT) "TOUTES" else filter.name

    private val orderBuffer = ArrayDeque<PurchaseOrderEntity>()
    private val paiementBuffer = ArrayDeque<SupplierPaymentEntity>()
    private var orderCursor: String? = null
    private var paiementCursor: String? = null
    private var orderExhausted = !includeOrders
    private var paiementExhausted = !includePaiements

    private var soldeInitialTx: SupplierTransaction? = null
    private var soldeInitialLoaded = false

    private suspend fun refillOrderBuffer(target: Int) {
        while (orderBuffer.size < target && !orderExhausted) {
            val batch = purchaseDao.pageOrdersForSupplier(supplierId, orderCursor, search, statusFilter, FETCH_BATCH)
            if (batch.isEmpty()) {
                orderExhausted = true
            } else {
                orderBuffer.addAll(batch)
                orderCursor = batch.last().created_at
                if (batch.size < FETCH_BATCH) orderExhausted = true
            }
        }
    }

    private suspend fun refillPaiementBuffer(target: Int) {
        while (paiementBuffer.size < target && !paiementExhausted) {
            val batch = paymentDao.pagePaymentsForSupplier(supplierId, paiementCursor, search, FETCH_BATCH)
            if (batch.isEmpty()) {
                paiementExhausted = true
            } else {
                paiementBuffer.addAll(batch)
                paiementCursor = batch.last().created_at
                if (batch.size < FETCH_BATCH) paiementExhausted = true
            }
        }
    }

    private suspend fun ensureSoldeInitialLoaded() {
        if (soldeInitialLoaded || !includeSoldeInitial) return
        soldeInitialLoaded = true
        val supplier = supplierDao.getSupplierById(supplierId) ?: return
        if (supplier.initial_balance == 0.0) return
        soldeInitialTx = SupplierTransaction(
            type = "solde_initial", id = supplier.id, amount = supplier.initial_balance,
            montant_paye = null, status = null, note = null, created_at = supplier.created_at
        )
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SupplierTransaction> {
        return try {
            val limit = params.loadSize
            refillOrderBuffer(limit)
            refillPaiementBuffer(limit)
            ensureSoldeInitialLoaded()

            val page = mutableListOf<SupplierTransaction>()
            while (page.size < limit &&
                (orderBuffer.isNotEmpty() || paiementBuffer.isNotEmpty() || soldeInitialTx != null)
            ) {
                val nextOrder = orderBuffer.firstOrNull()
                val nextPaiement = paiementBuffer.firstOrNull()
                val nextSolde = soldeInitialTx

                val orderDate = nextOrder?.created_at
                val paiementDate = nextPaiement?.created_at
                val soldeDate = nextSolde?.created_at

                val takeOrder = orderDate != null &&
                    (paiementDate == null || orderDate >= paiementDate) &&
                    (soldeDate == null || orderDate >= soldeDate)
                val takePaiement = !takeOrder && paiementDate != null &&
                    (soldeDate == null || paiementDate >= soldeDate)

                when {
                    takeOrder -> { orderBuffer.removeFirst(); page += nextOrder!!.toSupplierTransaction() }
                    takePaiement -> { paiementBuffer.removeFirst(); page += nextPaiement!!.toSupplierTransaction() }
                    nextSolde != null -> { soldeInitialTx = null; page += nextSolde }
                    else -> break
                }
            }

            val hasMore = orderBuffer.isNotEmpty() || paiementBuffer.isNotEmpty() || soldeInitialTx != null ||
                !orderExhausted || !paiementExhausted

            LoadResult.Page(
                data = page,
                prevKey = null,
                nextKey = if (hasMore && page.isNotEmpty()) (params.key ?: 0) + page.size else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, SupplierTransaction>): Int? = null

    companion object {
        private const val FETCH_BATCH = 20
    }
}

private fun PurchaseOrderEntity.toSupplierTransaction() = SupplierTransaction(
    type = "facture", id = id, amount = total,
    montant_paye = montant_paye, status = status, note = note, created_at = created_at
)

private fun SupplierPaymentEntity.toSupplierTransaction() = SupplierTransaction(
    type = "paiement", id = id, amount = amount,
    montant_paye = null, status = null, note = note, created_at = created_at
)
