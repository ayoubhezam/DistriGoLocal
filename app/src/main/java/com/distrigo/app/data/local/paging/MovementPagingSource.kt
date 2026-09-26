package com.distrigo.app.data.local.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.mouvement.StockMovementEntity
import com.distrigo.app.data.model.StockMovement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A product's Mouvements, a page at a time, newest first — the same shape as [PurchaseOrderPagingSource]:
 * keyset on `(created_at, id)`, a refresh that reloads from the top down to just past the row in view,
 * and live through Room's InvalidationTracker on `stock_movements`, so a sale made elsewhere appears.
 */
class MovementPagingSource(
    private val db         : AppDatabase,
    private val query      : MovementListQuery,
    private val toMovement : (StockMovementEntity) -> StockMovement,
) : PagingSource<MovementPagingSource.Key, StockMovement>() {

    sealed interface Key {
        /** Movements older than [cursor] — the next page down. */
        data class After(val cursor: MovementCursor) : Key
        /** The first [count] movements — a refresh reaching back down to where the list was. */
        data class Top(val count: Int) : Key
    }

    private val observer = object : InvalidationTracker.Observer(arrayOf("stock_movements")) {
        override fun onInvalidated(tables: Set<String>) = invalidate()
    }
    private val observing = AtomicBoolean(false)

    init {
        registerInvalidatedCallback {
            if (observing.get()) db.invalidationTracker.removeObserver(observer)
        }
    }

    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, StockMovement> = try {
        if (observing.compareAndSet(false, true)) {
            withContext(Dispatchers.IO) { db.invalidationTracker.addObserver(observer) }
        }
        val key = params.key
        val limit = if (key is Key.Top) maxOf(key.count, params.loadSize) else params.loadSize
        val rows = db.stockMovementDao().pageMovements(MovementListSql.page(query, (key as? Key.After)?.cursor, limit))
        LoadResult.Page(
            data    = rows.map(toMovement),
            prevKey = null,
            nextKey = if (rows.size < limit) null else Key.After(rows.last().let { MovementCursor(it.created_at, it.id) }),
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    /** Past the last row read by half an initial load, capped — see [PurchaseOrderPagingSource.getRefreshKey]. */
    override fun getRefreshKey(state: PagingState<Key, StockMovement>): Key? {
        val anchor = state.anchorPosition ?: return null
        return Key.Top((anchor + state.config.initialLoadSize / 2).coerceAtMost(MAX_REFRESH_ROWS))
    }

    private companion object {
        const val MAX_REFRESH_ROWS = 5_000
    }
}
