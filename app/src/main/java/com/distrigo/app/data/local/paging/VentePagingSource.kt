package com.distrigo.app.data.local.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import com.distrigo.app.data.local.dao.VenteListRow
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.Vente
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Ventes list, a page at a time, newest first — the same shape as [PurchaseOrderPagingSource]:
 * keyset on `(created_at, id)`, a refresh that reloads from the top down to just past the row in view
 * so the list keeps its place, and live through Room's InvalidationTracker on `ventes`, their lines and
 * `clients`, which replaces the `loadVentes()` every command used to end with.
 */
class VentePagingSource(
    private val db     : AppDatabase,
    private val query  : VenteListQuery,
    private val toVente: (VenteListRow) -> Vente,
) : PagingSource<VentePagingSource.Key, Vente>() {

    sealed interface Key {
        /** Sales older than [cursor] — the next page down. */
        data class After(val cursor: VenteCursor) : Key
        /** The first [count] sales — a refresh reaching back down to where the list was. */
        data class Top(val count: Int) : Key
    }

    private val observer = object : InvalidationTracker.Observer(arrayOf("ventes", "vente_items", "clients")) {
        override fun onInvalidated(tables: Set<String>) = invalidate()
    }
    private val observing = AtomicBoolean(false)

    init {
        registerInvalidatedCallback {
            if (observing.get()) db.invalidationTracker.removeObserver(observer)
        }
    }

    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, Vente> = try {
        if (observing.compareAndSet(false, true)) {
            withContext(Dispatchers.IO) { db.invalidationTracker.addObserver(observer) }
        }
        val key = params.key
        val limit = if (key is Key.Top) maxOf(key.count, params.loadSize) else params.loadSize
        val rows = db.venteDao().pageVentes(VenteListSql.page(query, (key as? Key.After)?.cursor, limit))
        LoadResult.Page(
            data    = rows.map(toVente),
            prevKey = null,
            nextKey = if (rows.size < limit) null else Key.After(rows.last().let { VenteCursor(it.vente.created_at, it.vente.id) }),
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    /** Past the last row read by half an initial load, capped — see [PurchaseOrderPagingSource.getRefreshKey]. */
    override fun getRefreshKey(state: PagingState<Key, Vente>): Key? {
        val anchor = state.anchorPosition ?: return null
        return Key.Top((anchor + state.config.initialLoadSize / 2).coerceAtMost(MAX_REFRESH_ROWS))
    }

    private companion object {
        const val MAX_REFRESH_ROWS = 5_000
    }
}
