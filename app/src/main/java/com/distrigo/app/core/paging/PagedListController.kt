package com.distrigo.app.core.paging

import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class PagedListController<Filter, T : Any>(
    scope: CoroutineScope,
    defaultFilter: Filter,
    searchDebounceMs: Long = 300L,
    pagerFactory: (filter: Filter, query: String) -> Flow<PagingData<T>>
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(defaultFilter)
    val filter: StateFlow<Filter> = _filter.asStateFlow()

    val items: Flow<PagingData<T>> =
        combine(
            _filter,
            _query.debounce(searchDebounceMs).distinctUntilChanged()
        ) { filter, query -> filter to query }
            .flatMapLatest { (filter, query) -> pagerFactory(filter, query) }
            .cachedIn(scope)

    fun onQueryChange(newQuery: String) { _query.value = newQuery }
    fun onFilterChange(newFilter: Filter) { _filter.value = newFilter }
}