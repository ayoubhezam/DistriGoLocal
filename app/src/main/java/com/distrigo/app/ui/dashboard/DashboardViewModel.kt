package com.distrigo.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportFilterStore
import com.distrigo.app.data.model.report.ReportPeriod
import com.distrigo.app.data.repository.DashboardAlerts
import com.distrigo.app.data.repository.DashboardBalances
import com.distrigo.app.data.repository.DashboardSales
import com.distrigo.app.data.repository.ReportRepository
import com.distrigo.app.ui.navigation.ReportEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Dashboard as the screen shows it: each block null until it has loaded once, then kept while it reloads. */
data class DashboardState(
    val sales: DashboardSales? = null,
    val alerts: DashboardAlerts? = null,
    val balances: DashboardBalances? = null,
)

/** Where a Dashboard card leads: a report, on the period the card showed. */
enum class DashboardLink(val report: ReportEntry, val period: ReportPeriod) {
    TODAY(ReportEntry.VENTES, ReportPeriod.AUJOURDHUI),
    MONTH(ReportEntry.VENTES, ReportPeriod.CE_MOIS),
    WEEK(ReportEntry.VENTES, ReportPeriod.SEPT_JOURS),
    RESTOCK(ReportEntry.REAPPRO, ReportPeriod.CE_MOIS),
    CLIENT_DEBTS(ReportEntry.CREANCES_CLIENTS, ReportPeriod.CE_MOIS),
    SUPPLIER_DEBTS(ReportEntry.DETTES_FOURNISSEURS, ReportPeriod.CE_MOIS),
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ReportRepository,
    private val filterStore: ReportFilterStore,
) : ViewModel() {

    /**
     * [read] once, then again each time [changes] says its tables were written — a burst of writes
     * once. A failed read keeps what was shown.
     */
    private fun <T : Any> block(changes: Flow<Unit>, read: suspend () -> T): Flow<T?> =
        changes.debounce(400).onStart { emit(Unit) }
            .mapLatest { runCatching { read() }.getOrNull() }
            .filterNotNull()
            .onStart<T?> { emit(null) }

    private var last = DashboardState()

    // The blocks load apart, the quick ones first; each reloads only when its own tables change.
    // Watched only while the Dashboard is: back on it, it reads again — showing what it had meanwhile,
    // so its figures count from what was shown to what is.
    val state: StateFlow<DashboardState> = combine(
        block(repository.salesChanges()) { repository.dashboardSales() },
        block(merge(repository.stockChanges(), repository.debtChanges())) { repository.dashboardAlerts() },
        block(repository.debtChanges()) { repository.dashboardBalances() },
    ) { sales, alerts, balances ->
        DashboardState(sales ?: last.sales, alerts ?: last.alerts, balances ?: last.balances).also { last = it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    /** Sets the period all reports share to the card's, so its report opens on what the card showed. */
    fun prepare(link: DashboardLink) = filterStore.update { ReportFilter(period = link.period) }
}
