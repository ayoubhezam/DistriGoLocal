package com.distrigo.app.data.local.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.distrigo.app.data.local.dao.InventorySessionListRow
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.InventoryItemEntity
import com.distrigo.app.data.model.InventoryItem
import com.distrigo.app.data.model.InventorySessionHistory

/**
 * The inventory history, a page of sessions at a time, newest first — keyset on the session's date
 * and id, and a refresh that reloads from the top down to just past the row in view, as
 * [PurchaseOrderPagingSource] does, so the list comes back where it was.
 *
 * **Not live on its own.** The other paged lists watch their tables; this one cannot usefully: every
 * line a count records also touches its session row (the sync triggers), so watching either table
 * would re-sum the history after each scan of a count in progress. It is invalidated where the app
 * already reloaded the history — leaving a count, or finishing one.
 */
class InventorySessionPagingSource(
    private val db   : AppDatabase,
    private val query: InventorySessionListQuery,
) : PagingSource<InventorySessionPagingSource.Key, InventorySessionHistory>() {

    sealed interface Key {
        /** Sessions older than [cursor] — the next page down. */
        data class After(val cursor: InventorySessionCursor) : Key
        /** The first [count] sessions — a refresh reaching back down to where the list was. */
        data class Top(val count: Int) : Key
    }

    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, InventorySessionHistory> = try {
        val key = params.key
        val limit = if (key is Key.Top) maxOf(key.count, params.loadSize) else params.loadSize
        val rows: List<InventorySessionListRow> =
            db.inventoryDao().pageSessions(InventoryListSql.sessionPage(query, (key as? Key.After)?.cursor, limit))
        LoadResult.Page(
            data    = rows.map { it.toHistory() },
            prevKey = null,
            nextKey = if (rows.size < limit) null else Key.After(rows.last().let { InventorySessionCursor(it.sort_at, it.id) }),
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    /** Past the last row read by half an initial load, capped — see [PurchaseOrderPagingSource.getRefreshKey]. */
    override fun getRefreshKey(state: PagingState<Key, InventorySessionHistory>): Key? {
        val anchor = state.anchorPosition ?: return null
        return Key.Top((anchor + state.config.initialLoadSize / 2).coerceAtMost(MAX_REFRESH_ROWS))
    }

    private companion object {
        const val MAX_REFRESH_ROWS = 5_000
    }
}

/**
 * One session's lines, a page at a time, newest scanned first. The detail is only opened for a
 * finished session, whose lines no longer change, so there is nothing to watch.
 */
class InventoryItemPagingSource(
    private val db        : AppDatabase,
    private val sessionId : Int,
    private val toItem    : (InventoryItemEntity) -> InventoryItem,
) : PagingSource<InventoryItemCursor, InventoryItem>() {

    override suspend fun load(params: LoadParams<InventoryItemCursor>): LoadResult<InventoryItemCursor, InventoryItem> = try {
        val rows = db.inventoryDao().pageItems(InventoryListSql.itemPage(sessionId, params.key, params.loadSize))
        LoadResult.Page(
            data    = rows.map(toItem),
            prevKey = null,
            nextKey = if (rows.size < params.loadSize) null else rows.last().let { InventoryItemCursor(it.created_at, it.id) },
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    /** From the top: nothing invalidates it but a new Pager. */
    override fun getRefreshKey(state: PagingState<InventoryItemCursor, InventoryItem>): InventoryItemCursor? = null
}
