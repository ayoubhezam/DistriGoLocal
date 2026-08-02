package com.distrigo.app.ui.retours

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.RetourClient
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.RetourClientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RetourClientViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository        = RetourClientRepository(db)
    private val productRepository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)

    private val _retours = MutableStateFlow<List<RetourClient>>(emptyList())
    val retours: StateFlow<List<RetourClient>> = _retours

    // Observés depuis Room — mise à jour automatique à chaque écriture sur la table products
    val products: StateFlow<List<Product>> = productRepository.observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Observés depuis Room — mise à jour automatique à chaque écriture sur la table clients
    val clients: StateFlow<List<Client>> = productRepository.observeClients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _returnableProducts = MutableStateFlow<List<ReturnableProduct>>(emptyList())
    val returnableProducts: StateFlow<List<ReturnableProduct>> = _returnableProducts

    fun loadReturnableProducts(clientId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val deliveredStatus = "delivered"
            val sold     = db.venteDao().getSoldQuantitiesForClient(clientId, deliveredStatus).associate { it.product_id to it.total_quantity }
            val returned = db.retourClientDao().getReturnedQuantitiesForClient(clientId).associate { it.product_id to it.total_quantity }
            val byId     = productRepository.getProducts().associateBy { it.id }
            _returnableProducts.value = sold.mapNotNull { (productId, soldQty) ->
                val remaining = soldQty - (returned[productId] ?: 0.0)
                if (remaining > 0) byId[productId]?.let { ReturnableProduct(it, remaining) } else null
            }
            _isLoading.value = false
        }
    }

    fun loadRetours() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _retours.value = repository.getRetours()
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createRetour(
        clientId  : Int,
        date      : String,
        motif     : String?,
        note      : String?,
        items     : List<Map<String, Any?>>,
        userName  : String? = null,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.createRetour(clientId, null, date, motif, note, items, userName)
            if (result.containsKey("error")) {
                onError(result["error"] as String)
            } else {
                loadRetours()
                onSuccess()
            }
        }
    }

    fun deleteRetour(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.deleteRetour(id)
            if (result.containsKey("error")) {
                onError(result["error"] as String)
            } else {
                loadRetours()
                onSuccess()
            }
        }
    }
}
