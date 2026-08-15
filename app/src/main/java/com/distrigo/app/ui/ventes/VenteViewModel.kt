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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

    // ── Draft state for the multi-step Vente form (Client → Products → Cart → Validation) ──
    // Shared across the nested vente_form_graph destinations so each step reads/writes the
    // same in-progress draft instead of local remember state, exactly like InventoryViewModel's
    // userName/lastScanResult support the Inventory session wizard's separate destinations.
    private val _formClient = MutableStateFlow<com.distrigo.app.data.model.Client?>(null)
    val formClient: StateFlow<com.distrigo.app.data.model.Client?> = _formClient
    fun setFormClient(client: com.distrigo.app.data.model.Client?) { _formClient.value = client }

    private val _formCartItems = MutableStateFlow<List<VenteCartItem>>(emptyList())
    val formCartItems: StateFlow<List<VenteCartItem>> = _formCartItems
    fun setFormCartItems(items: List<VenteCartItem>) { _formCartItems.value = items }

    private val _formNote = MutableStateFlow("")
    val formNote: StateFlow<String> = _formNote
    fun setFormNote(note: String) { _formNote.value = note }

    private val _formUserName = MutableStateFlow("")
    val formUserName: StateFlow<String> = _formUserName
    fun setFormUserName(name: String) { _formUserName.value = name }

    private val _formMontantPaye = MutableStateFlow("")
    val formMontantPaye: StateFlow<String> = _formMontantPaye
    fun setFormMontantPaye(value: String) { _formMontantPaye.value = value }

    /** Called once when a form graph session is freshly entered — clears any leftover draft
     * from a previous session (new vente, or a previously edited/cancelled vente). */
    fun resetVenteForm() {
        _formClient.value = null
        _formCartItems.value = emptyList()
        _formNote.value = ""
        _formUserName.value = ""
        _formMontantPaye.value = ""
    }

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


    // ── Filter state ──
    var searchQuery          by mutableStateOf("")
    var filterStatus         by mutableStateOf<String?>(null)
    var filterPaymentStatus  by mutableStateOf<String?>(null)
    var filterClientId       by mutableStateOf<Int?>(null)
    var filterDateFrom       by mutableStateOf<String?>(null)
    var filterDateTo         by mutableStateOf<String?>(null)

    fun clearAllFilters() {
        searchQuery         = ""
        filterStatus        = null
        filterPaymentStatus = null
        filterClientId      = null
        filterDateFrom      = null
        filterDateTo        = null
    }
}


