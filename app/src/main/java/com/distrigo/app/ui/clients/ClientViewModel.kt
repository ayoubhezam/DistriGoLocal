package com.distrigo.app.ui.clients

import com.distrigo.app.data.model.Client
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.distrigo.app.data.model.ClientTransaction
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.database.AppDatabase
class ClientViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)

    private val repository = ProductRepository(
        db.productDao(),
        db.categoryDao(),
        db.supplierDao(),
        db     = db
    )
    private val _clients = MutableStateFlow<List<Client>>(emptyList())
    val clients: StateFlow<List<Client>> = _clients

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { loadClients() }

    fun loadClients() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _clients.value = repository.getClients()
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
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
                loadClients()
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
                loadClients()
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
                loadClients()
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
                loadClients()
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
                loadClients()
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
                loadClients()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun loadClientsAndUpdate(clientId: Int, onUpdated: (Client?) -> Unit) {
        viewModelScope.launch {
            try {
                _clients.value = repository.getClients()
                val updated = _clients.value.find { it.id == clientId }
                onUpdated(updated)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}


