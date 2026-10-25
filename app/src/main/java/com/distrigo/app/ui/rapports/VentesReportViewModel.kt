package com.distrigo.app.ui.rapports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportFilterStore
import com.distrigo.app.data.repository.PlaceLevel
import com.distrigo.app.data.repository.PlaceNode
import com.distrigo.app.data.repository.ReportRepository
import com.distrigo.app.data.repository.SalesReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
import javax.inject.Inject

/**
 * Where « Répartition des ventes » is: [path], the keys of the places opened from the wilayas down (empty: the
 * wilayas), and [picked], a quartier whose clients are listed, its ring still the secteur's quartiers.
 */
data class PlaceView(val path: List<String> = emptyList(), val picked: String? = null)

/**
 * The Ventes report as the screen shows it. [report] and [places] stay on screen while the next ones
 * load, so changing the period does not blank the page; [loading] says a newer report is on its way.
 * [placeView] is where « Répartition des ventes » is in [places].
 */
data class VentesReportState(
    val filter: ReportFilter = ReportFilter(),
    val report: SalesReport? = null,
    val buckets: List<SalesBucket> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val places: PlaceNode? = null,
    val placeView: PlaceView = PlaceView(),
) {
    /** The place whose ring is shown: the wilayas, or the last place opened. */
    val place: PlaceNode? get() = places?.let { root -> placeView.path.lastOrNull()?.let(root::find) ?: root }

    /** The places from the wilayas down to [place], for the breadcrumb. */
    val placeTrail: List<PlaceNode> get() = places?.let { root -> listOf(root) + placeView.path.mapNotNull(root::find) }.orEmpty()

    /** The quartier picked, whose clients are listed. */
    val pickedQuartier: PlaceNode? get() = placeView.picked?.let { places?.find(it) }
}

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
    // The first load does not wait. Shared: the sales and their places reload on the same signal.
    private val changes = repository.salesChanges().debounce(400).onStart { emit(Unit) }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)
    // Where « Répartition des ventes » is: the report's own, back to the wilayas when the period or the source changes.
    private val placeView = MutableStateFlow(PlaceView())

    // The sales: the period's — moving through the places does not query them again.
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

    // The sales by place: the period's. The tree on screen stays until the next is there; a failure keeps it. A new
    // period or source starts again from the wilayas; a reload of the same one keeps the places still there.
    private val places: Flow<PlaceNode?> = run {
        var last: PlaceNode? = null
        var lastFilter: ReportFilter? = null
        combine(filterStore.filter, changes) { filter, _ -> filter }
            .flatMapLatest { filter ->
                flow {
                    if (filter != lastFilter) { lastFilter = filter; placeView.value = PlaceView() }
                    emit(last)
                    runCatching { repository.salesPlaces(filter) }.onSuccess { tree ->
                        last = tree
                        placeView.update { view -> view.within(tree) }
                        emit(tree)
                    }
                }
            }
    }

    // Kept running while the ViewModel lives — under a client opened from the report too — so coming
    // back finds the report loaded, instead of restarting it and querying again.
    val state: StateFlow<VentesReportState> = combine(sales, places, placeView) { s, tree, view ->
        VentesReportState(s.filter, s.report, s.buckets, s.loading, s.error, tree, view)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VentesReportState())

    fun setFilter(filter: ReportFilter) = filterStore.update { filter }

    /** Opens [place]: a wilaya, a commune or a secteur shows its own ring; a quartier lists its clients. */
    fun openPlace(place: PlaceNode) = placeView.update { view ->
        when (place.level) {
            PlaceLevel.WILAYA, PlaceLevel.COMMUNE, PlaceLevel.SECTEUR -> PlaceView(view.path + place.key)
            PlaceLevel.QUARTIER -> view.copy(picked = place.key)
            else -> view
        }
    }

    /** One step up: the quartier picked put down, or the place above. */
    fun placeUp() = placeView.update { view ->
        if (view.picked != null) view.copy(picked = null) else PlaceView(view.path.dropLast(1))
    }

    /** Back to the breadcrumb's [depth]th place: 0 is the wilayas. */
    fun placeTo(depth: Int) = placeView.update { view -> PlaceView(view.path.take(depth)) }
}

/** This view, keeping only the places [tree] still has: after a reload, a place emptied of sales is gone. */
private fun PlaceView.within(tree: PlaceNode): PlaceView {
    val path = path.takeWhile { tree.find(it) != null }
    return PlaceView(path, picked?.takeIf { path.size == this.path.size && tree.find(it) != null })
}
