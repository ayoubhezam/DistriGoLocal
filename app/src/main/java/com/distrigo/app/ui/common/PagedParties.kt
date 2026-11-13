package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import com.distrigo.app.data.local.paging.BalanceBand
import com.distrigo.app.data.local.paging.ClientListQuery
import com.distrigo.app.data.local.paging.SecteurCount
import com.distrigo.app.data.local.paging.SupplierListQuery
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * A client picker's list (PERF-01): the clients its search matches, by secteur, a page at a time, and how many are in
 * each secteur. Every picker used to hold the whole table, mapped again after every sale; this reads the folds' counts
 * and the rows in view.
 *
 * Built by a ViewModel, so its pages are cached in that ViewModel's scope.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientPickerList(scope: CoroutineScope, private val repository: ProductRepository, private val base: ClientListQuery) {

    /** The search box, as typed. */
    var search by mutableStateOf("")

    private val query: Flow<ClientListQuery> = debouncedSearch { search }
        .map { base.copy(search = it, bySecteur = true) }
        .distinctUntilChanged()

    val items: Flow<PagingData<Client>> = query.flatMapLatest { repository.pageClients(it) }.cachedIn(scope)

    /** The folds, in the list's order; null until the first count lands. */
    val groups: StateFlow<List<SecteurCount>?> = query
        .flatMapLatest { repository.observeSecteurCounts(it) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** The ids of a fold's clients, for « Tout sélectionner ». */
    suspend fun idsIn(secteur: String?): List<Int> = repository.clientIdsInSecteur(base.copy(search = search.trim()), secteur)
}

/** The supplier picker's list (PERF-01): the suppliers its search matches, a page at a time. */
@OptIn(ExperimentalCoroutinesApi::class)
class SupplierPickerList(scope: CoroutineScope, repository: ProductRepository) {

    var search by mutableStateOf("")

    val items: Flow<PagingData<Supplier>> = debouncedSearch { search }
        .map { SupplierListQuery(search = it) }
        .distinctUntilChanged()
        .flatMapLatest { repository.pageSuppliers(it) }
        .cachedIn(scope)
}

/** A fold's key in the picker's state: its name, or [SANS_SECTEUR]. */
fun SecteurCount.key(): String = secteur ?: SANS_SECTEUR

/**
 * The folds of a client picker and the rows of those open, from [items] by position: a fold's clients follow the
 * folds before it, in the order [groups] lists them. A row not loaded yet is a [PartyRowPlaceholder] of its height.
 *
 * Rows are keyed by fold and position, not by client: while a count and a page are a moment apart after a write, a
 * client can show at two positions, and two equal keys would end the list.
 */
fun LazyListScope.secteurFolds(
    groups     : List<SecteurCount>,
    items      : LazyPagingItems<Client>,
    isOpen     : (SecteurCount) -> Boolean,
    showHeaders: Boolean,
    header     : @Composable LazyItemScope.(SecteurCount) -> Unit,
    row        : @Composable LazyItemScope.(Client) -> Unit,
) {
    var start = 0
    groups.forEach { group ->
        val first = start
        start += group.count
        if (showHeaders) item(key = "secteur_${group.key()}", contentType = "secteur") { header(group) }
        if (isOpen(group)) {
            items(count = group.count, key = { "client_${group.key()}_$it" }, contentType = { "client" }) { i ->
                val index = first + i
                val client = if (index < items.itemCount) items[index] else null
                if (client == null) PartyRowPlaceholder() else row(client)
            }
        }
    }
}

/** Where a row will be once its page is read. */
@Composable
fun PartyRowPlaceholder(height: Dp = 70.dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(DsShapes.large).background(DsColors.Surface))
}

/** The filter sheet's solde, as the query reads it. */
fun BalanceFilter.band(): BalanceBand = when (this) {
    BalanceFilter.OWING   -> BalanceBand.OWING
    BalanceFilter.SETTLED -> BalanceBand.SETTLED
    BalanceFilter.ADVANCE -> BalanceBand.ADVANCE
}

/** The Clients list's sheet and search, as a query. */
fun ClientListFilters.toQuery(search: String, outsideQuartier: Int?): ClientListQuery = ClientListQuery(
    search = search, type = type, wilaya = wilaya, commune = commune, secteur = secteur, balance = balance?.band(),
    withoutQuartier = withoutQuartier, outsideQuartier = outsideQuartier,
)

/** The Fournisseurs list's sheet and search, as a query. */
fun SupplierListFilters.toQuery(search: String): SupplierListQuery =
    SupplierListQuery(search = search, wilaya = wilaya, commune = commune, balance = balance?.band())
