package com.distrigo.app.ui.ventes

import com.distrigo.app.data.model.DraftBaseState
import com.distrigo.app.data.model.Vente
import com.distrigo.app.data.model.VenteDraft
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.VenteDraftRepository
import com.distrigo.app.ui.common.extractErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VenteViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val draftRepository: VenteDraftRepository
) : ViewModel() {

    // ── Brouillons ──
    // Room-observed, so the "Brouillons (N)" chip and the list stay live while a draft is being
    // written from the form graph. Reads only — a draft is created and deleted by the session
    // ViewModel and by the commit transaction, never from here.
    val drafts: StateFlow<List<VenteDraft>> = draftRepository.observeDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val draftCount: StateFlow<Int> = draftRepository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The pending edit draft for a vente, if any. Every route into edit mode has to ask first. */
    suspend fun draftForVente(venteId: Int): VenteDraft? = draftRepository.draftForVente(venteId)

    /** Whether a draft can still be applied, once its vente has been re-read. */
    suspend fun resolveDraftBase(draft: VenteDraft): DraftBaseState =
        draftRepository.resolveBaseState(draft)

    fun deleteDraft(id: Int) { viewModelScope.launch { draftRepository.delete(id) } }

    /** Deletes every selected draft in one statement. Empty input is a no-op. */
    fun deleteDrafts(ids: Collection<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { draftRepository.deleteAll(ids.toList()) }
    }

    private val _ventes = MutableStateFlow<List<Vente>>(emptyList())
    val ventes: StateFlow<List<Vente>> = _ventes

    private val _selectedVente = MutableStateFlow<Vente?>(null)
    val selectedVente: StateFlow<Vente?> = _selectedVente

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Form state (client / cart / note / userName / montantPaye) used to live here, shared across
    // the vente_form_graph destinations. It moved to VenteFormSessionViewModel, which is scoped to
    // that graph's back stack entry instead of to the whole tab — so it is destroyed when the user
    // leaves the form rather than lingering until the tab goes away, and its SavedStateHandle
    // survives process death. This ViewModel keeps the list, the detail, the commands and the
    // filters.

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
        draftId     : Int? = null,
        /** A `tournee_vente_drafts` row to delete inside the sale's own transaction. */
        tourneeDraftId : Int? = null,
        onSuccess   : () -> Unit,
        onError     : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.createVente(clientId, tourneeId, source, items, note, montantPaye, userName, draftId, tourneeDraftId)
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
        draftId     : Int? = null,
        onSuccess   : () -> Unit,
        onError     : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updateVente(id, clientId, items, note, montantPaye, userName, draftId)
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


