package com.distrigo.app.ui.suppliers

import com.distrigo.app.data.model.Supplier
import com.distrigo.app.data.model.SupplierProduct
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.distrigo.app.data.model.SupplierTransaction
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.database.AppDatabase

class SupplierViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ProductRepository(
        db.productDao(),
        db.categoryDao(),
        db.supplierDao(),
        db     = db
    )
    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers: StateFlow<List<Supplier>> = _suppliers

    private val _supplierProducts = MutableStateFlow<List<SupplierProduct>>(emptyList())
    val supplierProducts: StateFlow<List<SupplierProduct>> = _supplierProducts

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { loadSuppliers() }

    fun loadSuppliers() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _suppliers.value = repository.getSuppliers()
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

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
                loadSuppliers()
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
                loadSuppliers()
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
                loadSuppliers()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun loadSuppliersAndUpdate(supplierId: Int, onUpdated: (Supplier?) -> Unit) {
        viewModelScope.launch {
            try {
                _suppliers.value = repository.getSuppliers()
                val updated = _suppliers.value.find { it.id == supplierId }
                onUpdated(updated)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private val _transactions = MutableStateFlow<List<SupplierTransaction>>(emptyList())
    val transactions: StateFlow<List<SupplierTransaction>> = _transactions

    fun loadTransactions(supplierId: Int) {
        viewModelScope.launch {
            try {
                _transactions.value = repository.getSupplierTransactions(supplierId)
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
                loadSuppliers()
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
                loadSuppliers()
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
                loadSuppliers()
                loadTransactions(supplierId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }
}


