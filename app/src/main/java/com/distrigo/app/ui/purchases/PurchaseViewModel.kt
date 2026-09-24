package com.distrigo.app.ui.purchases

import com.distrigo.app.data.model.PurchaseOrder
import com.distrigo.app.data.model.PurchaseOrderItem
import com.distrigo.app.data.model.DraftBaseState
import com.distrigo.app.data.model.PurchaseDraft
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.PurchaseDraftRepository
import androidx.compose.runtime.snapshotFlow
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.distrigo.app.data.local.dao.OrderSupplierChoice
import com.distrigo.app.data.local.paging.PurchaseOrderListQuery
import com.distrigo.app.data.time.BusinessDates
import com.distrigo.app.ui.common.OrderListFilters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.distrigo.app.ui.common.extractErrorMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PurchaseViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val draftRepository: PurchaseDraftRepository
) : ViewModel() {

    // ── Brouillons ──
    // Room-observed, so the "Brouillons (N)" chip and the list stay live while a draft is being
    // written from the form graph. Reads only — a draft is created and deleted by the session
    // ViewModel and by the commit transaction, never from here.
    val drafts: StateFlow<List<PurchaseDraft>> = draftRepository.observeDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val draftCount: StateFlow<Int> = draftRepository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The pending edit draft for a bon, if any. Every route into edit mode has to ask first. */
    suspend fun draftForOrder(orderId: Int): PurchaseDraft? = draftRepository.draftForOrder(orderId)

    /** Whether a draft can still be applied, once its bon has been re-read. */
    suspend fun resolveDraftBase(draft: PurchaseDraft): DraftBaseState =
        draftRepository.resolveBaseState(draft)

    fun deleteDraft(id: Int) { viewModelScope.launch { draftRepository.delete(id) } }

    /** Deletes every selected draft in one statement. Empty input is a no-op. */
    fun deleteDrafts(ids: Collection<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { draftRepository.deleteAll(ids.toList()) }
    }

    private val _selectedOrder = MutableStateFlow<PurchaseOrder?>(null)
    val selectedOrder: StateFlow<PurchaseOrder?> = _selectedOrder

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Form state (supplier / cart / note / montantPaye) used to live here. It moved to
    // PurchaseFormSessionViewModel, which is scoped to the purchase form graph's back stack entry
    // rather than to the Achats tab — so it is disposed when the user leaves the form, in both
    // hosts. That is what made resetPurchaseForm() unnecessary: there is no longer stale state to
    // clear on entry, and therefore no window in which a reset could overwrite a saved Brouillon.

    // ── The list ──
    //
    // Paged from the database a screenful at a time, filtered and searched in SQL, and live: Room
    // invalidates the pages when a bon changes anywhere, so no command below has to reload it.

    /** The search and the filter sheet, as the query they ask for. The search waits for typing to pause. */
    @OptIn(FlowPreview::class)
    private val listQuery: Flow<PurchaseOrderListQuery> = combine(
        snapshotFlow {
            OrderListFilters(filterReceptionStatus, filterPaymentStatus, filterSupplierId, filterDateFrom, filterDateTo)
        },
        snapshotFlow { searchQuery }.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS },
    ) { filters, search -> filters.toListQuery(search) }
        .distinctUntilChanged()

    /** The bons, newest first, with a header row before each day's first bon. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedOrders: Flow<PagingData<AchatsListItem>> = listQuery
        .flatMapLatest { repository.pagePurchaseOrders(it) }
        .map { page -> page.map<PurchaseOrder, AchatsListItem> { AchatsListItem.Order(it) }.withDayHeaders() }
        .cachedIn(viewModelScope)

    /** How many bons the search and filters match — null until the first count lands. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val orderCount: StateFlow<Int?> = listQuery
        .flatMapLatest { repository.observePurchaseOrderCount(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The supplier filter's choices: every supplier with at least one bon. */
    val orderSuppliers: StateFlow<List<OrderSupplierChoice>> = repository.observePurchaseOrderSuppliers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** One bon, live, for the detail screen: loading, found, or gone. */
    fun observeOrder(id: Int): Flow<OrderLookup> =
        repository.observePurchaseOrder(id).map { order -> order?.let { OrderLookup.Found(it) } ?: OrderLookup.Gone }

    fun loadOrderDetail(id: Int) {
        viewModelScope.launch {
            try {
                _selectedOrder.value = repository.getPurchaseOrder(id)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    /** [draftId] is deleted inside the same transaction as the insert — never before it commits. */
    fun createOrder(
        supplierId : Int,
        date       : String,
        items      : List<Map<String, Any?>>,
        note       : String?,
        montantPaye : Double = 0.0,
        draftId    : Int? = null,
        onSuccess  : () -> Unit,
        onError    : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.createPurchaseOrder(mapOf(
                    "supplier_id"       to supplierId,
                    "date"              to date,
                    "items"             to items,
                    "note"              to note,
                    "montant_paye"      to montantPaye
                ), draftId = draftId)
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))
            }
        }
    }

    fun receiveOrder(
        id        : Int,
        userName  : String? = null,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.receivePurchaseOrder(id, userName)
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))            }
        }
    }

    /** [draftId] is deleted inside the same transaction as the update — never before it commits. */
    fun updateOrder(
        id         : Int,
        supplierId : Int,
        items      : List<Map<String, Any?>>,
        note       : String?,
        montantPaye : Double = 0.0,
        draftId    : Int? = null,
        onSuccess  : () -> Unit,
        onError    : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updatePurchaseOrder(id, mapOf(
                    "supplier_id" to supplierId,
                    "items"       to items,
                    "note"        to note,
                    "montant_paye" to montantPaye
                ), draftId = draftId)
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))            }
        }
    }

    fun reopenOrder(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.reopenPurchaseOrder(id)
                // The detail too, not only the list: `selectedOrder` is what the detail screen and
                // the edit form read, and reopening changed the one field they branch on. Without
                // this the bon keeps reporting `received` after it has become `pending` — the badge
                // still says "Reçu" and the menu still offers "Rouvrir le bon".
                loadOrderDetail(id)
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))            }
        }
    }

    fun deleteOrder(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deletePurchaseOrder(id)
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))            }
        }
    }

    // ── Filter state ──
    var searchQuery           by mutableStateOf("")
    var filterReceptionStatus by mutableStateOf<String?>(null)
    var filterPaymentStatus   by mutableStateOf<String?>(null)
    var filterSupplierId      by mutableStateOf<Int?>(null)
    var filterDateFrom        by mutableStateOf<String?>(null)
    var filterDateTo          by mutableStateOf<String?>(null)

    fun clearAllFilters() {
        searchQuery           = ""
        filterReceptionStatus = null
        filterPaymentStatus   = null
        filterSupplierId      = null
        filterDateFrom        = null
        filterDateTo          = null
    }
}

