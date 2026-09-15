package com.distrigo.app.ui.suppliers

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

@HiltViewModel
class SupplierViewModel @Inject constructor(
    private val repository: ProductRepository
) : ViewModel() {

    private val _supplierProducts = MutableStateFlow<List<SupplierProduct>>(emptyList())
    val supplierProducts: StateFlow<List<SupplierProduct>> = _supplierProducts

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Room-observed single source of truth: re-emits on every write to the suppliers table
    // (ajout/modification/suppression, recalcul de solde après achat ou paiement…) — no manual refresh.
    val suppliers: StateFlow<List<Supplier>> = repository.observeSuppliers()
        .onEach { _isLoading.value = false; _error.value = null }
        .catch { e -> _error.value = e.message; _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadSupplierProducts(supplierId: Int) {
        viewModelScope.launch {
            try {
                _supplierProducts.value = repository.getSupplierProducts(supplierId)
            } catch (e: Exception) {
                _supplierProducts.value = emptyList()
            }
        }
    }

    fun addSupplier(
        supplier  : Map<String, Any?>,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.addSupplier(supplier)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
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
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun deleteSupplier(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteSupplier(id)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
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
                onError(e.message ?: "Erreur inconnue")
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
                onError(e.message ?: "Erreur inconnue")
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
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }
}


