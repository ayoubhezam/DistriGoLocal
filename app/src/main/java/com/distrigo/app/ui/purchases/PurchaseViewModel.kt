package com.distrigo.app.ui.purchases

import com.distrigo.app.data.model.PurchaseOrder
import com.distrigo.app.data.model.PurchaseOrderItem
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.distrigo.app.data.api.extractErrorMessage
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.Supplier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
class PurchaseViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ProductRepository(
        db.productDao(),
        db.categoryDao(),
        db.supplierDao(),
        db     = db
    )


    private val _orders = MutableStateFlow<List<PurchaseOrder>>(emptyList())
    val orders: StateFlow<List<PurchaseOrder>> = _orders

    private val _selectedOrder = MutableStateFlow<PurchaseOrder?>(null)
    val selectedOrder: StateFlow<PurchaseOrder?> = _selectedOrder

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ── Purchase form (wizard) state — shared across PurchaseFormNavGraph steps ──
    private val _formSupplier = MutableStateFlow<Supplier?>(null)
    val formSupplier: StateFlow<Supplier?> = _formSupplier

    private val _formCartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val formCartItems: StateFlow<List<CartItem>> = _formCartItems

    private val _formNote = MutableStateFlow("")
    val formNote: StateFlow<String> = _formNote

    private val _formMontantPaye = MutableStateFlow("")
    val formMontantPaye: StateFlow<String> = _formMontantPaye

    fun setFormSupplier(supplier: Supplier?) { _formSupplier.value = supplier }
    fun setFormCartItems(items: List<CartItem>) { _formCartItems.value = items }
    fun setFormNote(note: String) { _formNote.value = note }
    fun setFormMontantPaye(value: String) { _formMontantPaye.value = value }

    fun resetPurchaseForm() {
        _formSupplier.value = null
        _formCartItems.value = emptyList()
        _formNote.value = ""
        _formMontantPaye.value = ""
    }

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

    fun createOrder(
        supplierId : Int,
        date       : String,
        items      : List<Map<String, Any?>>,
        note       : String?,
        montantPaye : Double = 0.0,
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
                ))
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

    fun updateOrder(
        id         : Int,
        supplierId : Int,
        items      : List<Map<String, Any?>>,
        note       : String?,
        montantPaye : Double = 0.0,
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
                ))
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


