package com.distrigo.app.ui.retours

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.RetourFournisseur
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.RetourFournisseurRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RetourFournisseurViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository        = RetourFournisseurRepository(db)
    private val productRepository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)

    private val _retours = MutableStateFlow<List<RetourFournisseur>>(emptyList())
    val retours: StateFlow<List<RetourFournisseur>> = _retours

    // Observés depuis Room — mise à jour automatique à chaque écriture sur la table products
    val products: StateFlow<List<Product>> = productRepository.observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _returnableProducts = MutableStateFlow<List<ReturnableProduct>>(emptyList())
    val returnableProducts: StateFlow<List<ReturnableProduct>> = _returnableProducts

    private val _retourDetail = MutableStateFlow<RetourFournisseur?>(null)
    val retourDetail: StateFlow<RetourFournisseur?> = _retourDetail

    fun loadRetourDetail(id: Int) {
        viewModelScope.launch {
            _retourDetail.value = repository.getRetourDetail(id)
        }
    }

    fun loadReturnableProducts(supplierId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val receivedStatus = "received"
            val purchased = db.purchaseDao().getPurchasedQuantitiesForSupplier(supplierId, receivedStatus).associate { it.product_id to it.total_quantity }
            val returned   = db.retourFournisseurDao().getReturnedQuantitiesForSupplier(supplierId).associate { it.product_id to it.total_quantity }
            val byId       = productRepository.getProducts().associateBy { it.id }
            _returnableProducts.value = purchased.mapNotNull { (productId, purchasedQty) ->
                val remaining = purchasedQty - (returned[productId] ?: 0.0)
                if (remaining > 0) byId[productId]?.let { ReturnableProduct(it, remaining) } else null
            }
            _isLoading.value = false
        }
    }

    fun loadRetours(supplierId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _retours.value = repository.getRetours(supplierId)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createRetour(
        supplierId : Int,
        date       : String,
        motif      : String?,
        note       : String?,
        items      : List<Map<String, Any?>>,
        userName   : String? = null,
        onSuccess  : () -> Unit,
        onError    : (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.createRetour(supplierId, date, motif, note, items, userName)
            if (result.containsKey("error")) {
                onError(result["error"] as String)
            } else {
                loadRetours(supplierId)
                onSuccess()
            }
        }
    }

    fun deleteRetour(
        id         : Int,
        supplierId : Int,
        onSuccess  : () -> Unit,
        onError    : (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.deleteRetour(id)
            if (result.containsKey("error")) {
                onError(result["error"] as String)
            } else {
                loadRetours(supplierId)
                onSuccess()
            }
        }
    }
}