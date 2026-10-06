package com.distrigo.app.ui.rapports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportFilterStore
import com.distrigo.app.data.repository.ProductGrouping
import com.distrigo.app.data.repository.ProductRanking
import com.distrigo.app.data.repository.ProductReport
import com.distrigo.app.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Produits report as the screen shows it; [report] stays on screen while the next one loads. */
data class ProduitsReportState(
    val filter: ReportFilter = ReportFilter(),
    val report: ProductReport? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * The Produits report and its two full lists — every product ranked, and the stocked ones that did not
 * sell — held by the Rapports graph, so the lists open on the report already loaded.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ProduitsReportViewModel @Inject constructor(
    private val repository: ReportRepository,
    private val filterStore: ReportFilterStore,
) : ViewModel() {

    // A sale, or a product's price or stock changing, reloads it — a burst of writes once.
    private val changes = repository.productChanges().debounce(400).onStart { emit(Unit) }
    private var last = ProduitsReportState()

    val state: StateFlow<ProduitsReportState> = combine(filterStore.filter, changes) { filter, _ -> filter }
        .flatMapLatest { filter ->
            flow {
                emit(last.copy(filter = filter, loading = true, error = null))
                val next = runCatching { repository.productReport(filter) }.fold(
                    onSuccess = { ProduitsReportState(filter, it, loading = false) },
                    onFailure = { last.copy(filter = filter, loading = false, error = it.message ?: "Erreur de chargement") },
                )
                last = next
                emit(next)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProduitsReportState())

    val ranking = MutableStateFlow(ProductRanking.CA)
    val grouping = MutableStateFlow(ProductGrouping.CATEGORIE)
    /** The full lists' search. */
    val query = MutableStateFlow("")

    fun setFilter(filter: ReportFilter) = filterStore.update { filter }
}
