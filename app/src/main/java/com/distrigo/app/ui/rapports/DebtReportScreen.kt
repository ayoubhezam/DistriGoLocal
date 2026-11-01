package com.distrigo.app.ui.rapports

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.rememberLazyListState
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.ListSortChip
import com.distrigo.app.ui.common.SortOptionsSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.repository.AgeBand
import com.distrigo.app.data.repository.DebtReport
import com.distrigo.app.data.repository.DebtSide
import com.distrigo.app.data.repository.DebtorLine
import com.distrigo.app.ui.common.FitText
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.navigation.DrillTarget
import com.distrigo.app.ui.navigation.LocalDrillDown
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.border
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

/** Each age band's colour: green while recent, to red past 90 days. */
private val AgeColors = mapOf(
    AgeBand.RECENT to DsColors.Success,
    AgeBand.MONTH to DsColors.Caution,
    AgeBand.TWO_MONTHS to DsColors.Warning,
    AgeBand.OLD to DsColors.Danger,
)

private val DAY: DateTimeFormatter get() = com.distrigo.app.core.format.AppDates.numeric("dd/MM/yyyy")

/** How many debtors the report itself lists, the biggest first. */
private const val TOP_DEBTORS = 5

/** What each figure of the report is and how it is counted, for its ⓘ — in each side's words. */
private fun debtInfo(side: DebtSide, figure: String): ReportInfo {
    val clients = side == DebtSide.CLIENTS
    val (title, text) = when (figure) {
        "owed" -> if (clients) R.string.rapports_owed_clients to R.string.rapports_info_owed_clients
        else R.string.rapports_owed_suppliers to R.string.rapports_info_owed_suppliers
        "credit" -> if (clients) R.string.rapports_credit_clients to R.string.rapports_info_credit_clients
        else R.string.rapports_credit_suppliers to R.string.rapports_info_credit_suppliers
        "payments" -> if (clients) R.string.rapports_payments_clients to R.string.rapports_info_payments_clients
        else R.string.rapports_payments_suppliers to R.string.rapports_info_payments_suppliers
        "returns" -> if (clients) R.string.rapports_returns_clients to R.string.rapports_info_returns_clients
        else R.string.rapports_returns_suppliers to R.string.rapports_info_returns_suppliers
        "ages" -> if (clients) R.string.rapports_ages to R.string.rapports_info_ages_clients
        else R.string.rapports_ages to R.string.rapports_info_ages_suppliers
        else -> if (clients) R.string.rapports_balance_change to R.string.rapports_info_change_clients
        else R.string.rapports_balance_change to R.string.rapports_info_change_suppliers
    }
    return ReportInfo(reportText(title), reportText(text))
}

/** The words that change with the side, so the layout is written once. */
private class SideWords(
    val owed: String, val debtors: (Int) -> String, val credit: String,
    val payments: String, @androidx.annotation.PluralsRes val paymentUnit: Int, val returns: String, val listTitle: String,
    val noPayment: String, val lastPayment: String, val noDebt: String,
    val searchHint: String, val count: (Int) -> String,
)

private fun wordsFor(side: DebtSide) = when (side) {
    DebtSide.CLIENTS -> SideWords(
        owed = reportText(R.string.rapports_owed_clients),
        debtors = { plural(R.plurals.rapports_debtor_clients_n, it) },
        credit = reportText(R.string.rapports_credit_clients),
        payments = reportText(R.string.rapports_payments_clients),
        paymentUnit = R.plurals.rapports_payments_n,
        returns = reportText(R.string.rapports_returns_clients),
        listTitle = reportText(R.string.rapports_debtor_clients),
        noPayment = reportText(R.string.rapports_no_payment),
        lastPayment = reportText(R.string.rapports_last_payment_on),
        noDebt = reportText(R.string.rapports_no_debt_clients),
        searchHint = reportText(R.string.common_search_client),
        count = { plural(R.plurals.common_clients_count, it) },
    )
    DebtSide.FOURNISSEURS -> SideWords(
        owed = reportText(R.string.rapports_owed_suppliers),
        debtors = { plural(R.plurals.rapports_suppliers_to_pay_n, it) },
        credit = reportText(R.string.rapports_credit_suppliers),
        payments = reportText(R.string.rapports_payments_suppliers),
        paymentUnit = R.plurals.rapports_payments_n,
        returns = reportText(R.string.rapports_returns_suppliers),
        listTitle = reportText(R.string.rapports_suppliers_to_pay),
        noPayment = reportText(R.string.rapports_no_payment),
        lastPayment = reportText(R.string.rapports_last_payment_on),
        noDebt = reportText(R.string.rapports_no_debt_suppliers),
        searchHint = reportText(R.string.purchases_search_supplier),
        count = { plural(R.plurals.suppliers_count, it) },
    )
}

