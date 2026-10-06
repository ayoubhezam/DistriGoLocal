package com.distrigo.app.ui.rapports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportFilterStore
import com.distrigo.app.data.repository.DebtSide
import com.distrigo.app.data.repository.PartyRanking
import com.distrigo.app.data.repository.PartyReport
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

/** The Clients et fournisseurs report as the screen shows it; [report] stays on screen while the next one loads. */
data class PartyReportState(
    val side: DebtSide = DebtSide.CLIENTS,
    val filter: ReportFilter = ReportFilter(),
    val report: PartyReport? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/** The Clients et fournisseurs report and its lists, held by the Rapports graph. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class PartyReportViewModel @Inject constructor(
    private val repository: ReportRepository,
    private val filterStore: ReportFilterStore,
) : ViewModel() {

    private val side = MutableStateFlow(DebtSide.CLIENTS)
    private val changes = repository.partyChanges().debounce(400).onStart { emit(Unit) }
    private var last = PartyReportState()

    val state: StateFlow<PartyReportState> = combine(side, filterStore.filter, changes) { s, f, _ -> s to f }
        .flatMapLatest { (s, f) ->
            flow {
                // Another side is another report: its figures are not kept on screen while it loads.
                emit(if (last.side == s) last.copy(filter = f, loading = true, error = null) else PartyReportState(s, f))
                val next = runCatching { repository.partyReport(s, f) }.fold(
                    onSuccess = { PartyReportState(s, f, it, loading = false) },
                    onFailure = { PartyReportState(s, f, null, loading = false, error = it.message ?: "Erreur de chargement") },
                )
                last = next
                emit(next)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PartyReportState())

    val ranking = MutableStateFlow(PartyRanking.MONTANT)
    /** The full lists' search. */
    val query = MutableStateFlow("")

    fun setSide(s: DebtSide) {
        side.value = s
        if (s == DebtSide.FOURNISSEURS && ranking.value == PartyRanking.MARGE) ranking.value = PartyRanking.MONTANT
    }

    fun setFilter(filter: ReportFilter) = filterStore.update { filter }
}
