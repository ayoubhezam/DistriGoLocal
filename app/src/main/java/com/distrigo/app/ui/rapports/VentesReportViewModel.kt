package com.distrigo.app.ui.rapports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportFilterStore
import com.distrigo.app.data.repository.DistributionReport
import com.distrigo.app.data.repository.ReportRepository
import com.distrigo.app.data.repository.SalesReport
import com.distrigo.app.data.repository.SectorStat
import com.distrigo.app.data.repository.UnservedClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A sector's bottom sheet: its figures, and its clients without a sale — null while they load. */
data class SectorSheet(val sector: SectorStat, val unserved: List<UnservedClient>? = null)

/**
 * The Ventes report as the screen shows it. [report] and [distribution] stay on screen while the next
 * ones load, so changing the period does not blank the page; [loading] says a newer report is on its
 * way. [commune] narrows the distribution's figures and sectors — never the sales above them; [sheet]
 * is the sector opened.
 */
data class VentesReportState(
    val filter: ReportFilter = ReportFilter(),
    val report: SalesReport? = null,
    val buckets: List<SalesBucket> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val commune: String? = null,
    val distribution: DistributionReport? = null,
    val sheet: SectorSheet? = null,
)

/** The sales part of the state, loaded for a period. */
private data class SalesPart(
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

    // A reload when the report's tables are written — a sale, a payment, a client moved to another
    // sector — and only then: a burst of writes (one sale is several rows) settles into one reload.
    // The first load does not wait. Shared: the sales and the distribution reload on the same signal.
    private val changes = repository.salesChanges().debounce(400).onStart { emit(Unit) }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)
    // The Ventes report's own, and the distribution's alone: the other reports share the period, not
    // the commune, and the sales above the distribution are the whole business's.
    private val commune = MutableStateFlow<String?>(null)
    private val sheet = MutableStateFlow<SectorSheet?>(null)
    private var sheetJob: Job? = null

    // The sales: the period's, whatever the commune — choosing one does not query them again.
    private val sales: Flow<SalesPart> = run {
        var last = SalesPart()
        combine(filterStore.filter, changes) { filter, _ -> filter }
            .flatMapLatest { filter ->
                flow {
                    emit(last.copy(filter = filter, loading = true, error = null))
                    val next = runCatching { repository.salesReport(filter) }.fold(
                        onSuccess = { SalesPart(filter, it, salesBuckets(it.days), loading = false) },
                        onFailure = { last.copy(filter = filter, loading = false, error = it.message ?: "Erreur de chargement") },
                    )
                    last = next
                    emit(next)
                }
            }
    }

    // The distribution: the period's, in the commune chosen. The one on screen stays until the next
    // is there; a failure keeps it.
    private val distribution: Flow<Pair<String?, DistributionReport?>> = run {
        var last: DistributionReport? = null
        combine(filterStore.filter, commune, changes) { filter, commune, _ -> filter to commune }
            .flatMapLatest { (filter, commune) ->
                flow {
                    emit(commune to last)
                    runCatching { repository.distribution(filter, commune) }.onSuccess {
                        last = it
                        emit(commune to it)
                    }
                }
            }
    }

    // Kept running while the ViewModel lives — under a client opened from the report too — so coming
    // back finds the report loaded, instead of restarting it and querying again.
    val state: StateFlow<VentesReportState> = combine(sales, distribution, sheet) { s, (commune, d), sheet ->
        VentesReportState(s.filter, s.report, s.buckets, s.loading, s.error, commune, d, sheet)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VentesReportState())

    fun setFilter(filter: ReportFilter) = filterStore.update { filter }

    /** Narrows the distribution's figures and sectors to [name]'s clients, or — null — to none. */
    fun setCommune(name: String?) {
        closeSector()
        commune.value = name
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