/**
 * Rapports › Créances et dettes: what clients owe and what is owed to suppliers. Today's balances —
 * their total, how old they are, who owes them — and what the period did to them: credit given,
 * payments, returns.
 */
@Composable
fun DebtReportScreen(onBack: () -> Unit, onSeeAll: () -> Unit, viewModel: DebtReportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val words = wordsFor(state.side)
    val open = openDebtor(state.side)

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = stringResource(R.string.rapports_debts), subtitle = stringResource(R.string.rapports_debts_sub), leading = DsTopBarLeading.Back(onBack))
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (state.loading && state.report != null) {
                LinearProgressIndicator(Modifier.fillMaxSize(), color = DsColors.Primary, trackColor = Color.Transparent)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = DsSpacing.sm, bottom = DsSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
        ) {
            item { ReportFilterBar(state.filter, viewModel::setFilter, showSource = false) }
            item {
                ReportSegmented(
                    DebtSide.entries, state.side, { stringResource(it.label) }, viewModel::setSide,
                    Modifier.padding(horizontal = DsSpacing.lg),
                )
            }

            val report = state.report
            when {
                report == null && state.error != null -> item { ReportMessage(state.error!!) }
                report == null -> item {
                    Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DsColors.Primary)
                    }
                }
                else -> {
                    item { OwedCard(report, words) }
                    item { SectionTitle(stringResource(R.string.rapports_in_period)) }
                    item { FlowTiles(report, words) }
                    if (report.debtors.isEmpty()) {
                        item { ReportMessage(words.noDebt) }
                    } else {
                        item { AgeCard(report) }
                        // The biggest few; the rest are a tap away, on a screen of their own.
                        item { SectionTitle("${words.listTitle} (${report.debtors.size})", stringResource(R.string.common_action_see_all), onSeeAll) }
                        items(report.debtors.take(TOP_DEBTORS), key = { it.id }) { DebtorRow(it, words) { open(it.id) } }
                    }
                }
            }
        }
    }
}

/** Today's balance: the total owed, by how many, and the newest and oldest part of it. */
@Composable
private fun OwedCard(report: DebtReport, words: SideWords) {
    val money = LocalMoneyFormatter.current
    ReportHeroCard(
        // The title stays one line beside its ⓘ; the day goes under the count, on a line of its own.
        title = words.owed,
        amount = money.figure(report.outstanding),
        caption = stringResource(R.string.rapports_count_on_day, words.debtors(report.debtors.size), report.today.format(DAY)),
        info = debtInfo(report.side, "owed"),
        halves = if (report.outstanding > 0) listOf(
            HeroHalf(stringResource(R.string.rapports_under_30_days), money.figure(report.ages[AgeBand.RECENT.ordinal])),
            HeroHalf(stringResource(R.string.rapports_over_90_days), money.figure(report.ages[AgeBand.OLD.ordinal]), bold = true),
        ) else emptyList(),
    )
}

