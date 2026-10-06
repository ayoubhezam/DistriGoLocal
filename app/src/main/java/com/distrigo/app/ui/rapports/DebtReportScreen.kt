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

/** Each age band's colour: green while recent, to red past 90 days. */
private val AgeColors = mapOf(
    AgeBand.RECENT to Color(0xFF12B76A),
    AgeBand.MONTH to Color(0xFFFDB022),
    AgeBand.TWO_MONTHS to Color(0xFFF79009),
    AgeBand.OLD to Color(0xFFF04438),
)

private val DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** How many debtors the report itself lists, the biggest first. */
private const val TOP_DEBTORS = 5

/** What each figure of the report is and how it is counted, for its ⓘ — in each side's words. */
private fun debtInfo(side: DebtSide, figure: String): ReportInfo {
    val clients = side == DebtSide.CLIENTS
    return when (figure) {
        "owed" -> if (clients) ReportInfo("Reste à encaisser",
            "Ce que vos clients vous doivent aujourd'hui : la somme de leurs soldes, quelle que soit la période choisie.\n\n" +
                "« Moins de 30 jours » et « Plus de 90 jours » : l'âge de cette dette, d'après la date des ventes restées impayées, les plus récentes réglées en premier.")
        else ReportInfo("Reste à payer",
            "Ce que vous devez aujourd'hui à vos fournisseurs : la somme de leurs soldes, quelle que soit la période choisie.\n\n" +
                "« Moins de 30 jours » et « Plus de 90 jours » : l'âge de cette dette, d'après la date des bons restés impayés, les plus récents réglés en premier.")
        "credit" -> if (clients) ReportInfo("Crédit accordé",
            "La part des ventes de la période qui n'a pas été payée au moment de la vente : total − payé à la vente. Cela augmente ce que les clients vous doivent.")
        else ReportInfo("Achats à crédit",
            "La part des bons d'achat de la période qui n'a pas été payée à leur réception : total − payé. Cela augmente ce que vous devez aux fournisseurs.")
        "payments" -> if (clients) ReportInfo("Versements reçus",
            "Les paiements que vos clients vous ont faits sur la période, après la vente, et leur nombre. Ils diminuent ce qu'ils vous doivent.")
        else ReportInfo("Versements payés",
            "Les paiements que vous avez faits à vos fournisseurs sur la période, et leur nombre. Ils diminuent ce que vous leur devez.")
        "returns" -> if (clients) ReportInfo("Retours clients",
            "La marchandise rendue par vos clients sur la période, au prix de vente, et le nombre de retours. Ils sont déduits de ce que les clients vous doivent.")
        else ReportInfo("Retours fournisseurs",
            "La marchandise que vous avez rendue à vos fournisseurs sur la période, au prix d'achat, et le nombre de retours. Ils sont déduits de ce que vous leur devez.")
        "ages" -> if (clients) ReportInfo("Ancienneté",
            "Ce que vos clients vous doivent aujourd'hui, réparti selon l'âge des ventes restées impayées : 0 – 30, 31 – 60, 61 – 90 et plus de 90 jours.\n\n" +
                "Chaque versement est compté sur les ventes les plus récentes d'abord : ce qui reste dû est donc le plus ancien. " +
                "Au centre, le total ; à côté, la part et le montant de chaque tranche. Plus la part de plus de 90 jours est grande, plus l'argent est difficile à récupérer.")
        else ReportInfo("Ancienneté",
            "Ce que vous devez aujourd'hui à vos fournisseurs, réparti selon l'âge des bons restés impayés : 0 – 30, 31 – 60, 61 – 90 et plus de 90 jours.\n\n" +
                "Chaque versement est compté sur les bons les plus récents d'abord : ce qui reste dû est donc le plus ancien. " +
                "Au centre, le total ; à côté, la part et le montant de chaque tranche.")
        else -> if (clients) ReportInfo("Évolution du solde",
            "Crédit accordé − versements reçus − retours clients, sur la période.\n\n" +
                "En moins (vert, « En baisse ») : vos clients vous doivent moins qu'au début de la période. En plus (rouge, « En hausse ») : ils vous doivent davantage.")
        else ReportInfo("Évolution du solde",
            "Achats à crédit − versements payés − retours fournisseurs, sur la période.\n\n" +
                "En moins (vert, « En baisse ») : vous devez moins à vos fournisseurs qu'au début de la période. En plus (rouge, « En hausse ») : vous leur devez davantage.")
    }
}

