package com.distrigo.app.ui.suppliers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.core.paging.PagedListController
import com.distrigo.app.data.model.AchatFilter
import com.distrigo.app.data.model.SupplierTransaction
import com.distrigo.app.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Independent ViewModel for the "Voir tout l'historique" overlay (Achats & Paiements).
 * Uses PagedListController by composition — it does not extend/replace SupplierViewModel,
 * which keeps owning the small truncated list shown inline in SupplierDetailScreen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AchatHistoryViewModel @Inject constructor(
    private val repository: ProductRepository
) : ViewModel() {

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount

    private var controller: PagedListController<AchatFilter, SupplierTransaction>? = null

    fun bind(supplierId: Int): PagedListController<AchatFilter, SupplierTransaction> {
        controller?.let { return it }
        val newController = PagedListController<AchatFilter, SupplierTransaction>(
            scope = viewModelScope,
            defaultFilter = AchatFilter.TOUTES,
            pagerFactory = { filter, query -> repository.getSupplierLedgerPaged(supplierId, filter, query) }
        )
        controller = newController
        viewModelScope.launch {
            newController.filter
                .combine(newController.query.debounce(300L).distinctUntilChanged()) { filter, query -> filter to query }
                .collectLatest { (filter, query) ->
                    _totalCount.value = repository.countSupplierLedger(supplierId, filter, query)
                }
        }
        return newController
    }
}
