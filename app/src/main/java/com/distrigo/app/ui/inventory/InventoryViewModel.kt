package com.distrigo.app.ui.inventory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.InventoryItem
import com.distrigo.app.data.model.InventorySession
import com.distrigo.app.data.model.InventorySessionSummary
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.repository.InventoryRepository
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.distrigo.app.data.model.InventorySessionHistory
class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository        = InventoryRepository(db)
    private val productRepository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)

    // ── Session active (Draft) ──
    private val _activeSession = MutableStateFlow<InventorySession?>(null)
    val activeSession: StateFlow<InventorySession?> = _activeSession

    // ── Éléments scannés dans la session ──
    private val _sessionItems = MutableStateFlow<List<InventoryItem>>(emptyList())
    val sessionItems: StateFlow<List<InventoryItem>> = _sessionItems

    // ── Produits (pour "Rechercher un produit") ──
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products

    // ── Résumé après "Terminer l'inventaire" ──
    private val _summary = MutableStateFlow<InventorySessionSummary?>(null)
    val summary: StateFlow<InventorySessionSummary?> = _summary

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _history = MutableStateFlow<List<InventorySessionHistory>>(emptyList())
    val history: StateFlow<List<InventorySessionHistory>> = _history

    private val _historyItems = MutableStateFlow<List<InventoryItem>>(emptyList())
    val historyItems: StateFlow<List<InventoryItem>> = _historyItems

    init {
        viewModelScope.launch {
            loadProducts()
            startOrResumeSession()
        }
    }

    fun startOrResumeSession() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val session = repository.getOrCreateActiveSession()
                _activeSession.value = session
                loadSessionItems(session.id)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSessionItems(sessionId: Int) {
        viewModelScope.launch {
            try {
                _sessionItems.value = repository.getSessionItems(sessionId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun loadProducts() {
        viewModelScope.launch {
            try {
                _products.value = productRepository.getProducts()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    // ── Vérification locale rapide (sans requête DB) avant d'ouvrir l'écran de saisie ──
    fun isProductAlreadyScanned(productId: Int): Boolean {
        return _sessionItems.value.any { it.product_id == productId }
    }

    fun recordScan(
        productId   : Int,
        qtePhysique : Int,
        onSuccess   : (qteSysteme: Int, ecart: Int, valeurEcart: Double) -> Unit,
        onError     : (String) -> Unit
    ) {
        val sessionId = _activeSession.value?.id ?: return onError("Aucune session active")
        viewModelScope.launch {
            val result = repository.recordScan(sessionId, productId, qtePhysique)
            if (result.containsKey("error")) {
                onError(result["error"] as String)
            } else {
                loadSessionItems(sessionId)
                loadProducts()   // les stocks affichés (product.stock) ont changé
                onSuccess(
                    result["qte_systeme"] as Int,
                    result["ecart"] as Int,
                    result["valeur_ecart"] as Double
                )
            }
        }
    }

    fun finishSession(
        onSuccess : (InventorySessionSummary) -> Unit,
        onError   : (String) -> Unit
    ) {
        val sessionId = _activeSession.value?.id ?: return onError("Aucune session active")
        viewModelScope.launch {
            val summary = repository.getSessionSummary(sessionId)
            val result  = repository.finishSession(sessionId)
            if (result.containsKey("error")) {
                onError(result["error"] as String)
            } else {
                _summary.value = summary
                _activeSession.value = null
                onSuccess(summary)
            }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            try {
                _history.value = repository.getAllSessionsHistory()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun loadHistoryItems(sessionId: Int) {
        viewModelScope.launch {
            try {
                _historyItems.value = repository.getSessionItems(sessionId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun updateScan(itemId: Int, newQtePhysique: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val sessionId = _activeSession.value?.id ?: return onError("Aucune session active")
        viewModelScope.launch {
            val result = repository.updateScan(itemId, newQtePhysique)
            if (result.containsKey("error")) {
                onError(result["error"] as String)
            } else {
                loadSessionItems(sessionId)
                loadProducts()
                onSuccess()
            }
        }
    }

    fun deleteScan(itemId: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val sessionId = _activeSession.value?.id ?: return onError("Aucune session active")
        viewModelScope.launch {
            val result = repository.deleteScan(itemId)
            if (result.containsKey("error")) {
                onError(result["error"] as String)
            } else {
                loadSessionItems(sessionId)
                loadProducts()
                onSuccess()
            }
        }
    }
}