/** The words that change with the side, so the layout is written once. */
private class SideWords(
    val owed: String, val debtors: (Int) -> String, val credit: String,
    val payments: String, val paymentUnit: Pair<String, String>, val returns: String, val listTitle: String,
    val noPayment: String, val lastPayment: String, val noDebt: String,
    val searchHint: String, val count: (Int) -> String,
)

private fun wordsFor(side: DebtSide) = when (side) {
    DebtSide.CLIENTS -> SideWords(
        owed = "Reste à encaisser",
        debtors = { plural(it, "client débiteur", "clients débiteurs") },
        credit = "Crédit accordé",
        payments = "Versements reçus",
        paymentUnit = "versement" to "versements",
        returns = "Retours clients",
        listTitle = "Clients débiteurs",
        noPayment = "Aucun versement",
        lastPayment = "Dernier versement le",
        noDebt = "Aucun client ne vous doit d'argent.",
        searchHint = "Rechercher un client",
        count = { "$it client(s)" },
    )
    DebtSide.FOURNISSEURS -> SideWords(
        owed = "Reste à payer",
        debtors = { plural(it, "fournisseur à payer", "fournisseurs à payer") },
        credit = "Achats à crédit",
        payments = "Versements payés",
        paymentUnit = "versement" to "versements",
        returns = "Retours fournisseurs",
        listTitle = "Fournisseurs à payer",
        noPayment = "Aucun versement",
        lastPayment = "Dernier versement le",
        noDebt = "Vous ne devez rien à vos fournisseurs.",
        searchHint = "Rechercher un fournisseur",
        count = { "$it fournisseur(s)" },
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
        DsTopAppBar(title = "Créances et dettes", subtitle = "Ce qui reste à encaisser et à payer", leading = DsTopBarLeading.Back(onBack))
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
                    DebtSide.entries, state.side, { it.label }, viewModel::setSide,
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
                    item { SectionTitle("Sur la période") }
                    item { FlowTiles(report, words) }
                    if (report.debtors.isEmpty()) {
                        item { ReportMessage(words.noDebt) }
                    } else {
                        item { AgeCard(report) }
                        // The biggest few; the rest are a tap away, on a screen of their own.
                        item { SectionTitle("${words.listTitle} (${report.debtors.size})", "Voir tout", onSeeAll) }
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
        amount = money.da(report.outstanding),
        caption = "${words.debtors(report.debtors.size)}\nau ${report.today.format(DAY)}",
        icon = Icons.Default.AccountBalance,
        info = debtInfo(report.side, "owed"),
        halves = if (report.outstanding > 0) listOf(
            HeroHalf("Moins de 30 jours", money.da(report.ages[AgeBand.RECENT.ordinal])),
            HeroHalf("Plus de 90 jours", money.da(report.ages[AgeBand.OLD.ordinal]), bold = true),
        ) else emptyList(),
    )
}

/** The period's flows, and what they did to the balance. */
@Composable
private fun FlowTiles(report: DebtReport, words: SideWords) {
    val money = LocalMoneyFormatter.current
    val (one, many) = words.paymentUnit
    val change = report.change
    Column(Modifier.padding(horizontal = DsSpacing.lg), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            // Dark figures; only the balance's trend is coloured, green or red: that is the news.
            KpiTile(words.credit, money.da(report.credit), DsColors.TextPrimary, Modifier.weight(1f), null, Icons.Default.Schedule,
                debtInfo(report.side, "credit"), accent = DsColors.Warning)
            KpiTile(
                words.payments, money.da(report.payments.total), DsColors.TextPrimary, Modifier.weight(1f),
                plural(report.payments.count, one, many), Icons.Default.Payments, debtInfo(report.side, "payments"), accent = DsColors.Success
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile(
                words.returns, money.da(report.returns.total), DsColors.TextPrimary, Modifier.weight(1f),
                plural(report.returns.count, "retour", "retours"), Icons.AutoMirrored.Filled.AssignmentReturn, debtInfo(report.side, "returns"),
            )
            // More owed is bad news on either side: red when the balance grew, green when it shrank.
            val grew = change > 0.005
            val shrank = change < -0.005
            KpiTile(
                "Évolution du solde",
                if (grew) "+ ${money.da(change)}" else if (shrank) "− ${money.da(-change)}" else money.da(0.0),
                when { grew -> DsColors.Danger; shrank -> DsColors.Success; else -> DsColors.TextPrimary },
                Modifier.weight(1f),
                when { grew -> "En hausse"; shrank -> "En baisse"; else -> "Stable" },
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
            .padding(DsSpacing.lg)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Ancienneté", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            InfoButton(debtInfo(report.side, "ages"), tint = DsColors.TextTertiary)
        }
        Spacer(Modifier.height(2.dp))
        Text("Depuis combien de temps l'argent est dû", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        Spacer(Modifier.height(DsSpacing.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ShareRing(
                slices = AgeBand.entries.map { report.ages[it.ordinal] to AgeColors.getValue(it) },
                total = total,
                caption = if (report.side == DebtSide.CLIENTS) "Total des créances" else "Total à payer",
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
                                FitText(band.label, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary, modifier = Modifier.weight(1f), minScale = 0.8f)
                                Spacer(Modifier.width(DsSpacing.xs))
                                Text(
                                    percentOf(amount, total) ?: percent(0.0),
                                    fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary,
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
            FitText(money.da(line.balance), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                line.oldest.label, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = color,
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
    val query by viewModel.debtorQuery.collectAsState()
    val sort by viewModel.debtorSort.collectAsState()
    val words = wordsFor(state.side)
    val report = state.report
    val open = openDebtor(state.side)
    var sortSheet by remember { mutableStateOf(false) }

    // Recomputed only when the debtors, the words searched or the order change.
    val shown = remember(report?.debtors, query, sort) {
        report?.let { debtorsMatching(it.debtors, query, sort) }.orEmpty()
    }
    // A new search or a new order starts at the top of what it found.
    val listState = rememberLazyListState()
    LaunchedEffect(query, sort) { listState.scrollToItem(0) }

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
                Column(Modifier.fillMaxWidth().background(DsColors.Surface).padding(bottom = DsSpacing.sm)) {
                    DsCompactSearchField(
                        value         = query,
                        onValueChange = viewModel::setDebtorQuery,
                        placeholder   = words.searchHint,
                        modifier      = Modifier.padding(horizontal = DsSpacing.lg)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (query.isBlank()) words.count(shown.size) else "${shown.size} sur ${report.debtors.size}",
                            fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
                        )
                        ListSortChip(active = sort != DebtorSort.DETTE_DESC, onClick = { sortSheet = true })
                    }
                }
                if (shown.isEmpty()) {
                    ReportMessage("Aucun résultat pour « ${query.trim()} »")
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = DsSpacing.sm, bottom = DsSpacing.xxxl),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                    ) {
                        items(shown, key = { it.id }) { DebtorRow(it, words) { open(it.id) } }
                    }
                }
            }
        }
    }

    if (sortSheet) {
        SortOptionsSheet(
            options   = DebtorSort.entries,
            selected  = sort,
            label     = { it.label },
            onSelect  = viewModel::setDebtorSort,
            onDismiss = { sortSheet = false },
        )
    }
}
