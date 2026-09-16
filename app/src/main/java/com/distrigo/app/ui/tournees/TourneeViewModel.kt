package com.distrigo.app.ui.tournees

import com.distrigo.app.data.model.Tournee
import com.distrigo.app.data.model.TourneeVenteDraft
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.TourneeVenteDraftRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import com.distrigo.app.ui.common.extractErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.TourneeClientInfo
import com.distrigo.app.data.model.Secteur
import com.distrigo.app.data.model.TourneeSecteur
import com.distrigo.app.data.model.Client
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TourneeViewModel @Inject constructor(
    private val repository     : ProductRepository,
    private val draftRepository: TourneeVenteDraftRepository
) : ViewModel() {

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

    // ── Brouillons of the tournée currently on screen ────────────────────────
    //
    // Scoped rather than global: a van sale belongs to the round it was made on, so "the drafts"
    // is always one tournée's. The collection is re-pointed when the screen changes tournée, and
    // the previous one cancelled — two open collectors would race to publish into the same flow.

    private val _venteDrafts = MutableStateFlow<List<TourneeVenteDraft>>(emptyList())
    val venteDrafts: StateFlow<List<TourneeVenteDraft>> = _venteDrafts

    private var venteDraftsJob: Job? = null
    private var venteDraftsTourneeId: Int? = null

    fun observeVenteDrafts(tourneeId: Int) {
        if (venteDraftsTourneeId == tourneeId && venteDraftsJob?.isActive == true) return
        venteDraftsTourneeId = tourneeId
        venteDraftsJob?.cancel()
        // Cleared rather than left showing the previous tournée's drafts until the first emission.
        _venteDrafts.value = emptyList()
        venteDraftsJob = viewModelScope.launch {
            draftRepository.observeDrafts(tourneeId).collectLatest { _venteDrafts.value = it }
        }
    }

    fun deleteVenteDraft(id: Int) {
        viewModelScope.launch { draftRepository.delete(id) }
    }

    /** Bulk delete for the Brouillons screen's selection mode. One statement, one invalidation. */
    fun deleteVenteDrafts(ids: Collection<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { draftRepository.deleteAll(ids.toList()) }
    }

    suspend fun venteDraft(id: Int): TourneeVenteDraft? = draftRepository.getDraft(id)

    // ── Tournée vente form (wizard) state — shared across TourneeVenteFormNavGraph steps ──
    private val _formClient = MutableStateFlow<Client?>(null)
    val formClient: StateFlow<Client?> = _formClient

    private val _formCartItems = MutableStateFlow<List<TourneeVenteCartItem>>(emptyList())
    val formCartItems: StateFlow<List<TourneeVenteCartItem>> = _formCartItems

    private val _formNote = MutableStateFlow("")
    val formNote: StateFlow<String> = _formNote

    private val _formMontantPaye = MutableStateFlow("")
    val formMontantPaye: StateFlow<String> = _formMontantPaye

    fun setFormClient(client: Client?) { _formClient.value = client }
    fun setFormCartItems(items: List<TourneeVenteCartItem>) { _formCartItems.value = items }
    fun setFormNote(note: String) { _formNote.value = note }
    fun setFormMontantPaye(value: String) { _formMontantPaye.value = value }

    fun resetTourneeVenteForm() {
        _formClient.value = null
        _formCartItems.value = emptyList()
        _formNote.value = ""
        _formMontantPaye.value = ""
    }



    // ── Secteurs of the commune currently chosen in the tournée form ────────────────────────
    //
    // Same pair as ClientViewModel's: the picker lists what exists for one commune, and can add to
    // it. Duplicated rather than shared because the two ViewModels are scoped to different graphs;
    // the list itself lives in the database, so they never disagree.

    private val _secteurs = MutableStateFlow<List<Secteur>>(emptyList())
    val secteurs: StateFlow<List<Secteur>> = _secteurs

    fun loadSecteurs(communeName: String) {
        viewModelScope.launch {
            try {
                _secteurs.value = repository.getSecteursForCommune(communeName)
            } catch (e: Exception) {
                android.util.Log.e("DISTRIGO", "secteurs error: ${e.message}")
            }
        }
    }

    fun createSecteur(
        nom         : String,
        communeName : String,
        wilayaName  : String?,
        onSuccess   : (Secteur) -> Unit,
        onError     : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val secteur = repository.createSecteur(nom, communeName, wilayaName)
                loadSecteurs(communeName)
                onSuccess(secteur)
            } catch (e: Exception) {
                onError(extractErrorMessage(e))
            }
        }
    }

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

    fun removeClientFromTournee(
        tourneeId : Int,
        clientId  : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.removeClientFromTournee(tourneeId, clientId)
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

    /**
     * ترجع الولاية الأكثر استعمالًا إن ظهرت في أكثر من 3 تورنيهات سابقة، وإلا null.
     * تُستعمل لتعبئة حقل Wilaya تلقائيًا عند إنشاء تورنيه جديدة (قابل للتغيير من المستخدم).
     */
    fun getDefaultWilaya(): String? {
        val counts = _tournees.value
            .mapNotNull { it.wilaya_name?.takeIf { name -> name.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
        val (name, count) = counts.maxByOrNull { it.value } ?: return null
        return if (count >= 2) name else null
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
        note         : String?,
        secteurs     : List<TourneeSecteur> = emptyList(),
        onSuccess    : () -> Unit,
        onError      : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.createTournee(nom, wilayaName, communeName, note, secteurs)
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
        note         : String?,
        secteurs     : List<TourneeSecteur> = emptyList(),
        onSuccess    : () -> Unit,
        onError      : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updateTournee(id, nom, wilayaName, communeName, note, secteurs)
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
                // Before the tournée goes, so a failure leaves both intact. There is no foreign
                // key to cascade, so nothing else would ever reach these rows again.
                draftRepository.deleteForTournee(id)
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

    // ── Vente filter state ──
    // Held here, not in the screen, so the vente list comes back filtered the way it was left
    // after opening a bon or leaving the tab — this ViewModel is scoped to the Tournées graph,
    // while the screen's own `remember`s die with its composition.
    //
    // Scoped to one tournée, like the drafts above: these read against a single round's ventes,
    // and the client axis is built from them, so a filter set on one tournée would be a puzzle
    // rather than a convenience on the next. prepareVenteFilters() drops them when the screen is
    // pointed somewhere else, and leaves them alone when it comes back to the same tournée.
    var venteQuery               by mutableStateOf("")
    var venteFilterStatus        by mutableStateOf<String?>(null)
    var venteFilterPaymentStatus by mutableStateOf<String?>(null)
    var venteFilterClientId      by mutableStateOf<Int?>(null)

    private var venteFiltersTourneeId: Int? = null

    fun prepareVenteFilters(tourneeId: Int) {
        if (venteFiltersTourneeId == tourneeId) return
        venteFiltersTourneeId = tourneeId
        venteQuery = ""
        clearVenteFilters()
    }

    /** The three filter axes only — the search box is cleared by its own button, as elsewhere. */
    fun clearVenteFilters() {
        venteFilterStatus        = null
        venteFilterPaymentStatus = null
        venteFilterClientId      = null
    }
}