/** A row of the Achats list: a day's header, or a bon. */
sealed interface AchatsListItem {
    data class DayHeader(val day: String) : AchatsListItem
    data class Order(val order: PurchaseOrder) : AchatsListItem
}

/** The detail screen's bon: still being read, read, or no longer in the database. */
sealed interface OrderLookup {
    data object Loading : OrderLookup
    data class Found(val order: PurchaseOrder) : OrderLookup
    data object Gone : OrderLookup
}

private const val SEARCH_DEBOUNCE_MS = 300L

/** The filter sheet in database terms: the picked local days become instant bounds. */
private fun OrderListFilters.toListQuery(search: String): PurchaseOrderListQuery {
    val (from, before) = BusinessDates.dayRangeBounds(dateFrom, dateTo)
    return PurchaseOrderListQuery(
        search          = search,
        receptionStatus = receptionStatus,
        paymentStatus   = paymentStatus,
        supplierId      = supplierId,
        createdFrom     = from,
        createdBefore   = before,
    )
}

/**
 * A header before the first bon of each local day. Inserted between the loaded rows, as they load,
 * rather than by grouping the whole list — there is no whole list any more.
 */
private fun PagingData<AchatsListItem>.withDayHeaders(): PagingData<AchatsListItem> =
    insertSeparators { before, after ->
        val next = (after as? AchatsListItem.Order)?.order ?: return@insertSeparators null
        val previous = (before as? AchatsListItem.Order)?.order
        val day = dayOf(next)
        if (previous == null || dayOf(previous) != day) AchatsListItem.DayHeader(day) else null
    }

private fun dayOf(order: PurchaseOrder): String = BusinessDates.localDay(order.created_at ?: order.date)
