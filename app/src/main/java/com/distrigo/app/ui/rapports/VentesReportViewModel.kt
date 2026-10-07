package com.distrigo.app.ui.rapports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportFilterStore
import com.distrigo.app.data.repository.CommuneFilter
import com.distrigo.app.data.repository.DistributionReport
import com.distrigo.app.data.repository.ReportRepository
import com.distrigo.app.data.repository.SalesReport
import com.distrigo.app.data.repository.SectorStat
import com.distrigo.app.data.repository.UnservedClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A sector's bottom sheet: its figures, and its clients without a sale — null while they load. */
data class SectorSheet(val sector: SectorStat, val unserved: List<UnservedClient>? = null)

/**
 * The Ventes report as the screen shows it. [report] and [distribution] stay on screen while the next
 * ones load, so changing the period does not blank the page; [loading] says newer ones are on their
 * way. [commune] narrows the whole report to one commune's clients; [sheet] is the sector opened.
 */
data class VentesReportState(
    val filter: ReportFilter = ReportFilter(),
    val commune: CommuneFilter? = null,
    val report: SalesReport? = null,
    val buckets: List<SalesBucket> = emptyList(),
    val distribution: DistributionReport? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val sheet: SectorSheet? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class VentesReportViewModel @Inject constructor(
    private val repository: ReportRepository,
    private val filterStore: ReportFilterStore,
) : ViewModel() {

    // A reload when the report's tables are written — a sale, a payment, a client moved to another
    // sector — and only then: a burst of writes (one sale is several rows) settles into one reload.
    // The first load does not wait.
    private val changes = repository.salesChanges().debounce(400).onStart { emit(Unit) }
    // The Ventes report's own: the other reports share the period, not the commune.
    private val commune = MutableStateFlow<CommuneFilter?>(null)
    private val sheet = MutableStateFlow<SectorSheet?>(null)
    private var sheetJob: Job? = null
    private var last = VentesReportState()

    private val reports = combine(filterStore.filter, commune, changes) { filter, commune, _ -> filter to commune }
        .flatMapLatest { (filter, commune) ->
            flow {
                emit(last.copy(filter = filter, commune = commune, loading = true, error = null))
                val next = runCatching {
                    // The sales and the distribution read different rows: side by side.
                    coroutineScope {
                        val sales = async { repository.salesReport(filter, commune = commune) }
                        val distribution = async { repository.distribution(filter, commune) }
                        sales.await() to distribution.await()
                    }
                }.fold(
                    onSuccess = { (report, distribution) ->
                        VentesReportState(filter, commune, report, salesBuckets(report.days), distribution, loading = false)
                    },
                    onFailure = { last.copy(filter = filter, commune = commune, loading = false, error = it.message ?: "Erreur de chargement") },
                )
                last = next
                emit(next)
            }
        }

    // Kept running while the ViewModel lives — under a client opened from the report too — so coming
    // back finds the report loaded, instead of restarting it and querying again.
    val state: StateFlow<VentesReportState> = combine(reports, sheet) { report, sheet -> report.copy(sheet = sheet) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VentesReportState())

    fun setFilter(filter: ReportFilter) = filterStore.update { filter }

    /** Narrows the whole report to [filter]'s clients, or — null — takes the narrowing off. */
    fun setCommune(filter: CommuneFilter?) {
        closeSector()
        commune.value = filter
    }

    /** Opens [sector]'s sheet at once with its figures; its clients without a sale follow. */
    fun openSector(sector: SectorStat) {
        sheetJob?.cancel()
        sheet.value = SectorSheet(sector)
        val filter = state.value.filter
        sheetJob = viewModelScope.launch {
            val unserved = runCatching { repository.unservedClients(filter, sector.id) }.getOrDefault(emptyList())
            sheet.update { if (it?.sector?.id == sector.id) it.copy(unserved = unserved) else it }
        }
    }

    fun closeSector() {
        sheetJob?.cancel()
        sheet.value = null
    }
}
