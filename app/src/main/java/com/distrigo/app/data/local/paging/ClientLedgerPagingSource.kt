package com.distrigo.app.data.local.paging

import com.distrigo.app.data.local.database.AppDatabase
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.distrigo.app.data.local.dao.ClientPaymentDao
import com.distrigo.app.data.local.dao.VenteDao
import com.distrigo.app.data.local.entity.ClientPaymentEntity
import com.distrigo.app.data.local.entity.VenteEntity
import com.distrigo.app.data.model.ClientTransaction
import com.distrigo.app.data.model.FactureFilter

/**
 * Merges `ventes` and `client_payments` — two separate tables, no shared ledger —
 * into a single chronological, searchable, status-filtered feed for one client.
 *
 * Each source is queried with its own keyset (created_at, id) cursor — the id tells apart
 * rows written in the same millisecond, which a created_at cursor alone skipped at a page
 * boundary — and buffered a
 * page ahead; load() interleaves the two buffers by created_at to produce a
 * correctly ordered page. Buffers/cursors live on the instance, which Paging3
 * recreates whenever the filter or search query changes (new PagingSource).
 * This is forward-only (no prevKey/jump support), matching the LazyColumn
 * infinite-scroll usage in PagedHistoryScreen.
 *
 * Live (see InvalidateOnWrite): a sale or a payment written anywhere reloads the feed, and the reload
 * reaches back down to where the list was, so a versement deleted deep in the history disappears
 * without the list jumping to the top. The key is how many rows come before a page.
 */
class ClientLedgerPagingSource(
    db: AppDatabase,
    private val venteDao: VenteDao,
    private val paymentDao: ClientPaymentDao,
    private val clientId: Int,
    private val filter: FactureFilter,
    private val search: String
) : PagingSource<Int, ClientTransaction>() {

    private val live = InvalidateOnWrite(db, arrayOf("ventes", "client_payments"), this)
    /** Rows handed out so far: the next page's key. */
    private var loaded = 0

    private val includeVentes = filter != FactureFilter.VERSEMENT
    private val includePaiements = filter == FactureFilter.TOUTES || filter == FactureFilter.VERSEMENT
    private val statusFilter = if (filter == FactureFilter.VERSEMENT) "TOUTES" else filter.name

    private val venteBuffer = ArrayDeque<VenteEntity>()
    private val paiementBuffer = ArrayDeque<ClientPaymentEntity>()
    private var venteCursor: String? = null
    private var venteCursorId: Int? = null
    private var paiementCursor: String? = null
    private var paiementCursorId: Int? = null
    private var venteExhausted = !includeVentes
    private var paiementExhausted = !includePaiements

    private suspend fun refillVenteBuffer(target: Int) {
        while (venteBuffer.size < target && !venteExhausted) {
            val batch = venteDao.pageVentesForClient(clientId, venteCursor, venteCursorId, search, statusFilter, FETCH_BATCH)
            if (batch.isEmpty()) {
                venteExhausted = true
            } else {
                venteBuffer.addAll(batch)
                venteCursor = batch.last().created_at
                venteCursorId = batch.last().id
                if (batch.size < FETCH_BATCH) venteExhausted = true
            }
        }
    }

    private suspend fun refillPaiementBuffer(target: Int) {
        while (paiementBuffer.size < target && !paiementExhausted) {
            val batch = paymentDao.pagePaymentsForClient(clientId, paiementCursor, paiementCursorId, search, FETCH_BATCH)
            if (batch.isEmpty()) {
                paiementExhausted = true
            } else {
                paiementBuffer.addAll(batch)
                paiementCursor = batch.last().created_at
                paiementCursorId = batch.last().id
                if (batch.size < FETCH_BATCH) paiementExhausted = true
            }
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ClientTransaction> {
        return try {
            live.start()
            // A refresh after a write asks for every row down to where the list was (getRefreshKey).
            val limit = if (params is LoadParams.Refresh && params.key != null) maxOf(params.loadSize, params.key!!) else params.loadSize
            refillVenteBuffer(limit)
            refillPaiementBuffer(limit)

            val page = mutableListOf<ClientTransaction>()
            while (page.size < limit && (venteBuffer.isNotEmpty() || paiementBuffer.isNotEmpty())) {
                val nextVente = venteBuffer.firstOrNull()
                val nextPaiement = paiementBuffer.firstOrNull()
                val takeVente = nextVente != null &&
                    (nextPaiement == null || nextVente.created_at >= nextPaiement.created_at)

                if (takeVente) {
                    venteBuffer.removeFirst()
                    page += nextVente!!.toClientTransaction()
                } else if (nextPaiement != null) {
                    paiementBuffer.removeFirst()
                    page += nextPaiement.toClientTransaction()
                }
            }

            val hasMore = venteBuffer.isNotEmpty() || paiementBuffer.isNotEmpty() ||
                !venteExhausted || !paiementExhausted

            loaded += page.size
            LoadResult.Page(
                data = page,
                prevKey = null,
                nextKey = if (hasMore && page.isNotEmpty()) loaded else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ClientTransaction>): Int? =
        InvalidateOnWrite.refreshCount(state.anchorPosition, state.config.initialLoadSize)

    companion object {
        private const val FETCH_BATCH = 20
    }
}

private fun VenteEntity.toClientTransaction() = ClientTransaction(
    type = "vente", id = id, amount = null,
    total = total, montant_paye = montant_paye,
    status = status, note = note, created_at = created_at, numero = numero
)

private fun ClientPaymentEntity.toClientTransaction() = ClientTransaction(
    type = "paiement", id = id, amount = amount,
    total = null, montant_paye = null, status = null,
    note = note, created_at = created_at
)