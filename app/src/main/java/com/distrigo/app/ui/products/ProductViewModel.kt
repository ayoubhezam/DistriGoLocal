package com.distrigo.app.ui.products

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.local.database.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.distrigo.app.data.model.Category
import com.distrigo.app.data.model.PriceHistory
import com.distrigo.app.data.model.Supplier

class ProductViewModel(application: Application) : AndroidViewModel(application) {

    // �� ProductViewModel � CategoryViewModel
    private val db = AppDatabase.getDatabase(application)
    private val repository = ProductRepository(
        db.productDao(),
        db.categoryDao(),
        db.supplierDao(),
        db     = db
    )

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers: StateFlow<List<Supplier>> = _suppliers

    private val _priceHistory = MutableStateFlow<List<PriceHistory>>(emptyList())
    val priceHistory: StateFlow<List<PriceHistory>> = _priceHistory

    init {
        loadProducts()
        loadCategories()
        loadSuppliers()
    }

    fun loadProducts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _products.value = repository.getProducts()
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteProduct(id: Int) {
        viewModelScope.launch {
            try {
                repository.deleteProduct(id)
                loadProducts()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun addProduct(
        product   : Map<String, Any?>,
        onSuccess : (Map<String, Any>) -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = repository.addProduct(product)
                onSuccess(result)
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun updateProduct(
        id        : Int,
        product   : Map<String, Any?>,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updateProduct(id, product)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            try {
                _categories.value = repository.getCategories()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun addCategoryAndRefresh(
        name      : String,
        onSuccess : (Int) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = repository.addCategory(mapOf("name" to name, "sort_order" to 0))
                val newId  = (result["id"] as? Double)?.toInt() ?: 0
                loadCategories()
                onSuccess(newId)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun linkProductToSupplier(
        supplierId    : Int,
        productId     : Int,
        purchasePrice : Double,
        onSuccess     : () -> Unit,
        onError       : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.linkProductToSupplier(supplierId, productId, purchasePrice)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun unlinkProductFromAllSuppliers(
        productId : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.unlinkProductFromAllSuppliers(productId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun loadSuppliers() {
        viewModelScope.launch {
            try {
                _suppliers.value = repository.getSuppliers()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun addSupplierAndRefresh(
        name      : String,
        phone     : String,
        onSuccess : (Int) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = repository.addSupplier(mapOf(
                    "name"    to name,
                    "phone"   to phone,
                    "address" to null,
                    "note"    to null,
                    "balance" to 0.0
                ))
                val newId = (result["id"] as? Double)?.toInt() ?: 0
                loadSuppliers()
                onSuccess(newId)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun loadPriceHistory(productId: Int) {
        viewModelScope.launch {
            try {
                _priceHistory.value = repository.getProductPriceHistory(productId)
            } catch (e: Exception) {
                _priceHistory.value = emptyList()
            }
        }
    }
}


