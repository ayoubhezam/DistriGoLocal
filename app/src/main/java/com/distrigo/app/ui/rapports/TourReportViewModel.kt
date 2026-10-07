package com.distrigo.app.ui.rapports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportFilterStore
import com.distrigo.app.data.repository.TourReport
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

/** The Tournées report as the screen shows it; [report] stays on screen while the next one loads. */
data class TourReportState(
    val filter: ReportFilter = ReportFilter(),
    val report: TourReport? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TourReportViewModel @Inject constructor(
    private val repository: ReportRepository,
    private val filterStore: ReportFilterStore,
) : ViewModel() {

    // A tournée, a visit or a sale reloads it — a burst of writes once.
    private val changes = repository.tourChanges().debounce(400).onStart { emit(Unit) }
    private var last = TourReportState()

    val state: StateFlow<TourReportState> = combine(filterStore.filter, changes) { filter, _ -> filter }
        .flatMapLatest { filter ->
            flow {
                emit(last.copy(filter = filter, loading = true, error = null))
                val next = runCatching { repository.tourReport(filter) }.fold(
                    onSuccess = { TourReportState(filter, it, loading = false) },
                    onFailure = { last.copy(filter = filter, loading = false, error = it.message ?: "Erreur de chargement") },
                )
                last = next
                emit(next)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TourReportState())

    fun setFilter(filter: ReportFilter) = filterStore.update { filter }
}
