package com.distrigo.app.ui.mouvements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.StockMovement
import com.distrigo.app.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.distrigo.app.data.local.dao.mouvement.MovementTotals
import com.distrigo.app.data.local.paging.MovementListQuery
import com.distrigo.app.data.time.BusinessDates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StockMovementViewModel @Inject constructor(
    private val repository: ProductRepository
) : ViewModel() {

    private val _selectedMovement = MutableStateFlow<StockMovement?>(null)
    val selectedMovement: StateFlow<StockMovement?> = _selectedMovement

    /** The selected movement's source document as it is numbered — "#26", "V-6DED-000027". */
    private val _selectedSourceNumber = MutableStateFlow<String?>(null)
    val selectedSourceNumber: StateFlow<String?> = _selectedSourceNumber

    /** The clients and the suppliers this product moved with — what the filter offers. */
    private val _clients = MutableStateFlow<List<PartyOption>>(emptyList())
    val clients: StateFlow<List<PartyOption>> = _clients

    private val _suppliers = MutableStateFlow<List<PartyOption>>(emptyList())
    val suppliers: StateFlow<List<PartyOption>> = _suppliers

    private val _filters = MutableStateFlow(MovementFilters())
    val filters: StateFlow<MovementFilters> = _filters

    fun setFilters(newFilters: MovementFilters) {
        _filters.value = newFilters
    }

    // -- The list --
    //
    // Paged from the database a screenful at a time, with the figures above it summed in SQL: the
    // screen used to read the product's whole history (over 3,000 movements for a best-seller) and
    // count it while drawing.

    private val _productId = MutableStateFlow<Int?>(null)

    /** Points the list at a product. Called by the screen as it opens. */
    fun showProduct(productId: Int) { _productId.value = productId }

    private val listQuery: Flow<MovementListQuery?> = combine(_productId, _filters) { productId, filters ->
        productId?.let { query(it, filters) }
    }.distinctUntilChanged()

    /** The product's movements, newest first, with a header row before each day's first movement. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedMovements: Flow<PagingData<MovementListItem>> = listQuery
        .flatMapLatest { query -> if (query == null) flowOf(PagingData.empty()) else repository.pageMovements(query) }
        .map { page -> page.map<StockMovement, MovementListItem> { MovementListItem.Row(it) }.withDayHeaders() }
        .cachedIn(viewModelScope)

    /** Count, entrées and sorties for the filters applied - null until the first sum lands. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val totals: StateFlow<MovementTotals?> = listQuery
        .flatMapLatest { query -> if (query == null) flowOf(null) else repository.observeMovementTotals(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun query(productId: Int?, filters: MovementFilters) = repository.movementQuery(
        productId   = productId,
        dateFrom    = filters.dateFrom,
        dateTo      = filters.dateTo,
        direction   = filters.direction,
        emplacement = filters.emplacement,
        types       = filters.types.map { it.key },
        party       = filters.party?.key,
        partyId     = filters.partyId,
    )

    /** The parties the filter can offer for this product. Loaded once, with the screen. */
    fun loadPartiesForProduct(productId: Int) {
        viewModelScope.launch {
            _clients.value = repository.clientsForProductMovements(productId).map { PartyOption(it.id, it.name) }
            _suppliers.value = repository.suppliersForProductMovements(productId).map { PartyOption(it.id, it.name) }
        }
    }

    /**
     * How many movements [filters] would leave, for the sheet's own button — asked of the database
     * rather than counted over the list on screen, which holds the *applied* filters, not the draft.
     */
    suspend fun countFor(productId: Int?, filters: MovementFilters): Int =
        repository.countMovements(query(productId, filters))

    fun loadMovementDetail(id: Int) {
        viewModelScope.launch {
            val movement = repository.getMovementById(id)
            _selectedSourceNumber.value = movement?.let { repository.documentLabel(it.source_type, it.source_id) }
            _selectedMovement.value = movement
        }
    }
}

/** A row of the Mouvements list: a day's header, or a movement. */
sealed interface MovementListItem {
    data class DayHeader(val day: String) : MovementListItem
    data class Row(val movement: StockMovement) : MovementListItem
}

/** A header before the first movement of each local day, inserted between loaded rows as they load. */
private fun PagingData<MovementListItem>.withDayHeaders(): PagingData<MovementListItem> =
    insertSeparators { before, after ->
        val next = (after as? MovementListItem.Row)?.movement ?: return@insertSeparators null
        val previous = (before as? MovementListItem.Row)?.movement
        val day = BusinessDates.localDay(next.created_at)
        if (previous == null || BusinessDates.localDay(previous.created_at) != day) MovementListItem.DayHeader(day) else null
    }
