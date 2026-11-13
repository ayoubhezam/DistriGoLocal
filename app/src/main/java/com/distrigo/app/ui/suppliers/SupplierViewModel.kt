package com.distrigo.app.ui.suppliers

import com.distrigo.app.ui.common.userMessage

import com.distrigo.app.data.model.Supplier
import com.distrigo.app.data.model.SupplierProduct
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.distrigo.app.data.model.SupplierLedgerPreview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.distrigo.app.ui.common.SupplierListFilters
import androidx.compose.runtime.snapshotFlow
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.distrigo.app.data.local.paging.PartySummary
import com.distrigo.app.data.local.paging.SupplierListQuery
import com.distrigo.app.ui.common.SupplierPickerList
import com.distrigo.app.ui.common.debouncedSearch
import com.distrigo.app.ui.common.placesOf
import com.distrigo.app.ui.common.toQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SupplierViewModel @Inject constructor(
    private val repository: ProductRepository
) : ViewModel() {

    /** The Fournisseurs list's filter sheet. Here, not in the screen, so opening a supplier and coming back keeps it. */
    var listFilters by mutableStateOf(SupplierListFilters())

    private val _supplierProducts = MutableStateFlow<List<SupplierProduct>>(emptyList())
    val supplierProducts: StateFlow<List<SupplierProduct>> = _supplierProducts

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** The Fournisseurs list's search box, as typed. */
    var listSearch by mutableStateOf("")

    /**
     * The Fournisseurs list as the search and the sheet ask for it, once typing pauses: searched, filtered, counted
     * and paged in SQL (PERF-01), where every screen holding the list read the whole table again after each write.
     */
    private val listQuery: Flow<SupplierListQuery> =
        combine(snapshotFlow { listFilters }, debouncedSearch { listSearch }) { filters, search -> filters.toQuery(search) }
            .distinctUntilChanged()

    val pagedSuppliers: Flow<PagingData<Supplier>> = listQuery.flatMapLatest { repository.pageSuppliers(it) }.cachedIn(viewModelScope)

    /** How many suppliers the list shows; null until counted. */
    val shownCount: StateFlow<Int?> = listQuery.flatMapLatest { repository.observeSupplierCount(it) }
        .catch { e -> _error.value = userMessage(e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** All of them: how many, and what is owed them. Null until counted. */
    val summary: StateFlow<PartySummary?> = repository.observeSupplierSummary()
        .catch { e -> _error.value = userMessage(e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The places the suppliers have, for the filter sheet ([column]: wilaya_name or commune_name). */
    suspend fun places(column: String, inWilaya: String? = null): List<String> =
        placesOf(repository.supplierPlaces(column, inWilaya))

    /** One supplier, live: null once deleted. */
    fun observeSupplier(id: Int): Flow<Supplier?> = repository.observeSupplier(id)

    suspend fun getSupplier(id: Int): Supplier? = repository.getSupplier(id)

    /** « Choisir un fournisseur »: the Achats form's picker. */
    val picker: SupplierPickerList by lazy { SupplierPickerList(viewModelScope, repository) }

    fun loadSupplierProducts(supplierId: Int) {
        viewModelScope.launch {
            try {
                _supplierProducts.value = repository.getSupplierProducts(supplierId)
            } catch (e: Exception) {
                _supplierProducts.value = emptyList()
            }
        }
    }

    /** Adds [supplier]; [onAdded] then gets it as saved, for the picker that selects it at once. */
    fun addSupplier(
        supplier  : Map<String, Any?>,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit,
        onAdded   : (Supplier) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                val id = (repository.addSupplier(supplier)["id"] as? Number)?.toInt()
                onSuccess()
                id?.let { repository.getSupplier(it) }?.let(onAdded)
            } catch (e: Exception) {
                onError(userMessage(e))
            }
        }
    }

    fun updateSupplier(
        id        : Int,
        supplier  : Map<String, Any?>,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updateSupplier(id, supplier)
                onSuccess()
            } catch (e: Exception) {
                onError(userMessage(e))
            }
        }
    }

    /** [onError] gets the refusal itself: the screen words it, amounts in the business's format (refusalMessage). */
    fun deleteSupplier(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (Throwable) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteSupplier(id)
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }




    // What the detail screen shows of the supplier's orders and payments: the figures, and the latest
    // four entries (the screen shows two, or four expanded). The whole history is the paged
    // "Voir tout l'historique" screen's.
    private val _ledger = MutableStateFlow(SupplierLedgerPreview())
    val ledger: StateFlow<SupplierLedgerPreview> = _ledger

    fun loadTransactions(supplierId: Int) {
        viewModelScope.launch {
            try {
                _ledger.value = repository.getSupplierLedgerPreview(supplierId, limit = 4)
            } catch (e: Exception) {
                android.util.Log.e("DISTRIGO", "transactions error: ${e.message}")
            }
        }
    }

    fun addPayment(
        supplierId : Int,
        amount     : Double,
        note       : String?,
        onSuccess  : () -> Unit,
        onError    : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.addSupplierPayment(supplierId, amount, note)
                loadTransactions(supplierId)
                onSuccess()
            } catch (e: Exception) {
                onError(userMessage(e))
            }
        }
    }

    fun deletePayment(
        supplierId : Int,
        paymentId  : Int,
        onSuccess  : () -> Unit,
        onError    : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteSupplierPayment(supplierId, paymentId)
                loadTransactions(supplierId)
                onSuccess()
            } catch (e: Exception) {
                onError(userMessage(e))
            }
        }
    }

    fun updatePayment(
        supplierId : Int,
        paymentId  : Int,
        amount     : Double,
        onSuccess  : () -> Unit,
        onError    : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updateSupplierPayment(supplierId, paymentId, amount)
                loadTransactions(supplierId)
                onSuccess()
            } catch (e: Exception) {
                onError(userMessage(e))
            }
        }
    }
}


