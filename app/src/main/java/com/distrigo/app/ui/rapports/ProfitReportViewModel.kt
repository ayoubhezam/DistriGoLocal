package com.distrigo.app.ui.rapports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportFilterStore
import com.distrigo.app.data.repository.ProfitReport
import com.distrigo.app.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Résultat report as the screen shows it; [report] stays on screen while the next one loads. */
data class ProfitReportState(
    val filter: ReportFilter = ReportFilter(),
    val report: ProfitReport? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ProfitReportViewModel @Inject constructor(
    private val repository: ReportRepository,
    private val filterStore: ReportFilterStore,
) : ViewModel() {

    // A sale, a return, a charge or a perte reloads it — a burst of writes once.
    private val changes = repository.profitChanges().debounce(400).onStart { emit(Unit) }
    private var last = ProfitReportState()

    val state: StateFlow<ProfitReportState> = combine(filterStore.filter, changes) { filter, _ -> filter }
        .flatMapLatest { filter ->
            flow {
                emit(last.copy(filter = filter, loading = true, error = null))
                val next = runCatching { repository.profitReport(filter) }.fold(
                    onSuccess = { ProfitReportState(filter, it, loading = false) },
                    onFailure = { last.copy(filter = filter, loading = false, error = it.message ?: "Erreur de chargement") },
                )
                last = next
                emit(next)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProfitReportState())

    fun setFilter(filter: ReportFilter) = filterStore.update { filter }
}
