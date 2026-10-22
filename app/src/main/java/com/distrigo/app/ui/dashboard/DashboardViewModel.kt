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
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.distrigo.app.data.model.TourneeClientInfo

/**
 * One block of the Dashboard: [Loading] until it has been read once, then [Ready], kept while it reloads. A block
 * with nothing to show is Ready with null (no policy, no tournée open). [Failed] when its first read failed: it
 * counts as read, so the Dashboard never waits on it forever.
 */
sealed interface Block<out T> {
    data object Loading : Block<Nothing>
    data object Failed : Block<Nothing>
    data class Ready<T>(val value: T) : Block<T>
}

private fun <T> Block<T>.valueOrNull(): T? = (this as? Block.Ready)?.value

/** The Dashboard's figures, every block read once (docs/design/dashboard_objectif_plan.md, Part 1). */
data class DashboardContent(
    /** The account's first name for « Bonjour, … » (D16); null for an account without one. */
    val firstName: String?,
    /** Null only if they could not be read: the card then keeps its placeholder. */
    val sales: DashboardSales?,
    /** The active commission policy's progress; null without a policy (the card then shows « Ce mois », D3). */
    val goal: GoalProgress?,
    val alerts: DashboardAlerts?,
    val tournee: TourneeDay?,
    val activity: List<RecentSale>,
    val activityFilter: ActivityFilter,
)

/**
 * The Dashboard all at once (owner, 2026-10-22): [Loading] — the skeleton — until every block has been read, then
 * [Ready]. Loaded apart, each block arriving pushed the ones below it down the screen.
 */
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Ready(val content: DashboardContent) : DashboardUiState

    companion object {
        /**
         * [Ready] once no block is [Block.Loading]. A block never goes back to Loading (a reload keeps what was
         * read), so once Ready the Dashboard stays Ready and only its figures change.
         */
        fun of(
            name: Block<String?>,
            sales: Block<DashboardSales>,
            goal: Block<GoalProgress?>,
            alerts: Block<DashboardAlerts>,
            tournee: Block<TourneeDay?>,
            activity: Block<List<RecentSale>>,
            filter: ActivityFilter,
        ): DashboardUiState {
            if (listOf(name, sales, goal, alerts, tournee, activity).any { it == Block.Loading }) return Loading
            return Ready(DashboardContent(
                firstName      = name.valueOrNull(),
                sales          = sales.valueOrNull(),
                goal           = goal.valueOrNull(),
                alerts         = alerts.valueOrNull(),
                tournee        = tournee.valueOrNull(),
                activity       = activity.valueOrNull().orEmpty(),
                activityFilter = filter,
            ))
        }
    }
}

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
    @ApplicationContext context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Whether a tournée was open when the Dashboard last loaded: its skeleton then keeps the chip's place, so the
     * swap only moves the screen the day the tournée opens or closes (owner, 2026-10-22).
     */
    val tourneeWasOpen: Boolean = prefs.getBoolean(KEY_TOURNEE_OPEN, false)

    /**
     * [read] once, then again each time [changes] says its tables were written — a burst of writes once. A failed
     * read keeps what was shown, or is [Block.Failed] if nothing was.
     */
    private fun <T> block(changes: Flow<Unit>, from: Block<T> = Block.Loading, read: suspend () -> T): Flow<Block<T>> {
        var last: Block<T> = from
        return changes.debounce(400).onStart { emit(Unit) }
            .mapLatest { runCatching { read() }.map { Block.Ready(it) }.getOrNull() ?: last.takeUnless { it == Block.Loading } ?: Block.Failed }
            .onStart { emit(last) }
            .map { it.also { last = it } }
    }

    private val activityFilter = MutableStateFlow(ActivityFilter.ALL)

    /** Loading until the stored session has been read: « Bonjour » then « Bonjour, Ayoub » was the first jump. */
    private val firstName: Flow<Block<String?>> = auth.state.map { account ->
        if (account == AccountState.Loading) Block.Loading
        else Block.Ready((account as? AccountState.SignedIn)?.session?.takeUnless { it.isAnonymous }
            ?.displayName?.trim()?.split(Regex("\\s+"))?.firstOrNull { it.isNotEmpty() }
            ?.replaceFirstChar { it.uppercase() })
    }

    // A new filter keeps the sales shown until its own are read: emptied in between, the list would shrink and the
    // page jump up under the finger
    private var shownActivity: Block<List<RecentSale>> = Block.Loading
    private val activity: Flow<Block<List<RecentSale>>> = activityFilter.flatMapLatest { filter ->
        block(reports.salesChanges(), from = shownActivity) { reports.recentSales(filter, limit = RECENT_SALES) }
    }.onEach { shownActivity = it }

    // The blocks are read together and shown together; each reloads only when its own tables change.
    val state: StateFlow<DashboardUiState> = combine(
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
        DashboardUiState.of(name, sales, goal, alerts, tournee, recent, filter)
    // Stopped as soon as the Dashboard is left, so every return reads it again, the last figures shown meanwhile: a
    // return within a few seconds used to rely on the tables' signal alone (owner, 2026-10-20)
    }.onEach { ui ->
        val open = (ui as? DashboardUiState.Ready)?.content?.tournee != null
        if (ui is DashboardUiState.Ready && prefs.getBoolean(KEY_TOURNEE_OPEN, false) != open) {
            prefs.edit().putBoolean(KEY_TOURNEE_OPEN, open).apply()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), DashboardUiState.Loading)

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

    // ── « Clients du jour »: the tournée's own action sheet ──

    /** [clientId] as the tournée's sheet reads it; null once it has left the tournée. */
    suspend fun tourneeClient(tourneeId: Int, clientId: Int): TourneeClientInfo? =
        runCatching { products.getTourneeClientsWithDetails(tourneeId).find { it.client.id == clientId } }.getOrNull()

    /** « Marquer sans vente »: visited, nothing sold. */
    fun markVisited(tourneeId: Int, clientId: Int, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                products.markTourneeClientVisited(tourneeId, clientId)
            } catch (e: Exception) {
                onError(userMessage(e))
            }
        }
    }

    /** « Supprimer de la tournée »: off the list; the client's sales stay. */
    fun removeFromTournee(tourneeId: Int, clientId: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                products.removeClientFromTournee(tourneeId, clientId)
                onSuccess()
            } catch (e: Exception) {
                onError(userMessage(e))
            }
        }
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

private const val PREFS = "dashboard"
private const val KEY_TOURNEE_OPEN = "tournee_open"
