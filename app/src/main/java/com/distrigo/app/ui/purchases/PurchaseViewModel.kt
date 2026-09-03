package com.distrigo.app.ui.purchases

import com.distrigo.app.data.model.PurchaseOrder
import com.distrigo.app.data.model.PurchaseOrderItem
import com.distrigo.app.data.model.DraftBaseState
import com.distrigo.app.data.model.PurchaseDraft
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.PurchaseDraftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.distrigo.app.data.api.extractErrorMessage
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

    private val _orders = MutableStateFlow<List<PurchaseOrder>>(emptyList())
    val orders: StateFlow<List<PurchaseOrder>> = _orders

    private val _selectedOrder = MutableStateFlow<PurchaseOrder?>(null)
    val selectedOrder: StateFlow<PurchaseOrder?> = _selectedOrder

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Form state (supplier / cart / note / montantPaye) used to live here. It moved to
    // PurchaseFormSessionViewModel, which is scoped to the purchase form graph's back stack entry
    // rather than to the Achats tab — so it is disposed when the user leaves the form, in both
    // hosts. That is what made resetPurchaseForm() unnecessary: there is no longer stale state to
    // clear on entry, and therefore no window in which a reset could overwrite a saved Brouillon.

    init { loadOrders() }

    fun loadOrders() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _orders.value = repository.getPurchaseOrders()
                _error.value  = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

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
                loadOrders()
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
                loadOrders()
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
                loadOrders()
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
                loadOrders()
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
                loadOrders()
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


