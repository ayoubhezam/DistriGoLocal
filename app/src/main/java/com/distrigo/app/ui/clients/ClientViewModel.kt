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
import com.distrigo.app.data.model.ClientTransaction
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
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * ترجع الولاية الأكثر استعمالًا إن ظهرت في زبونين أو أكثر، وإلا null.
     * تُستعمل لتعبئة حقل Wilaya تلقائيًا عند إضافة زبون جديد (قابل للتغيير من المستخدم).
     */
    fun getDefaultWilaya(): String? {
        val counts = clients.value
            .mapNotNull { it.wilaya_name?.takeIf { name -> name.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
        val (name, count) = counts.maxByOrNull { it.value } ?: return null
        return if (count >= 2) name else null
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

    private val _transactions = MutableStateFlow<List<ClientTransaction>>(emptyList())
    val transactions: StateFlow<List<ClientTransaction>> = _transactions

    fun loadTransactions(clientId: Int) {
        viewModelScope.launch {
            try {
                _transactions.value = repository.getClientTransactions(clientId)
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


