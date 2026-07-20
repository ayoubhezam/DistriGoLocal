package com.distrigo.app.ui.pertes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.Perte
import com.distrigo.app.data.model.PerteType
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.repository.PerteRepository
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PerteViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository        = PerteRepository(db)
    private val productRepository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)

    // ── قائمة الأنواع ──
    private val _perteTypes = MutableStateFlow<List<PerteType>>(emptyList())
    val perteTypes: StateFlow<List<PerteType>> = _perteTypes

    // ── خسائر النوع المفتوح حالياً ──
    private val _pertes = MutableStateFlow<List<Perte>>(emptyList())
    val pertes: StateFlow<List<Perte>> = _pertes

    // ── المنتجات (لاختيار Produit في الفورم) ──
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products

    private val _selectedMonth = MutableStateFlow(currentMonth())
    val selectedMonth: StateFlow<String> = _selectedMonth

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            repository.seedDefaultPerteTypesIfNeeded()
            loadPerteTypes()
            loadProducts()
        }
    }

    fun setSelectedMonth(month: String) {
        _selectedMonth.value = month
    }

    fun loadPerteTypes() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _perteTypes.value = repository.getPerteTypesWithStats(_selectedMonth.value)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadPertes(typeId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _pertes.value = repository.getPertes(typeId, _selectedMonth.value)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadProducts() {
        viewModelScope.launch {
            try {
                _products.value = productRepository.getProducts()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun addPerteType(
        name      : String,
        icon      : String,
        colorHex  : String,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.addPerteType(name, icon, colorHex)
                loadPerteTypes()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun deletePerteType(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deletePerteType(id)
                loadPerteTypes()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun addPerte(
        typeId    : Int,
        productId : Int,
        quantity  : Int,
        source    : String,
        dateTime  : String,
        motif     : String?,
        photoPath : String?,
        userName  : String? = null,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.addPerte(typeId, productId, quantity, source, dateTime, motif, photoPath, userName)
            if (result.containsKey("error")) {
                onError(result["error"] as String)
            } else {
                loadPertes(typeId)
                loadPerteTypes()
                loadProducts()   // تحديث الأرصدة المعروضة بعد خصم المخزون
                onSuccess()
            }
        }
    }

    fun deletePerte(
        id        : Int,
        typeId    : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deletePerte(id)
                loadPertes(typeId)
                loadPerteTypes()
                loadProducts()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun updatePerte(
        id        : Int,
        typeId    : Int,
        productId : Int,
        quantity  : Int,
        source    : String,
        dateTime  : String,
        motif     : String?,
        photoPath : String?,
        userName  : String? = null,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.updatePerte(id, productId, quantity, source, dateTime, motif, photoPath, userName)
            if (result.containsKey("error")) {
                onError(result["error"] as String)
            } else {
                loadPertes(typeId)
                loadPerteTypes()
                loadProducts()
                onSuccess()
            }
        }
    }

    companion object {
        fun currentMonth(): String = java.time.LocalDate.now().toString().take(7)
    }
}