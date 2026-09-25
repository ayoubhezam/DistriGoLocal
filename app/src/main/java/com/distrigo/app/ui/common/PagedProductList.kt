package com.distrigo.app.ui.common

import androidx.compose.runtime.snapshotFlow
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.distrigo.app.data.local.paging.ProductListQuery
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * A product picker's list: the products a query matches, a page at a time, and how many there are.
 *
 * Every picker — Achats, Vente dépôt, Tournée vente, Chargement and the rest — used to collect the
 * whole catalogue and filter it in its composable. They differ only in their query, so they share this.
 *
 * Built by the form's session ViewModel, not by the destination: [items] is cached in the session's
 * scope, so leaving the picker for the cart and coming back finds the pages already loaded and the
 * list's saved scroll position on the same rows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PagedProductList(
    scope     : CoroutineScope,
    repository: ProductRepository,
    query     : Flow<ProductListQuery>,
) {
    private val distinct = query.distinctUntilChanged()

    val items: Flow<PagingData<Product>> = distinct
        .flatMapLatest { repository.pageProducts(it) }
        .cachedIn(scope)

    /** Null until the first count lands. */
    val count: StateFlow<Int?> = distinct
        .flatMapLatest { repository.observeProductCount(it) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * A search box's text as a flow, waiting for typing to pause before it moves on — but not after it is
 * cleared, which should show everything at once.
 */
@OptIn(FlowPreview::class)
fun debouncedSearch(read: () -> String): Flow<String> =
    snapshotFlow(read).debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

private const val SEARCH_DEBOUNCE_MS = 300L
