package com.distrigo.app.data.local.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import com.distrigo.app.data.local.dao.PurchaseOrderListRow
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.PurchaseOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Achats list, a page at a time, newest first.
 *
 * **Keyset, not offset.** Each page starts after the last bon of the one before it, by
 * `(created_at, id)` — see [PurchaseOrderCursor]. A page costs the same at the bottom of five years of
 * bons as at the top, where `OFFSET` would read and discard every row above it.
 *
 * **A refresh keeps the rows where they were.** A refresh — a bon received, edited or deleted, here or
 * on another screen — reloads from the newest bon down to just past the one in view, in one query, so
 * every row is back at the same position. That is what the list needs: coming back from a bon's detail
 * screen, it restores its scroll position by row number, not by bon, and a refresh that restarted
 * part-way down put a different bon at that number — the list came back further down than it left.
 *
 * **Live.** Room re-runs its own paged queries when a table they read changes; a raw query gets no
 * such thing for free, so this watches `purchase_orders`, its lines and `suppliers` itself and
 * invalidates when any of them is written. That replaces the `loadOrders()` calls every command used
 * to end with.
 */
class PurchaseOrderPagingSource(
    private val db   : AppDatabase,
    private val query: PurchaseOrderListQuery,
) : PagingSource<PurchaseOrderPagingSource.Key, PurchaseOrder>() {

    /** Where a load starts. */
    sealed interface Key {
        /** Rows older than [cursor] — the next page down. */
        data class After(val cursor: PurchaseOrderCursor) : Key
        /** The first [count] rows — a refresh that has to reach back down to where the list was. */
        data class Top(val count: Int) : Key
    }

    private val observer = object : InvalidationTracker.Observer(arrayOf("purchase_orders", "purchase_order_items", "suppliers")) {
        override fun onInvalidated(tables: Set<String>) = invalidate()
    }
    private val observing = AtomicBoolean(false)

    init {
        registerInvalidatedCallback {
            if (observing.get()) db.invalidationTracker.removeObserver(observer)
        }
    }

    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, PurchaseOrder> = try {
        // Registered on the first load, off the main thread, as Room's own paging sources do.
        if (observing.compareAndSet(false, true)) {
            withContext(Dispatchers.IO) { db.invalidationTracker.addObserver(observer) }
        }
        val key = params.key
        val limit = if (key is Key.Top) maxOf(key.count, params.loadSize) else params.loadSize
        val after = (key as? Key.After)?.cursor
        val rows = db.purchaseDao().pageOrders(PurchaseOrderListSql.page(query, after, limit))
        LoadResult.Page(
            data    = rows.map { it.toPurchaseOrder() },
            prevKey = null,
            nextKey = if (rows.size < limit) null else Key.After(rows.last().cursor),
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    /**
     * How far down a refresh reads: past the row the list last read by half an initial load, so the
     * rows on screen — which are above that row by up to a screenful plus the prefetch distance — are
     * all back, at the positions they had. Capped, so a list scrolled thousands of bons deep reloads
     * a bounded amount; past the cap it comes back nearer the top.
     */
    override fun getRefreshKey(state: PagingState<Key, PurchaseOrder>): Key? {
        val anchor = state.anchorPosition ?: return null
        return Key.Top((anchor + state.config.initialLoadSize / 2).coerceAtMost(MAX_REFRESH_ROWS))
    }

    private val PurchaseOrderListRow.cursor: PurchaseOrderCursor
        get() = PurchaseOrderCursor(order.created_at, order.id)

    private companion object {
        /** The most rows a refresh reloads — a few hundred KB of list rows, one indexed query. */
        const val MAX_REFRESH_ROWS = 5_000
    }
}

/** A list row as the list shows it: no lines, only their count — the detail screen loads the lines. */
internal fun PurchaseOrderListRow.toPurchaseOrder() = PurchaseOrder(
    id                 = order.id,
    date               = order.date,
    total              = order.total,
    status             = order.status,
    note               = order.note,
    supplier_id        = order.supplier_id,
    supplier_name      = display_supplier_name,
    items_count        = items_count,
    created_at         = order.created_at,
    items              = null,
    montant_paye       = order.montant_paye,
    supplier_image_uri = order.supplier_image_uri,
    numero             = order.numero,
)
