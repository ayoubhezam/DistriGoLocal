package com.distrigo.app.ui.rapports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportFilterStore
import com.distrigo.app.data.repository.DebtReport
import com.distrigo.app.data.repository.DebtSide
import com.distrigo.app.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.distrigo.app.data.repository.DebtorLine
import javax.inject.Inject

/** The Créances et dettes report as the screen shows it; [report] stays while the next one loads. */
data class DebtReportState(
    val filter: ReportFilter = ReportFilter(),
    val side: DebtSide = DebtSide.CLIENTS,
    val report: DebtReport? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class DebtReportViewModel @Inject constructor(
    private val repository: ReportRepository,
    private val filterStore: ReportFilterStore,
) : ViewModel() {

    // The side is this screen's own choice; the period is the one all reports share.
    private val side = MutableStateFlow(DebtSide.CLIENTS)
    // A reload when the report's tables are written — a payment, a sale — and only then: a burst of
    // writes settles into one reload. The first load does not wait.
    private val changes = repository.debtChanges().debounce(400).onStart { emit(Unit) }
    private var last = DebtReportState()

    val state: StateFlow<DebtReportState> = combine(filterStore.filter, side, changes) { filter, side, _ -> filter to side }
        .flatMapLatest { (filter, side) ->
            flow {
                // A report of the other side is not shown under this one's switch while it loads.
                val kept = last.report?.takeIf { it.side == side }
                emit(last.copy(filter = filter, side = side, report = kept, loading = true, error = null))
                val next = runCatching { repository.debtReport(side, filter) }.fold(
                    onSuccess = { DebtReportState(filter, side, it, loading = false) },
                    onFailure = { DebtReportState(filter, side, kept, loading = false, error = it.message ?: "Erreur de chargement") },
                )
                last = next
                emit(next)
            }
        }
        // Kept running while the ViewModel lives — under a client opened from the report too — so coming
        // back finds the report loaded, instead of restarting it and querying again.
        .stateIn(viewModelScope, SharingStarted.Eagerly, DebtReportState())

    fun setFilter(filter: ReportFilter) = filterStore.update { filter }

    fun setSide(value: DebtSide) {
        if (side.value != value) {
            // A search typed among clients means nothing among suppliers.
            debtorQuery.value = ""
            debtorSort.value = DebtorSort.DETTE_DESC
        }
        side.value = value
    }

    // ── The full list ("Voir tout"): its search and its order, kept while the report lives ──

    val debtorQuery = MutableStateFlow("")
    val debtorSort = MutableStateFlow(DebtorSort.DETTE_DESC)

    fun setDebtorQuery(value: String) { debtorQuery.value = value }

    fun setDebtorSort(value: DebtorSort) { debtorSort.value = value }

    /**
     * The full list as its search and its order leave it, worked out off the main thread once typing
     * pauses (as Produits' and Clients' searches do); a new order applies at once. Null until the report
     * has loaded and the first list is ready.
     *
     * The screen used to filter and sort in composition on every key — by name, with a Collator, over
     * every debtor (UI fluidity audit, Step 7). Now only its search box reads the keys.
     */
    val debtorList: StateFlow<DebtorList?> = combine(
        state.map { it.report?.debtors }.distinctUntilChanged { a, b -> a === b },
        debtorQuery.debounce { if (it.isEmpty()) 0L else SEARCH_PAUSE_MS },
        debtorSort,
    ) { debtors, query, sort ->
        debtors?.let { DebtorList(query, sort, debtorsMatching(it, query, sort), total = it.size) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * The full list as shown: [lines] of [total] debtors, with the [query] and [sort] they were worked out
 * for, so the count, the "no result" line and the scroll to the top agree with what is on screen even
 * while a newer search is still waiting for typing to pause.
 */
data class DebtorList(val query: String, val sort: DebtorSort, val lines: List<DebtorLine>, val total: Int)

/** How long typing has to pause before the list follows, as everywhere else in the app. */
private const val SEARCH_PAUSE_MS = 300L
