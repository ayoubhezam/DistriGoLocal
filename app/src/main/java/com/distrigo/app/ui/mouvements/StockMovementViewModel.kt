package com.distrigo.app.ui.mouvements

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.StockMovement
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StockMovementViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ProductRepository(
        db.productDao(), db.categoryDao(), db.supplierDao(), db = db
    )

    private val _movements = MutableStateFlow<List<StockMovement>>(emptyList())
    val movements: StateFlow<List<StockMovement>> = _movements

    private val _selectedMovement = MutableStateFlow<StockMovement?>(null)
    val selectedMovement: StateFlow<StockMovement?> = _selectedMovement

    private val _availableSources = MutableStateFlow<List<String>>(emptyList())
    val availableSources: StateFlow<List<String>> = _availableSources

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadMovementsForProduct(productId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _movements.value = repository.getMovementsForProduct(productId)
            _isLoading.value = false
        }
    }

    fun loadSourcesForProduct(productId: Int) {
        viewModelScope.launch {
            _availableSources.value = repository.getDistinctSourcesForProduct(productId)
        }
    }

    fun loadFilteredMovements(
        productId: Int? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
        direction: String? = null,
        sourceLabel: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _movements.value = repository.getFilteredMovements(productId, dateFrom, dateTo, direction, sourceLabel)
            _isLoading.value = false
        }
    }

    fun loadMovementDetail(id: Int) {
        viewModelScope.launch {
            _selectedMovement.value = repository.getMovementById(id)
        }
    }
}