package com.distrigo.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.auth.AccountState
import com.distrigo.app.data.auth.AuthRepository
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.incentive.GoalProgress
import com.distrigo.app.data.model.locationLabel
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportFilterStore
import com.distrigo.app.data.model.report.ReportPeriod
import com.distrigo.app.data.repository.ActivityFilter
import com.distrigo.app.data.repository.DashboardAlerts
import com.distrigo.app.data.repository.DashboardSales
import com.distrigo.app.data.repository.IncentiveRepository
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.RecentSale
import com.distrigo.app.data.repository.ReportRepository
import com.distrigo.app.data.repository.TourneeDay
import com.distrigo.app.ui.common.userMessage
import com.distrigo.app.ui.navigation.ReportEntry
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One block of the Dashboard: [Loading] until it has been read once, then [Ready], kept while it reloads. A block
 * with nothing to show is Ready with null (no policy, no tournée open).
 */
sealed interface Block<out T> {
    data object Loading : Block<Nothing>
    data class Ready<T>(val value: T) : Block<T>
}

/** The Dashboard as the screen shows it (docs/design/dashboard_objectif_plan.md, Part 1). */
data class DashboardState(
    /** The account's first name for « Bonjour, … » (D16); null for an account without one. */
    val firstName: String? = null,
    val sales: Block<DashboardSales> = Block.Loading,
    /** The active commission policy's progress; null without a policy (the card then shows « Ce mois », D3). */
    val goal: Block<GoalProgress?> = Block.Loading,
    val alerts: Block<DashboardAlerts> = Block.Loading,
    val tournee: Block<TourneeDay?> = Block.Loading,
    val activity: Block<List<RecentSale>> = Block.Loading,
    val activityFilter: ActivityFilter = ActivityFilter.ALL,
)

/** Where a Dashboard card leads: a report, on the period the card showed. */
enum class DashboardLink(val report: ReportEntry, val period: ReportPeriod) {
    TODAY(ReportEntry.VENTES, ReportPeriod.AUJOURDHUI),
    MONTH(ReportEntry.VENTES, ReportPeriod.CE_MOIS),
    RESTOCK(ReportEntry.REAPPRO, ReportPeriod.CE_MOIS),
    CLIENT_DEBTS(ReportEntry.CREANCES_CLIENTS, ReportPeriod.CE_MOIS),
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val reports: ReportRepository,
    private val incentives: IncentiveRepository,
    private val products: ProductRepository,
    private val filterStore: ReportFilterStore,
    auth: AuthRepository,
) : ViewModel() {

    /**
     * [read] once, then again each time [changes] says its tables were written — a burst of writes once. A failed
     * read keeps what was shown.
     */
    private fun <T> block(changes: Flow<Unit>, from: Block<T> = Block.Loading, read: suspend () -> T): Flow<Block<T>> {
        var last: Block<T> = from
        return changes.debounce(400).onStart { emit(Unit) }
            .mapLatest { runCatching { read() }.map { Block.Ready(it) }.getOrNull() ?: last }
            .onStart { emit(last) }
            .map { it.also { last = it } }
    }

    private val activityFilter = MutableStateFlow(ActivityFilter.ALL)

    private val firstName: Flow<String?> = auth.state.map { account ->
        (account as? AccountState.SignedIn)?.session?.takeUnless { it.isAnonymous }
            ?.displayName?.trim()?.split(Regex("\\s+"))?.firstOrNull { it.isNotEmpty() }
            ?.replaceFirstChar { it.uppercase() }
    }

    // A new filter keeps the sales shown until its own are read: emptied in between, the list would shrink and the
    // page jump up under the finger
    private var shownActivity: Block<List<RecentSale>> = Block.Loading
    private val activity: Flow<Block<List<RecentSale>>> = activityFilter.flatMapLatest { filter ->
        block(reports.salesChanges(), from = shownActivity) { reports.recentSales(filter, limit = 4) }
    }.onEach { shownActivity = it }

    // The blocks load apart, the quick ones first; each reloads only when its own tables change.
    val state: StateFlow<DashboardState> = combine(
        combine(
            firstName,
            block(reports.salesChanges()) { reports.dashboardSales() },
            block(reports.goalChanges()) { incentives.progress() },
        ) { name, sales, goal -> Triple(name, sales, goal) },
        block(merge(reports.stockChanges(), reports.debtChanges())) { reports.dashboardAlerts() },
        block(reports.tourneeChanges()) { tourneeDay() },
        activity,
        activityFilter,
    ) { (name, sales, goal), alerts, tournee, recent, filter ->
        DashboardState(name, sales, goal, alerts, tournee, recent, filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    fun showActivity(filter: ActivityFilter) {
        activityFilter.value = filter
    }

    /** Sets the period all reports share to the card's, so its report opens on what the card showed. */
    fun prepare(link: DashboardLink) = filterStore.update { ReportFilter(period = link.period) }

    /** The open tournée with its clients' visits; null when none is open. */
    private suspend fun tourneeDay(): TourneeDay? {
        val tournee = products.getOpenTournee() ?: return null
        val infos = products.getTourneeClientsWithDetails(tournee.id)
        return TourneeDay.of(
            id = tournee.id,
            label = tournee.locationLabel(),
            clients = infos.map { Triple(it.client.id, it.client.name, it.client.image_uri) },
            statuses = infos.associate { it.client.id to it.status },
            clientsWithSale = products.clientIdsWithSaleOnTournee(tournee.id),
        )
    }

    // ── « Encaissement » (D15) ──

    /** The clients who owe something, the largest balance first: « Encaissement »'s picker. */
    suspend fun clientsOwing(): List<Client> =
        products.getClients().filter { com.distrigo.app.data.model.Amount.owing(it.balance) }.sortedByDescending { it.balance }

    fun addPayment(clientId: Int, amount: Double, note: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                products.addClientPayment(clientId, amount, note)
                onSuccess()
            } catch (e: Exception) {
                onError(userMessage(e))
            }
        }
    }
}