/** The period's flows, and what they did to the balance. */
@Composable
private fun FlowTiles(report: DebtReport, words: SideWords) {
    val money = LocalMoneyFormatter.current
    val change = report.change
    Column(Modifier.padding(horizontal = DsSpacing.lg), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            // Dark figures; only the balance's trend is coloured, green or red: that is the news.
            KpiTile(words.credit, money.figure(report.credit), DsColors.PrimaryDark, Modifier.weight(1f), null, Icons.Default.Schedule,
                debtInfo(report.side, "credit"), accent = DsColors.Warning)
            KpiTile(
                words.payments, money.figure(report.payments.total), DsColors.PrimaryDark, Modifier.weight(1f),
                plural(words.paymentUnit, report.payments.count), Icons.Default.Payments, debtInfo(report.side, "payments"), accent = DsColors.Success
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile(
                words.returns, money.figure(report.returns.total), DsColors.PrimaryDark, Modifier.weight(1f),
                plural(R.plurals.rapports_returns_n, report.returns.count), Icons.AutoMirrored.Filled.AssignmentReturn, debtInfo(report.side, "returns"),
            )
            // More owed is bad news on either side: red when the balance grew, green when it shrank.
            val grew = change > 0.005
            val shrank = change < -0.005
            KpiTile(
                stringResource(R.string.rapports_balance_change),
                // Its sign follows the count: a balance that turns from growth to shrinkage crosses zero.
                Figure(change) { v -> if (v > 0.005) "+ ${money.da(v)}" else if (v < -0.005) "− ${money.da(-v)}" else money.da(0.0) },
                when { grew -> DsColors.Danger; shrank -> DsColors.Success; else -> DsColors.PrimaryDark },
                Modifier.weight(1f),
                stringResource(when { grew -> R.string.rapports_trend_up; shrank -> R.string.rapports_trend_down; else -> R.string.products_price_stable }),
                when { grew -> Icons.AutoMirrored.Filled.TrendingUp; shrank -> Icons.AutoMirrored.Filled.TrendingDown; else -> Icons.AutoMirrored.Filled.TrendingFlat },
                debtInfo(report.side, "change"),
            )
        }
    }
}

/**
 * How old today's balance is: a ring of the four bands with the total inside, and beside it each
 * band's share and amount.
 */
@Composable
private fun AgeCard(report: DebtReport) {
    val money = LocalMoneyFormatter.current
    val total = report.outstanding
    Column(
        Modifier
            .padding(horizontal = DsSpacing.lg)
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .padding(DsSpacing.lg)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rapports_ages), fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark)
            InfoButton(debtInfo(report.side, "ages"), tint = DsColors.TextTertiary)
        }
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.rapports_ages_sub), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        Spacer(Modifier.height(DsSpacing.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ShareRing(
                slices = AgeBand.entries.map { report.ages[it.ordinal] to AgeColors.getValue(it) },
                total = total,
                caption = stringResource(if (report.side == DebtSide.CLIENTS) R.string.rapports_total_owed_to_us else R.string.rapports_total_to_pay),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(DsSpacing.lg))
            Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                AgeBand.entries.forEach { band ->
                    val amount = report.ages[band.ordinal]
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 4.dp).size(12.dp).clip(DsShapes.pill).background(AgeColors.getValue(band)))
                        Spacer(Modifier.width(DsSpacing.sm))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // One line: "Plus de 90 jours" shrinks a little rather than wrap under its share.
                                FitText(stringResource(band.label), fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary, modifier = Modifier.weight(1f), minScale = 0.8f)
                                Spacer(Modifier.width(DsSpacing.xs))
                                Text(
                                    percentOf(amount, total) ?: percent(0.0),
                                    fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark,
                                )
                            }
                            FitText(money.da(amount), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

/** Opens a debtor's own page — the client's, or the supplier's — through the app's drill-down. */
@Composable
private fun openDebtor(side: DebtSide): (Int) -> Unit {
    val drill = LocalDrillDown.current
    return { id -> drill(if (side == DebtSide.CLIENTS) DrillTarget.Client(id) else DrillTarget.Supplier(id)) }
}

/**
 * One party that owes or is owed: its name and last payment, its balance and how old its oldest debt
 * is. A tap opens its page, where a payment is recorded; the report updates when it is.
 */
@Composable
private fun DebtorRow(line: DebtorLine, words: SideWords, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val color = AgeColors.getValue(line.oldest)
    Row(
        Modifier
            .padding(horizontal = DsSpacing.lg)
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                line.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                line.lastPayment?.let { "${words.lastPayment} ${it.format(DAY)}" } ?: words.noPayment,
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
            )
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            FitText(money.da(line.balance), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(line.oldest.label), fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = color,
                modifier = Modifier.clip(DsShapes.pill).background(color.copy(alpha = 0.12f)).padding(horizontal = DsSpacing.sm, vertical = 2.dp),
            )
        }
    }
}

