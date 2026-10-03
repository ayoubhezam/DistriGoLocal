package com.distrigo.app.ui.rapports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportFilterStore
import com.distrigo.app.data.repository.ReportRepository
import com.distrigo.app.data.repository.SalesReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.FlowPreview
import javax.inject.Inject

/**
 * The Ventes report as the screen shows it. [report] stays on screen while the next one loads, so
 * changing the period does not blank the page; [loading] says a newer one is on its way.
 */
data class VentesReportState(
    val filter: ReportFilter = ReportFilter(),
    val report: SalesReport? = null,
    val buckets: List<SalesBucket> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class VentesReportViewModel @Inject constructor(
    private val repository: ReportRepository,
    private val filterStore: ReportFilterStore,
) : ViewModel() {

    // A reload when the report's tables are written — a sale, a payment — and only then: a burst of
    // writes (one sale is several rows) settles into one reload. The first load does not wait.
    private val changes = repository.salesChanges().debounce(400).onStart { emit(Unit) }
    private var last = VentesReportState()

    val state: StateFlow<VentesReportState> = combine(filterStore.filter, changes) { filter, _ -> filter }
        .flatMapLatest { filter ->
            flow {
                emit(last.copy(filter = filter, loading = true, error = null))
                val next = runCatching { repository.salesReport(filter) }.fold(
                    onSuccess = { VentesReportState(filter, it, salesBuckets(it.days), loading = false) },
                    onFailure = { last.copy(filter = filter, loading = false, error = it.message ?: "Erreur de chargement") },
                )
                last = next
                emit(next)
            }
        }
        // Kept running while the ViewModel lives — under a client opened from the report too — so coming
        // back finds the report loaded, instead of restarting it and querying again.
        .stateIn(viewModelScope, SharingStarted.Eagerly, VentesReportState())

    fun setFilter(filter: ReportFilter) = filterStore.update { filter }
}
