package com.distrigo.app.ui.retours

import androidx.lifecycle.ViewModel
import com.distrigo.app.data.model.StockPolicy
import com.distrigo.app.data.repository.BusinessSettingsRepository
import kotlinx.coroutines.flow.first
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.RetourFournisseur
import com.distrigo.app.data.model.RetourPreview
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.RetourFournisseurRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RetourFournisseurViewModel @Inject constructor(
    private val db: AppDatabase,
    private val repository: RetourFournisseurRepository,
    private val productRepository: ProductRepository,
    private val businessSettings: BusinessSettingsRepository
) : ViewModel() {

    private val _retours = MutableStateFlow<List<RetourFournisseur>>(emptyList())
    val retours: StateFlow<List<RetourFournisseur>> = _retours

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _retourDetail = MutableStateFlow<RetourFournisseur?>(null)
    val retourDetail: StateFlow<RetourFournisseur?> = _retourDetail

    fun loadRetourDetail(id: Int) {
        viewModelScope.launch {
            _retourDetail.value = repository.getRetourDetail(id)
        }
    }

    fun loadRetours(supplierId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _retours.value = repository.getRetoursForSupplier(supplierId)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    // The supplier detail screen's returns: count, total and the latest three. Kept apart from
    // [retours], which the returns list screen fills.
    private val _detailPreview = MutableStateFlow(RetourPreview<RetourFournisseur>())
    val detailPreview: StateFlow<RetourPreview<RetourFournisseur>> = _detailPreview

    fun loadDetailPreview(supplierId: Int) {
        viewModelScope.launch {
            try {
                _detailPreview.value = repository.getDetailPreview(supplierId, limit = 3)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
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
            val result = try {
                repository.deleteRetour(id)
            } catch (e: Exception) {
                mapOf("error" to (e.message ?: "La suppression a échoué."))
            }
            if (result.containsKey("error")) {
                onError(result["error"] as String)
            } else {
                loadRetours(supplierId)
                onSuccess()
            }
        }
    }
}