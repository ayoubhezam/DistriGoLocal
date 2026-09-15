package com.distrigo.app.ui.clients

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.Secteur
import com.distrigo.app.data.api.extractErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ClientViewModel @Inject constructor(
    private val repository: ProductRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _secteurs = MutableStateFlow<List<Secteur>>(emptyList())
    val secteurs: StateFlow<List<Secteur>> = _secteurs

    // Room-observed single source of truth: re-emits on every write to the clients table
    // (ajout/modification/suppression, recalcul de solde après vente ou paiement…) — no manual refresh.
    val clients: StateFlow<List<Client>> = repository.observeClients()
        .onEach { _isLoading.value = false; _error.value = null }
        .catch { e -> _error.value = e.message; _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                onError(com.distrigo.app.data.api.extractErrorMessage(e))
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
                onError(com.distrigo.app.data.api.extractErrorMessage(e))
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
                onError(com.distrigo.app.data.api.extractErrorMessage(e))
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
                onError(e.message ?: "Erreur inconnue")
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
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun deleteClient(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteClient(id)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    // Résout un client précis directement depuis la base (lecture one-shot) — le flux observé
    // `clients` se met à jour tout seul ; ceci sert uniquement au rappel immédiat après création
    // (auto-sélection du nouveau client) sans dépendre du timing d'émission du flux.
    fun loadClientsAndUpdate(clientId: Int, onUpdated: (Client?) -> Unit) {
        viewModelScope.launch {
            try {
                onUpdated(repository.getClients().find { it.id == clientId })
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}


