package com.distrigo.app.ui.retours

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.RetourClient
import com.distrigo.app.data.model.RetourPreview
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.RetourClientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RetourClientViewModel @Inject constructor(
    private val db: AppDatabase,
    private val repository: RetourClientRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _retours = MutableStateFlow<List<RetourClient>>(emptyList())
    val retours: StateFlow<List<RetourClient>> = _retours

    // Observés depuis Room — mise à jour automatique à chaque écriture sur la table clients
    val clients: StateFlow<List<Client>> = productRepository.observeClients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _retourDetail = MutableStateFlow<RetourClient?>(null)
    val retourDetail: StateFlow<RetourClient?> = _retourDetail

    fun loadRetourDetail(id: Int) {
        viewModelScope.launch {
            _retourDetail.value = repository.getRetourDetail(id)
        }
    }

    // Which client's list is open, so a save or a delete reloads that same client.
    private var openClientId: Int? = null

    fun loadRetours(clientId: Int) {
        openClientId = clientId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _retours.value = repository.getRetoursForClient(clientId)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    // The client detail screen's returns: count, total and the latest three. Kept apart from
    // [retours], which the returns list screen fills with one client's returns.
    private val _detailPreview = MutableStateFlow(RetourPreview<RetourClient>())
    val detailPreview: StateFlow<RetourPreview<RetourClient>> = _detailPreview

    fun loadDetailPreview(clientId: Int) {
        viewModelScope.launch {
            try {
                _detailPreview.value = repository.getDetailPreview(clientId, limit = 3)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun deleteRetour(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
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
                openClientId?.let { loadRetours(it) }
                onSuccess()
            }
        }
    }
}
