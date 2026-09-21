package com.distrigo.app.ui.mouvements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.StockMovement
import com.distrigo.app.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StockMovementViewModel @Inject constructor(
    private val repository: ProductRepository
) : ViewModel() {

    private val _movements = MutableStateFlow<List<StockMovement>>(emptyList())
    val movements: StateFlow<List<StockMovement>> = _movements

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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _filters = MutableStateFlow(MovementFilters())
    val filters: StateFlow<MovementFilters> = _filters

    fun setFilters(newFilters: MovementFilters) {
        _filters.value = newFilters
    }

    fun loadMovementsForProduct(productId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _movements.value = repository.getMovementsForProduct(productId)
            _isLoading.value = false
        }
    }

    /** The parties the filter can offer for this product. Loaded once, with the screen. */
    fun loadPartiesForProduct(productId: Int) {
        viewModelScope.launch {
            _clients.value = repository.clientsForProductMovements(productId).map { PartyOption(it.id, it.name) }
            _suppliers.value = repository.suppliersForProductMovements(productId).map { PartyOption(it.id, it.name) }
        }
    }

    fun loadFilteredMovements(productId: Int?, filters: MovementFilters) {
        viewModelScope.launch {
            _isLoading.value = true
            _movements.value = repository.getFilteredMovements(
                productId   = productId,
                dateFrom    = filters.dateFrom,
                dateTo      = filters.dateTo,
                direction   = filters.direction,
                emplacement = filters.emplacement,
                types       = filters.types.map { it.key },
                party       = filters.party?.key,
                partyId     = filters.partyId,
            )
            _isLoading.value = false
        }
    }

    /**
     * How many movements [filters] would leave, for the sheet's own button — asked of the database
     * rather than counted over the list on screen, which holds the *applied* filters, not the draft.
     */
    suspend fun countFor(productId: Int?, filters: MovementFilters): Int = repository.countFilteredMovements(
        productId   = productId,
        dateFrom    = filters.dateFrom,
        dateTo      = filters.dateTo,
        direction   = filters.direction,
        emplacement = filters.emplacement,
        types       = filters.types.map { it.key },
        party       = filters.party?.key,
        partyId     = filters.partyId,
    )

    fun loadMovementDetail(id: Int) {
        viewModelScope.launch {
            val movement = repository.getMovementById(id)
            _selectedSourceNumber.value = movement?.let { repository.documentLabel(it.source_type, it.source_id) }
            _selectedMovement.value = movement
        }
    }
}
