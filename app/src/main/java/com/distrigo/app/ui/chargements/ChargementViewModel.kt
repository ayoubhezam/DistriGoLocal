package com.distrigo.app.ui.chargements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.Chargement
import com.distrigo.app.data.model.ChargementSession
import com.distrigo.app.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChargementViewModel @Inject constructor(
    private val repository: ProductRepository
) : ViewModel() {

    private val _chargements = MutableStateFlow<List<Chargement>>(emptyList())
    val chargements: StateFlow<List<Chargement>> = _chargements

    private val _selectedChargement = MutableStateFlow<Chargement?>(null)
    val selectedChargement: StateFlow<Chargement?> = _selectedChargement

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ── Chargement form (wizard) state — shared across ChargementNavHost's Products/Cart steps ──
    private val _formCartItems = MutableStateFlow<List<ChargementCartItem>>(emptyList())
    val formCartItems: StateFlow<List<ChargementCartItem>> = _formCartItems

    private val _formNote = MutableStateFlow("")
    val formNote: StateFlow<String> = _formNote

    private val _formUserName = MutableStateFlow("")
    val formUserName: StateFlow<String> = _formUserName

    fun setFormCartItems(items: List<ChargementCartItem>) { _formCartItems.value = items }
    fun setFormNote(note: String) { _formNote.value = note }
    fun setFormUserName(name: String) { _formUserName.value = name }

    fun resetChargementForm() {
        _formCartItems.value = emptyList()
        _formNote.value = ""
        _formUserName.value = ""
    }

    init { loadChargements() }

    fun loadChargements() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _chargements.value = repository.getChargements()
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadChargementDetail(id: Int) {
        viewModelScope.launch {
            try {
                _selectedChargement.value = repository.getChargement(id)
            } catch (e: Exception) {
                android.util.Log.e("DISTRIGO", "chargement detail error: ${e.message}")
            }
        }
    }

    fun createChargement(
        note      : String?,
        userName  : String? = null,
        items     : List<Map<String, Any?>>,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.createChargement(note, items, userName)
                loadChargements()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun deleteChargement(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteChargement(id)
                loadChargements()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    private val _sessions = MutableStateFlow<List<ChargementSession>>(emptyList())
    val sessions: StateFlow<List<ChargementSession>> = _sessions

    private val _selectedSession = MutableStateFlow<ChargementSession?>(null)
    val selectedSession: StateFlow<ChargementSession?> = _selectedSession

    fun loadSessions() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _sessions.value = repository.getChargementSessions()
                _error.value = null
            } catch (e: Exception) {
                _error.value = com.distrigo.app.data.api.extractErrorMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSessionDetail(id: Int) {
        viewModelScope.launch {
            try {
                _selectedSession.value = repository.getChargementSession(id)
            } catch (e: Exception) {
                android.util.Log.e("DISTRIGO", "session detail error: ${e.message}")
            }
        }
    }

    fun updateSessionNote(
        id        : Int,
        note      : String?,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updateChargementSessionNote(id, note)
                loadSessions()
                onSuccess()
            } catch (e: Exception) {
                onError(com.distrigo.app.data.api.extractErrorMessage(e))
            }
        }
    }
}
