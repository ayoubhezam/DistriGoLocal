package com.distrigo.app.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.InventoryItem
import com.distrigo.app.data.model.InventorySession
import com.distrigo.app.data.model.InventorySessionSummary
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.repository.InventoryRepository
import com.distrigo.app.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.distrigo.app.data.local.paging.ProductListQuery
import com.distrigo.app.data.local.paging.ProductSort
import com.distrigo.app.ui.common.PagedProductList
import com.distrigo.app.ui.common.debouncedSearch
import com.distrigo.app.ui.products.ProductLookup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.distrigo.app.core.paging.PagingDefaults
import com.distrigo.app.data.local.paging.InventorySessionListQuery
import com.distrigo.app.data.local.paging.InventorySessionPagingSource
import com.distrigo.app.data.time.BusinessDates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlinx.coroutines.launch
import com.distrigo.app.data.model.InventorySessionHistory
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    // ── Session active (Draft) ──
    private val _activeSession = MutableStateFlow<InventorySession?>(null)
    val activeSession: StateFlow<InventorySession?> = _activeSession

    // -- The count in progress --
    //
    // Its figures are kept up to date line by line, not re-read: the count used to reload every line
    // it held after each scan, edit and delete — on a full count, 15,000 lines read back to show three
    // numbers, more of them with every scan. Now a scan costs its own write and nothing else; the lines
    // themselves are only read, a page at a time, where they are listed (the review and the écarts).

    private val _counts = MutableStateFlow(InventorySessionSummary(total_products = 0, total_ecarts = 0, total_value_ecarts = 0.0))

    /** Lines counted, lines with an écart, and the écarts' value, for the count in progress. */
    val counts: StateFlow<InventorySessionSummary> = _counts

    // Each write and the figures' update after it are one step, and so is a re-read of the figures:
    // a re-read can then never land between a write and its update and count a line twice, or miss one.
    // It also takes rapid scans one at a time, in the order they were made.
    private val countLock = Mutex()

    /** The figures moved by a line going from ([oldEcart], [oldValeur]) to ([newEcart], [newValeur]); null for none. */
    private fun InventorySessionSummary.moved(oldEcart: Double?, oldValeur: Double, newEcart: Double?, newValeur: Double) =
        InventorySessionSummary(
            total_products     = total_products + (if (newEcart != null) 1 else 0) - (if (oldEcart != null) 1 else 0),
            total_ecarts       = total_ecarts + (if (newEcart != null && newEcart != 0.0) 1 else 0) - (if (oldEcart != null && oldEcart != 0.0) 1 else 0),
            total_value_ecarts = total_value_ecarts + abs(newValeur) - abs(oldValeur),
        )

    /** The count's lines, a page at a time, live while listed. Collected by the screen that lists them. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun sessionItemPages(): Flow<PagingData<InventoryItem>> =
        _activeSession.flatMapLatest { session ->
            if (session == null) flowOf(PagingData.empty()) else repository.pageSessionItems(session.id, live = true)
        }

    // ── Produits (pour "Rechercher un produit") — observés depuis Room, mise à jour automatique ──
    // -- Finding a product to count --
    //
    // By scan or by search, from the database: the session used to hold the whole catalogue for both.

    /** The search dialog's text. */
    var productSearch by mutableStateOf("")

    /** The search dialog's products, paged, newest first as the dialog always listed them. */
    val productList = PagedProductList(
        scope      = viewModelScope,
        repository = productRepository,
        query      = debouncedSearch { productSearch }.map { ProductListQuery(search = it, sort = ProductSort.NEWEST) },
    )

    /** The product a scanned code belongs to, or null. */
    suspend fun productByBarcode(code: String): Product? = productRepository.findLiveProductByBarcode(code)

    /** One product, live: the count and confirmation steps' subject. */
    fun observeProduct(id: Int): Flow<ProductLookup> =
        productRepository.observeProduct(id).map { product -> product?.let { ProductLookup.Found(it) } ?: ProductLookup.Gone }

    // ── Résumé après "Terminer l'inventaire" ──
    private val _summary = MutableStateFlow<InventorySessionSummary?>(null)
    val summary: StateFlow<InventorySessionSummary?> = _summary

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // -- History --
    //
    // Paged from the database, each page's totals summed in SQL for its sessions alone: the history
    // used to read every session and sum every line of every inventory each time it opened.

    /** The history's search text. */
    var historySearch by mutableStateOf("")

    // The source in use, so the history can be told to reload — see InventorySessionPagingSource.
    private var historySource: InventorySessionPagingSource? = null

    /** The sessions, newest first, with a header before each day's first session. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val history: Flow<PagingData<InventoryHistoryItem>> = debouncedSearch { historySearch }
        .flatMapLatest { search ->
            Pager(PagingDefaults.config) {
                repository.sessionHistorySource(InventorySessionListQuery(search)).also { historySource = it }
            }.flow
        }
        .map { page -> page.map<InventorySessionHistory, InventoryHistoryItem> { InventoryHistoryItem.Row(it) }.withDayHeaders() }
        .cachedIn(viewModelScope)

    private val _detailSessionId = MutableStateFlow<Int?>(null)

    /** Points the detail at a session. Called by the detail screen as it opens. */
    fun showSessionDetail(sessionId: Int) { _detailSessionId.value = sessionId }

    /** The detail's lines, a page at a time — a count can hold the whole catalogue. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val historyItems: Flow<PagingData<InventoryItem>> = _detailSessionId
        .flatMapLatest { id -> if (id == null) flowOf(PagingData.empty()) else repository.pageSessionItems(id, live = false) }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            startOrResumeSession()
        }
    }

    fun startOrResumeSession() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val session = repository.getOrCreateActiveSession()
                _activeSession.value = session
                // The figures from the lines themselves, once, as the count opens.
                countLock.withLock { _counts.value = repository.getSessionSummary(session.id) }
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Whether the count already has [productId] — asked of the database, through the unique index on
     * (session, product), where it was looked for in the list the count kept in memory. That list
     * could lag a scan behind, so a product scanned twice in quick succession got past it.
     */
    suspend fun isProductAlreadyScanned(productId: Int): Boolean {
        val sessionId = _activeSession.value?.id ?: return false
        return repository.isProductAlreadyScanned(sessionId, productId)
    }

    fun recordScan(
        productId   : Int,
        qtePhysique : Double,
        userName    : String? = null,
        onSuccess   : (qteSysteme: Double, ecart: Double, valeurEcart: Double) -> Unit,
        onError     : (String) -> Unit
    ) {
        val sessionId = _activeSession.value?.id ?: return onError("Aucune session active")
        viewModelScope.launch {
            val result = countLock.withLock {
                repository.recordScan(sessionId, productId, qtePhysique, userName).also { result ->
                    if (!result.containsKey("error")) {
                        _counts.value = _counts.value.moved(null, 0.0, result["ecart"] as Double, result["valeur_ecart"] as Double)
                    }
                }
            }
            if (result.containsKey("error")) {
                onError(result["error"] as String)
            } else {
                _lastScanResult.value = LastScanResult(
                    productId   = productId,
                    qtePhysique = qtePhysique,
                    qteSysteme  = result["qte_systeme"] as Double,
                    ecart       = result["ecart"] as Double
                )
                onSuccess(
                    result["qte_systeme"] as Double,
                    result["ecart"] as Double,
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
            // The final figures from the lines themselves, after any write still under way.
            val summary = countLock.withLock { repository.getSessionSummary(sessionId) }
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

    /**
     * Reloads the history where it stands — leaving a count or finishing one. Paging keeps the rows
     * in view where they were.
     */
    fun loadHistory() {
        historySource?.invalidate()
    }

    fun updateScan(itemId: Int, newQtePhysique: Double, userName: String? = null, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _activeSession.value ?: return onError("Aucune session active")
        viewModelScope.launch {
            val result = countLock.withLock {
                repository.updateScan(itemId, newQtePhysique, userName).also { result ->
                    if (!result.containsKey("error")) {
                        _counts.value = _counts.value.moved(
                            result["old_ecart"] as Double, result["old_valeur_ecart"] as Double,
                            result["ecart"] as Double, result["valeur_ecart"] as Double,
                        )
                    }
                }
            }
            if (result.containsKey("error")) {
                onError(result["error"] as String)
            } else {
                onSuccess()
            }
        }
    }
    fun deleteScan(itemId: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _activeSession.value ?: return onError("Aucune session active")
        viewModelScope.launch {
            val result = countLock.withLock {
                repository.deleteScan(itemId).also { result ->
                    if (!result.containsKey("error")) {
                        _counts.value = _counts.value.moved(result["ecart"] as Double, result["valeur_ecart"] as Double, null, 0.0)
                    }
                }
            }
            if (result.containsKey("error")) {
                onError(result["error"] as String)
            } else {
                onSuccess()
            }
        }
    }

    // ── Nom de l'utilisateur (partagé entre les étapes Scan/Quantity/Review) ──
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName
    fun setUserName(name: String) { _userName.value = name }

    // ── Dernier scan confirmé (pour l'écran Confirmed) ──
    data class LastScanResult(
        val productId   : Int,
        val qtePhysique : Double,
        val qteSysteme  : Double,
        val ecart       : Double
    )
    private val _lastScanResult = MutableStateFlow<LastScanResult?>(null)
    val lastScanResult: StateFlow<LastScanResult?> = _lastScanResult
}

/** A row of the inventory history: a day's header, or a session. */
sealed interface InventoryHistoryItem {
    data class DayHeader(val day: String) : InventoryHistoryItem
    data class Row(val entry: InventorySessionHistory) : InventoryHistoryItem
}

/** The local day a session is listed under: when it finished, or when it started while a draft. */
private fun InventorySessionHistory.day(): String = BusinessDates.localDay(session.completed_at ?: session.started_at)

/** A header before the first session of each local day, inserted between loaded rows as they load. */
private fun PagingData<InventoryHistoryItem>.withDayHeaders(): PagingData<InventoryHistoryItem> =
    insertSeparators { before, after ->
        val next = (after as? InventoryHistoryItem.Row)?.entry ?: return@insertSeparators null
        val previous = (before as? InventoryHistoryItem.Row)?.entry
        if (previous == null || previous.day() != next.day()) InventoryHistoryItem.DayHeader(next.day()) else null
    }