/**
 * Rapports › Créances et dettes › Voir tout: every client who owes (or supplier to pay), searchable by
 * name and sorted — the biggest debt first unless chosen otherwise. It reads the report already loaded
 * — [viewModel] is the report screen's — so it opens at once and shows the same side and the same day.
 *
 * The search bar, the count and "Trier" are Produits' own, so a list is searched and sorted the same
 * way everywhere.
 */
@Composable
fun DebtorsScreen(onBack: () -> Unit, viewModel: DebtReportViewModel) {
    val state by viewModel.state.collectAsState()
    // Searched and sorted off the main thread once typing pauses (DebtReportViewModel.debtorList); what
    // is being typed is read by the search box alone, so a key redraws the box and nothing else.
    val list by viewModel.debtorList.collectAsState()
    val sort by viewModel.debtorSort.collectAsState()
    val words = wordsFor(state.side)
    val report = state.report
    val open = openDebtor(state.side)
    var sortSheet by remember { mutableStateOf(false) }

    // A new search or a new order starts at the top of what it found — once the list for it is here.
    val listState = rememberLazyListState()
    LaunchedEffect(list?.query, list?.sort) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = words.listTitle, leading = DsTopBarLeading.Back(onBack))
        when {
            report == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.Primary)
            }
            report.debtors.isEmpty() -> ReportMessage(words.noDebt)
            else -> {
                // On white, as in Produits: the sunken search pill reads against white, not against the
                // list's grey. The grey starts under it, behind the white rows.
                Column(Modifier.fillMaxWidth().background(DsColors.SurfaceMuted).padding(bottom = DsSpacing.sm)) {
                    DebtorSearchField(viewModel, words.searchHint, Modifier.padding(horizontal = DsSpacing.lg))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        list?.let { l ->
                            Text(
                                if (l.query.isBlank()) words.count(l.lines.size) else stringResource(R.string.common_shown_of_total, l.lines.size, l.total),
                                fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
                            )
                        } ?: Spacer(Modifier.width(1.dp))
                        ListSortChip(active = sort != DebtorSort.DETTE_DESC, onClick = { sortSheet = true })
                    }
                }
                val shown = list
                if (shown == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DsColors.Primary)
                    }
                } else if (shown.lines.isEmpty()) {
                    ReportMessage(stringResource(R.string.rapports_no_result_for, shown.query.trim()))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = DsSpacing.sm, bottom = DsSpacing.xxxl),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                    ) {
                        items(shown.lines, key = { it.id }) { DebtorRow(it, words) { open(it.id) } }
                    }
                }
            }
        }
    }

    if (sortSheet) {
        SortOptionsSheet(
            options   = DebtorSort.entries,
            selected  = sort,
            label     = { stringResource(it.label) },
            onSelect  = viewModel::setDebtorSort,
            onDismiss = { sortSheet = false },
        )
    }
}

/** The debtors' search box: the one place that reads each key typed, so a key redraws it alone. */
@Composable
private fun DebtorSearchField(viewModel: DebtReportViewModel, hint: String, modifier: Modifier) {
    val query by viewModel.debtorQuery.collectAsState()
    DsCompactSearchField(
        value         = query,
        onValueChange = viewModel::setDebtorQuery,
        placeholder   = hint,
        modifier      = modifier
    )
}
