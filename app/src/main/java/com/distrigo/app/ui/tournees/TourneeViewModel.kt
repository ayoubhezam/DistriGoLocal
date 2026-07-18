package com.distrigo.app.ui.tournees

import com.distrigo.app.data.model.Tournee
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.api.extractErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.TourneeClientInfo

class TourneeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ProductRepository(
        db.productDao(),
        db.categoryDao(),
        db.supplierDao(),
        db     = db
    )


    private val _tournees = MutableStateFlow<List<Tournee>>(emptyList())
    val tournees: StateFlow<List<Tournee>> = _tournees

    private val _selectedTournee = MutableStateFlow<Tournee?>(null)
    val selectedTournee: StateFlow<Tournee?> = _selectedTournee

    private val _openTournee = MutableStateFlow<Tournee?>(null)
    val openTournee: StateFlow<Tournee?> = _openTournee

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _tourneeClients = MutableStateFlow<List<TourneeClientInfo>>(emptyList())
    val tourneeClients: StateFlow<List<TourneeClientInfo>> = _tourneeClients

    init { loadTournees() }

    fun loadTourneeClients(tourneeId: Int) {
        viewModelScope.launch {
            try {
                _tourneeClients.value = repository.getTourneeClientsWithDetails(tourneeId)
            } catch (e: Exception) {
                android.util.Log.e("DISTRIGO", "tournee clients error: ${e.message}")
            }
        }
    }

    fun addClientsToTournee(
        tourneeId : Int,
        clientIds : List<Int>,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.addClientsToTournee(tourneeId, clientIds)
                loadTourneeClients(tourneeId)
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))
            }
        }
    }

    fun setCurrentTourneeClient(
        tourneeId : Int,
        clientId  : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.setCurrentTourneeClient(tourneeId, clientId)
                loadTourneeClients(tourneeId)
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))
            }
        }
    }

    fun markTourneeClientVisited(
        tourneeId : Int,
        clientId  : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.markTourneeClientVisited(tourneeId, clientId)
                loadTourneeClients(tourneeId)
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))
            }
        }
    }

    fun loadTournees() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _tournees.value = repository.getTournees()
                _error.value = null
            } catch (e: Exception) {
                _error.value = extractErrorMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadTourneeDetail(id: Int) {
        viewModelScope.launch {
            try {
                _selectedTournee.value = repository.getTournee(id)
            } catch (e: Exception) {
                android.util.Log.e("DISTRIGO", "tournee detail error: ${e.message}")
            }
        }
    }

    fun loadOpenTournee() {
        viewModelScope.launch {
            try {
                _openTournee.value = repository.getOpenTournee()
            } catch (e: Exception) {
                android.util.Log.e("DISTRIGO", "open tournee error: ${e.message}")
                _openTournee.value = null
            }
        }
    }

    fun createTournee(
        nom          : String,
        wilayaName   : String?,
        communeName  : String?,
        chauffeur    : String?,
        vehicule     : String?,
        note         : String?,
        onSuccess    : () -> Unit,
        onError      : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.createTournee(nom, wilayaName, communeName, chauffeur, vehicule, note)
                loadTournees()
                loadOpenTournee()
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))
            }
        }
    }

    fun closeTournee(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.closeTournee(id)
                loadTournees()
                loadOpenTournee()
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))
            }
        }
    }

    fun reopenTournee(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.reopenTournee(id)
                loadTournees()
                loadOpenTournee()
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))
            }
        }
    }

    fun updateTournee(
        id           : Int,
        nom          : String,
        wilayaName   : String?,
        communeName  : String?,
        chauffeur    : String?,
        vehicule     : String?,
        note         : String?,
        onSuccess    : () -> Unit,
        onError      : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updateTournee(id, nom, wilayaName, communeName, chauffeur, vehicule, note)
                loadTournees()
                loadTourneeDetail(id)
                onSuccess()
            } catch (e: Exception) {
                onError(extractErrorMessage(e))
            }
        }
    }
    fun deleteTournee(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = repository.deleteTournee(id)
                if (result.containsKey("error")) {
                    onError(result["error"] as String)
                } else {
                    loadTournees()
                    loadOpenTournee()
                    onSuccess()
                }
            } catch (e: Exception) {
                onError(extractErrorMessage(e))
            }
        }
    }

    fun refreshAfterVenteChange(tourneeId: Int) {
        loadTournees()
        loadTourneeDetail(tourneeId)
        loadTourneeClients(tourneeId)
    }


}


