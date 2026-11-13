package com.distrigo.app.ui.clients

import com.distrigo.app.data.lists.ListsRepository
import com.distrigo.app.data.lists.QuartierSummary
import kotlinx.coroutines.withContext
import com.distrigo.app.ui.common.userMessage

import com.distrigo.app.data.model.Client
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.distrigo.app.data.model.ClientLedgerPreview
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.Secteur
import com.distrigo.app.ui.common.extractErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.distrigo.app.ui.common.ClientListFilters
import com.distrigo.app.ui.common.debouncedSearch
import com.distrigo.app.ui.common.filterClients
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.distrigo.app.data.local.paging.ClientListQuery
import com.distrigo.app.data.local.paging.PartySummary
import com.distrigo.app.ui.common.ClientPickerList
import com.distrigo.app.ui.common.placesOf
import com.distrigo.app.ui.common.toQuery

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClientViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val lists: ListsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * Set when the list picks clients for a quartier (« Ajouter des clients »): it then lists only those not in it
     * already, as the query reads them. Null for the Clients list itself.
     */
    private val outsideQuartier: Int? = savedStateHandle.get<Long>(ARG_QUARTIER_ID)?.takeIf { it > 0 }?.toInt()

    /** The Clients list's filter sheet. Here, not in the screen, so opening a client and coming back keeps it. */
    var listFilters by mutableStateOf(ClientListFilters())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _secteurs = MutableStateFlow<List<Secteur>>(emptyList())
    val secteurs: StateFlow<List<Secteur>> = _secteurs

    /** The Clients list's search box, as typed. */
    var listSearch by mutableStateOf("")

    /**
     * The Clients list as the search and the sheet ask for it, once typing pauses. Searched, filtered, counted and
     * paged in SQL (PERF-01): the list used to hold every client, mapped again after every sale.
     */
    private val listQuery: Flow<ClientListQuery> = combine(snapshotFlow { listFilters }, debouncedSearch { listSearch }) { filters, search ->
        filters.toQuery(search, outsideQuartier)
    }.distinctUntilChanged()

    val pagedClients: Flow<PagingData<Client>> = listQuery.flatMapLatest { repository.pageClients(it) }.cachedIn(viewModelScope)

    /** How many clients the list shows; null until counted. */
    val shownCount: StateFlow<Int?> = listQuery.flatMapLatest { repository.observeClientCount(it) }
        .catch { e -> _error.value = userMessage(e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** All of them, whatever is searched: how many, and how many owe how much. Null until counted. */
    val summary: StateFlow<PartySummary?> = repository.observeClientSummary(ClientListQuery(outsideQuartier = outsideQuartier))
        .catch { e -> _error.value = userMessage(e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The places the clients have, for the filter sheet ([column]: wilaya_name, commune_name or secteur_name). */
    suspend fun places(column: String, inWilaya: String? = null): List<String> =
        placesOf(repository.clientPlaces(ClientListQuery(outsideQuartier = outsideQuartier), column, inWilaya))

    /** One client, live: null once deleted. */
    fun observeClient(id: Int): Flow<Client?> = repository.observeClient(id)

    /** « Choisir un client »: the forms' picker. */
    val picker: ClientPickerList by lazy { ClientPickerList(viewModelScope, repository, ClientListQuery()) }

    private val pickers = mutableMapOf<ClientListQuery, ClientPickerList>()

    /** A picker over [base]: « Ajouter des clients » to a tournée, which leaves out those it has. */
    fun picker(base: ClientListQuery): ClientPickerList = pickers.getOrPut(base) { ClientPickerList(viewModelScope, repository, base) }

    /**
     * ترجع الولاية الأكثر استعمالًا إن ظهرت في زبونين أو أكثر، وإلا null.
     * تُستعمل لتعبئة حقل Wilaya تلقائيًا عند إضافة زبون جديد (قابل للتغيير من المستخدم).
     *
     * Asks the database rather than reading [clients]. This used to count over `clients.value`,
     * which meant it only worked while something else was collecting that flow — an invisible
     * dependency on which screen happened to be composed, and one that
     * `SharingStarted.WhileSubscribed` could have quietly broken. See
     * [com.distrigo.app.data.local.dao.ClientDao.getMostCommonWilaya].
     */
    suspend fun getDefaultWilaya(): String? = repository.getMostCommonClientWilaya()

    private val _quartiers = MutableStateFlow<List<QuartierSummary>>(emptyList())
    val quartiers: StateFlow<List<QuartierSummary>> = _quartiers

    /** The form's quartier picker: secteur [secteurId]'s quartiers; none without a secteur. */
    fun loadQuartiers(secteurId: Int?) {
        viewModelScope.launch {
            _quartiers.value = if (secteurId == null) emptyList() else withContext(Dispatchers.IO) {
                runCatching { lists.quartiers(secteurId.toLong()) }.getOrElse { emptyList() }
            }
        }
    }

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
        nom          : String,
        communeName  : String,
        wilayaName   : String?,
        onSuccess    : (Secteur) -> Unit,
        onError      : (String) -> Unit
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

    // What the detail screen shows of the client's sales and payments: the figures, and the latest
    // four entries (the screen shows two, or four expanded). The whole history is the paged
    // "Voir tout l'historique" screen's.
    private val _ledger = MutableStateFlow(ClientLedgerPreview())
    val ledger: StateFlow<ClientLedgerPreview> = _ledger

    fun loadTransactions(clientId: Int) {
        viewModelScope.launch {
            try {
                _ledger.value = repository.getClientLedgerPreview(clientId, limit = 4)
            } catch (e: Exception) {
                android.util.Log.e("DISTRIGO", "client transactions error: ${e.message}")
            }
        }
    }

    fun addPayment(
        clientId  : Int,
        amount    : Double,
        note      : String?,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.addClientPayment(clientId, amount, note)
                loadTransactions(clientId)
                onSuccess()
            } catch (e: Exception) {
                onError(com.distrigo.app.ui.common.extractErrorMessage(e))
            }
        }
    }

    fun deletePayment(
        clientId  : Int,
        paymentId : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteClientPayment(clientId, paymentId)
                loadTransactions(clientId)
                onSuccess()
            } catch (e: Exception) {
                onError(com.distrigo.app.ui.common.extractErrorMessage(e))
            }
        }
    }

    fun updatePayment(
        clientId  : Int,
        paymentId : Int,
        amount    : Double,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updateClientPayment(clientId, paymentId, amount)
                loadTransactions(clientId)
                onSuccess()
            } catch (e: Exception) {
                onError(com.distrigo.app.ui.common.extractErrorMessage(e))
            }
        }
    }

    fun addClient(
        client    : Map<String, Any?>,
        onSuccess : (Map<String, Any>) -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = repository.addClient(client)
                onSuccess(result)
            } catch (e: Exception) {
                onError(userMessage(e))
            }
        }
    }

    fun updateClient(
        id        : Int,
        client    : Map<String, Any?>,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updateClient(id, client)
                onSuccess()
            } catch (e: Exception) {
                onError(userMessage(e))
            }
        }
    }

    /** [onError] gets the refusal itself: the screen words it, amounts in the business's format (refusalMessage). */
    fun deleteClient(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (Throwable) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteClient(id)
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    // Résout un client précis directement depuis la base (lecture one-shot) — le flux observé
    // `clients` se met à jour tout seul ; ceci sert uniquement au rappel immédiat après création
    // (auto-sélection du nouveau client) sans dépendre du timing d'émission du flux.
    fun loadClientsAndUpdate(clientId: Int, onUpdated: (Client?) -> Unit) {
        viewModelScope.launch {
            try {
                onUpdated(repository.getClient(clientId))
            } catch (e: Exception) {
                _error.value = userMessage(e)
            }
        }
    }

    companion object {
        /** The quartier « Ajouter des clients » picks for (a Long route argument). */
        const val ARG_QUARTIER_ID = "quartierId"
    }
}
