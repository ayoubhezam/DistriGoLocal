package com.distrigo.app.ui.ventes

import com.distrigo.app.data.model.Vente
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.api.extractErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.database.AppDatabase

class VenteViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ProductRepository(
        db.productDao(),
        db.categoryDao(),
        db.supplierDao(),
        db     = db
    )
    private val _ventes = MutableStateFlow<List<Vente>>(emptyList())
    val ventes: StateFlow<List<Vente>> = _ventes

    private val _selectedVente = MutableStateFlow<Vente?>(null)
    val selectedVente: StateFlow<Vente?> = _selectedVente

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { loadVentes() }

    fun loadVentes() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _ventes.value = repository.getVentes()
                _error.value = null
            } catch (e: Exception) {
                _error.value = extractErrorMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    fun loadVentesForClient(clientId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _ventes.value = repository.getVentes(clientId = clientId)
                _error.value = null
            } catch (e: Exception) {
                _error.value = extractErrorMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadVenteDetail(id: Int) {
        viewModelScope.launch {
            try {
                _selectedVente.value = repository.getVente(id)
            } catch (e: Exception) {
                android.util.Log.e("DISTRIGO", "vente detail error: ${e.message}")
            }
        }
    }

    fun createVente(
        clientId    : Int,
        tourneeId   : Int?,
        source      : String,
        items       : List<Map<String, Any?>>,
        note        : String?,
        montantPaye : Double,
        userName    : String? = null,
        onSuccess   : () -> Unit,
        onError     : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.createVente(clientId, tourneeId, source, items, note, montantPaye, userName)
                loadVentes()
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))
            }
        }
    }
    fun updateVente(
        id          : Int,
        clientId    : Int,
        items       : List<Map<String, Any?>>,
        note        : String?,
        montantPaye : Double,
        userName    : String? = null,
        onSuccess   : () -> Unit,
        onError     : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updateVente(id, clientId, items, note, montantPaye, userName)
                loadVentes()
                loadVenteDetail(id)
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))
            }
        }
    }

    fun deliverVente(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deliverVente(id)
                loadVentes()
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))
            }
        }
    }

    fun deleteVente(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteVente(id)
                loadVentes()
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))
            }
        }
    }
}


