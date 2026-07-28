package com.distrigo.app.ui.clients

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.core.paging.PagedListController
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.ClientTransaction
import com.distrigo.app.data.model.FactureFilter
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Independent ViewModel for the "Voir tout l'historique" overlay (Factures & Paiements).
 * Uses PagedListController by composition — it does not extend/replace ClientViewModel,
 * which keeps owning the small truncated list shown inline in ClientDetailScreen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FactureHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ProductRepository(
        db.productDao(),
        db.categoryDao(),
        db.supplierDao(),
        db = db
    )

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount

    private var controller: PagedListController<FactureFilter, ClientTransaction>? = null

    // bind() is idempotent/synchronous (no I/O) — safe to call both directly during
    // composition and from a LaunchedEffect(Unit); repeated calls for the same
    // ViewModel instance return the already-created controller as-is.
    fun bind(clientId: Int): PagedListController<FactureFilter, ClientTransaction> {
        controller?.let { return it }
        val newController = PagedListController<FactureFilter, ClientTransaction>(
            scope = viewModelScope,
            defaultFilter = FactureFilter.TOUTES,
            pagerFactory = { filter, query -> repository.getClientLedgerPaged(clientId, filter, query) }
        )
        controller = newController
        viewModelScope.launch {
            newController.filter
                .combine(newController.query.debounce(300L).distinctUntilChanged()) { filter, query -> filter to query }
                .collectLatest { (filter, query) ->
                    _totalCount.value = repository.countClientLedger(clientId, filter, query)
                }
        }
        return newController
    }
}