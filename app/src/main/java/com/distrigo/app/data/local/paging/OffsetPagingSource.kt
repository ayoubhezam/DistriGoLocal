package com.distrigo.app.data.local.paging

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.distrigo.app.data.local.database.AppDatabase

/**
 * A list read by position, with placeholders: the client and supplier lists and pickers (PERF-01).
 *
 * Their pickers fold clients by secteur and show only the open folds, so the list is reached by index — the first
 * client of the third fold is row 57 — rather than scrolled through from the top. Placeholders give every row its
 * index up front, and an index far from what is loaded jumps there. A list of parties is a few thousand rows at most,
 * so OFFSET costs little where a sale history would have needed a keyset ([VentePagingSource]).
 *
 * Live: it invalidates when [tables] are written, and a refresh reloads only around the row in view — a sale rewrites
 * its client's balance, and reloads that page, not the whole table as the old flows did.
 */
class OffsetPagingSource<T : Any>(
    db: AppDatabase,
    tables: Array<String>,
    private val count: suspend () -> Int,
    private val rows: suspend (offset: Int, limit: Int) -> List<T>,
) : PagingSource<Int, T>() {

    private val live = InvalidateOnWrite(db, tables, this)

    override val jumpingSupported: Boolean get() = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> = try {
        live.start()
        val total = count()
        val key = params.key ?: 0
        val (offset, limit) = when (params) {
            // Centred on the row asked for, so a jump lands with rows on both sides of it
            is LoadParams.Refresh -> (key - params.loadSize / 2).coerceIn(0, maxOf(0, total - params.loadSize)) to params.loadSize
            is LoadParams.Prepend -> maxOf(0, key - params.loadSize).let { it to key - it }
            is LoadParams.Append  -> key to params.loadSize
        }
        val data = if (limit <= 0) emptyList() else rows(offset, limit)
        val end = offset + data.size
        LoadResult.Page(
            data       = data,
            prevKey    = offset.takeIf { it > 0 },
            nextKey    = end.takeIf { data.isNotEmpty() && it < total },
            itemsBefore = offset,
            itemsAfter  = maxOf(0, total - end),
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? = state.anchorPosition

    companion object {
        /** Placeholders on, and a jump when a fold opens far from what is loaded. */
        val config = PagingConfig(pageSize = 30, prefetchDistance = 10, enablePlaceholders = true, initialLoadSize = 60, jumpThreshold = 90)
    }
}
