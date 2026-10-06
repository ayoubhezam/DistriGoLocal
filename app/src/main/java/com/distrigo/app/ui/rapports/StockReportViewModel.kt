package com.distrigo.app.ui.rapports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportFilterStore
import com.distrigo.app.data.repository.ReportRepository
import com.distrigo.app.data.repository.StockReport
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

/** The Stock et pertes report as the screen shows it; [report] stays on screen while the next one loads. */
data class StockReportState(
    val filter: ReportFilter = ReportFilter(),
    val report: StockReport? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/** The Stock et pertes report and its restock list, held by the Rapports graph. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class StockReportViewModel @Inject constructor(
    private val repository: ReportRepository,
    private val filterStore: ReportFilterStore,
) : ViewModel() {

    // A sale, a perte, a stock movement reloads it — a burst of writes once.
    private val changes = repository.stockChanges().debounce(400).onStart { emit(Unit) }
    private var last = StockReportState()

    val state: StateFlow<StockReportState> = combine(filterStore.filter, changes) { filter, _ -> filter }
        .flatMapLatest { filter ->
            flow {
                emit(last.copy(filter = filter, loading = true, error = null))
                val next = runCatching { repository.stockReport(filter) }.fold(
                    onSuccess = { StockReportState(filter, it, loading = false) },
                    onFailure = { last.copy(filter = filter, loading = false, error = it.message ?: "Erreur de chargement") },
                )
                last = next
                emit(next)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StockReportState())

    /** The restock list's search. */
    val query = MutableStateFlow("")

    fun setFilter(filter: ReportFilter) = filterStore.update { filter }
}
