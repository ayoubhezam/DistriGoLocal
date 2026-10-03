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
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** The Créances et dettes report as the screen shows it; [report] stays while the next one loads. */
data class DebtReportState(
    val filter: ReportFilter = ReportFilter(),
    val side: DebtSide = DebtSide.CLIENTS,
    val report: DebtReport? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DebtReportViewModel @Inject constructor(
    private val repository: ReportRepository,
    private val filterStore: ReportFilterStore,
) : ViewModel() {

    // The side is this screen's own choice; the period is the one all reports share.
    private val side = MutableStateFlow(DebtSide.CLIENTS)
    private val refreshes = MutableStateFlow(0)
    private var last = DebtReportState()

    val state: StateFlow<DebtReportState> = combine(filterStore.filter, side, refreshes) { filter, side, _ -> filter to side }
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtReportState())

    fun refresh() = refreshes.update { it + 1 }

    fun setFilter(filter: ReportFilter) = filterStore.update { filter }

    fun setSide(value: DebtSide) { side.value = value }
}
