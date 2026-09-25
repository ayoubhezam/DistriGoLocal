package com.distrigo.app.data.local.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import com.distrigo.app.data.local.dao.ProductPageRow
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.ProductEntity
import com.distrigo.app.data.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A product list, a page at a time: the Produits screen and every product picker.
 *
 * The same shape as [PurchaseOrderPagingSource], for the same reasons:
 * - **keyset** on the sort value and the id, so a page deep in the catalogue costs what the first does;
 * - **a refresh reloads from the top down to just past the row in view**, so every row keeps its
 *   position — the lists restore their scroll position by index when the user comes back to them;
 * - **live**: it watches `products` and `product_barcodes` and invalidates when either is written.
 *   A sale moves stock, stock is a column of `products`, so a sale refreshes the page on screen — only
 *   that page and the rows above it, not the whole catalogue as the old flow did.
 *
 * Each page's barcodes come in one query for the whole page, not one per product.
 */
class ProductPagingSource(
    private val db       : AppDatabase,
    private val query    : ProductListQuery,
    private val toProduct: (ProductEntity, List<String>?) -> Product,
) : PagingSource<ProductPagingSource.Key, Product>() {

    /** Where a load starts. */
    sealed interface Key {
        /** Rows past [cursor] — the next page down. */
        data class After(val cursor: ProductCursor) : Key
        /** The first [count] rows — a refresh that has to reach back down to where the list was. */
        data class Top(val count: Int) : Key
    }

    private val observer = object : InvalidationTracker.Observer(arrayOf("products", "product_barcodes")) {
        override fun onInvalidated(tables: Set<String>) = invalidate()
    }
    private val observing = AtomicBoolean(false)

    init {
        registerInvalidatedCallback {
            if (observing.get()) db.invalidationTracker.removeObserver(observer)
        }
    }

    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, Product> = try {
        if (observing.compareAndSet(false, true)) {
            withContext(Dispatchers.IO) { db.invalidationTracker.addObserver(observer) }
        }
        val key = params.key
        val limit = if (key is Key.Top) maxOf(key.count, params.loadSize) else params.loadSize
        val rows = db.productDao().pageProducts(ProductListSql.page(query, (key as? Key.After)?.cursor, limit))
        val codes = if (rows.isEmpty()) emptyMap()
        else db.productBarcodeDao().getForProducts(rows.map { it.product.id })
            .groupBy({ it.product_id }, { it.code })
        LoadResult.Page(
            data    = rows.map { toProduct(it.product, codes[it.product.id]) },
            prevKey = null,
            nextKey = if (rows.size < limit) null else Key.After(rows.last().cursor()),
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    /** Past the last row read by half an initial load, capped — see [PurchaseOrderPagingSource.getRefreshKey]. */
    override fun getRefreshKey(state: PagingState<Key, Product>): Key? {
        val anchor = state.anchorPosition ?: return null
        return Key.Top((anchor + state.config.initialLoadSize / 2).coerceAtMost(MAX_REFRESH_ROWS))
    }

    /** The row's value in the column the list is sorted by, exactly as SQLite compares it. */
    private fun ProductPageRow.cursor(): ProductCursor = ProductCursor(
        sortValue = when (query.sort) {
            ProductSort.NEWEST                            -> product.id
            ProductSort.NAME_ASC, ProductSort.NAME_DESC   -> sort_name
            ProductSort.STOCK_ASC, ProductSort.STOCK_DESC -> product.stock
            ProductSort.PRICE_ASC, ProductSort.PRICE_DESC -> product.selling_price
        },
        id = product.id,
    )

    private companion object {
        const val MAX_REFRESH_ROWS = 5_000
    }
}
