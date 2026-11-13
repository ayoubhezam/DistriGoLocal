package com.distrigo.app.ui.rapports

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.repository.DebtSide
import com.distrigo.app.data.repository.InactiveClient
import com.distrigo.app.data.repository.PartyFigures
import com.distrigo.app.data.repository.PartyRanking
import com.distrigo.app.data.repository.PartyReport
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.EntityAvatar
import com.distrigo.app.ui.common.FitText
import com.distrigo.app.ui.common.matchesAllTokens
import com.distrigo.app.ui.common.searchTokens
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

/** How many parties the report itself ranks, and how many the concentration ring names. */
private const val TOP_PARTIES = 10
private const val TOP_SHARE = 5

private val ShareColors = listOf(DsColors.Primary, DsColors.AccentTeal, DsColors.Warning, DsColors.AccentRose, DsColors.AccentSky, DsColors.TextTertiary)
private val DAY: DateTimeFormatter get() = com.distrigo.app.core.format.AppDates.numeric("dd/MM/yyyy")

/** The words that change with the side, and what each figure means — for its ⓘ. */
private class PartyWords(
    val total: String, val totalInfo: ReportInfo, val people: (Int) -> String, val documents: (Int) -> String,
    val active: String, val activeInfo: ReportInfo, val average: String, val averageInfo: ReportInfo,
    val topTitle: String, val topInfo: ReportInfo, val shareInfo: ReportInfo, val listTitle: String,
    val rankings: List<PartyRanking>, val rankingLabel: (PartyRanking) -> Int,
)

private fun wordsFor(side: DebtSide) = if (side == DebtSide.CLIENTS) PartyWords(
    total = reportText(R.string.rapports_sales_to_clients),
    totalInfo = ReportInfo(reportText(R.string.rapports_sales_to_clients), reportText(R.string.rapports_info_sales_to_clients)),
    people = { plural(R.plurals.rapports_active_clients_n, it) },
    documents = { plural(R.plurals.ventes_count, it) },
    active = reportText(R.string.rapports_active_clients),
    activeInfo = ReportInfo(reportText(R.string.rapports_active_clients), reportText(R.string.rapports_info_active_clients)),
    average = reportText(R.string.rapports_avg_basket),
    averageInfo = ReportInfo(reportText(R.string.rapports_avg_basket), reportText(R.string.rapports_info_avg_basket_clients)),
    topTitle = reportText(R.string.rapports_top_clients),
    topInfo = ReportInfo(reportText(R.string.rapports_top_clients), reportText(R.string.rapports_info_top_clients)),
    shareInfo = ReportInfo(reportText(R.string.rapports_concentration), reportText(R.string.rapports_info_concentration_clients)),
    listTitle = reportText(R.string.rapports_clients_of_period),
    rankings = PartyRanking.entries,
    rankingLabel = { if (it == PartyRanking.DOCUMENTS) R.string.rapports_rank_sales else it.label },
) else PartyWords(
    total = reportText(R.string.rapports_purchases_from_suppliers),
    totalInfo = ReportInfo(reportText(R.string.rapports_purchases_from_suppliers), reportText(R.string.rapports_info_purchases_from_suppliers)),
    people = { plural(R.plurals.suppliers_count, it) },
    documents = { plural(R.plurals.tournees_bons_count, it) },
    active = reportText(R.string.rapports_suppliers),
    activeInfo = ReportInfo(reportText(R.string.rapports_suppliers), reportText(R.string.rapports_info_suppliers)),
    average = reportText(R.string.rapports_avg_bon),
    averageInfo = ReportInfo(reportText(R.string.rapports_avg_bon), reportText(R.string.rapports_info_avg_bon)),
    topTitle = reportText(R.string.rapports_top_suppliers),
    topInfo = ReportInfo(reportText(R.string.rapports_top_suppliers), reportText(R.string.rapports_info_top_suppliers)),
    shareInfo = ReportInfo(reportText(R.string.rapports_concentration), reportText(R.string.rapports_info_concentration_suppliers)),
    listTitle = reportText(R.string.rapports_suppliers_of_period),
    rankings = listOf(PartyRanking.MONTANT, PartyRanking.DOCUMENTS),
    rankingLabel = { if (it == PartyRanking.DOCUMENTS) R.string.rapports_rank_bons else it.label },
)

private val INFO_NEW: ReportInfo get() = ReportInfo(reportText(R.string.rapports_new_clients), reportText(R.string.rapports_info_new_clients))
private val INFO_INACTIVE: ReportInfo get() = ReportInfo(reportText(R.string.rapports_inactive_clients), reportText(R.string.rapports_info_inactive_clients))

/** Opens a party's own page through the app's drill-down. */
@Composable
private fun openParty(side: DebtSide): (Int) -> Unit {
    val drill = LocalDrillDown.current
    return { id -> drill(if (side == DebtSide.CLIENTS) DrillTarget.Client(id) else DrillTarget.Supplier(id)) }
}

/** Rapports › Clients et fournisseurs: who buys and who sells to you, over the shared period. */
@Composable
fun PartyReportScreen(onBack: () -> Unit, onSeeAll: () -> Unit, onInactive: () -> Unit, viewModel: PartyReportViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ranking by viewModel.ranking.collectAsStateWithLifecycle()
    val words = wordsFor(state.side)
    val open = openParty(state.side)

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = stringResource(R.string.rapports_parties), subtitle = stringResource(R.string.rapports_parties_sub), leading = DsTopBarLeading.Back(onBack))
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
                ReportSegmented(DebtSide.entries, state.side, { stringResource(it.label) }, viewModel::setSide, Modifier.padding(horizontal = DsSpacing.lg))
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
                    item { TotalCard(report, words) }
                    item { Tiles(report, words, onInactive) }
                    if (report.parties.isNotEmpty()) {
                        item { TitleWithInfo(words.topTitle, words.topInfo, action = stringResource(R.string.common_action_see_all), onAction = onSeeAll) }
                        item {
                            ReportSegmented(words.rankings, ranking, { stringResource(words.rankingLabel(it)) }, { viewModel.ranking.value = it },
                                Modifier.padding(horizontal = DsSpacing.lg))
                        }
                        itemsIndexed(report.ranked(ranking).take(TOP_PARTIES), key = { _, p -> "top_${p.id}" }) { i, p ->
                            PartyLine(i + 1, p, ranking, words) { open(p.id) }
                        }
                        item { ShareCard(report, words) }
                    } else {
                        item { ReportMessage(stringResource(if (report.side == DebtSide.CLIENTS) R.string.rapports_no_sales else R.string.rapports_no_purchases)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalCard(report: PartyReport, words: PartyWords) {
    val money = LocalMoneyFormatter.current
    ReportHeroCard(
        title = words.total,
        amount = money.figure(report.total),
        caption = "${words.people(report.parties.size)} · ${words.documents(report.documents)}",
        info = words.totalInfo,
    )
}

@Composable
private fun Tiles(report: PartyReport, words: PartyWords, onInactive: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val clients = report.side == DebtSide.CLIENTS
    Column(Modifier.padding(horizontal = DsSpacing.lg), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile(words.active, countFigure(report.parties.size), DsColors.PrimaryDark, Modifier.weight(1f), null,
                Icons.Default.Group, words.activeInfo, accent = DsColors.Primary)
            KpiTile(words.average, report.average?.let { money.figure(it) } ?: Figure.text("—"), DsColors.PrimaryDark, Modifier.weight(1f), null,
                if (clients) Icons.Default.ShoppingBasket else Icons.Default.Receipt, words.averageInfo, accent = DsColors.AccentTeal)
        }
        if (clients) Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile(stringResource(R.string.rapports_new_clients), countFigure(report.newCount), DsColors.PrimaryDark, Modifier.weight(1f), null,
                Icons.Default.PersonAdd, INFO_NEW, accent = DsColors.Success)
            KpiTile(stringResource(R.string.rapports_inactive_clients), countFigure(report.inactive.size), DsColors.PrimaryDark,
                Modifier.weight(1f).clip(DsShapes.large).clickable(role = Role.Button, onClick = onInactive), null,
                Icons.Default.Bedtime, INFO_INACTIVE, accent = DsColors.Warning)
        }
    }
}

/** A party in a ranking: its rank, avatar and name; under it its documents, and the figure it is ranked by. */
@Composable
private fun PartyLine(rank: Int, p: PartyFigures, by: PartyRanking, words: PartyWords, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    Row(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().clip(DsShapes.medium).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.medium)
            .clickable(role = Role.Button, onClick = onClick).padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.TextTertiary, modifier = Modifier.width(26.dp))
        EntityAvatar(p.name, p.imageUri, size = 36.dp)
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val detail = if (by == PartyRanking.MONTANT || by == PartyRanking.DOCUMENTS) words.documents(p.count)
                else "${words.documents(p.count)} · ${money.da(p.total)}"
                Text(detail, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(DsSpacing.sm))
                val figure = when (by) {
                    PartyRanking.MONTANT, PartyRanking.DOCUMENTS -> money.da(p.total)
                    PartyRanking.MARGE -> money.da(p.margin)
                }
                Text(figure, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold,
                    color = if (by == PartyRanking.MARGE && p.margin < 0) DsColors.Danger else DsColors.PrimaryDark, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** The five biggest and the rest: a ring of their shares of the period's total. */
@Composable
private fun ShareCard(report: PartyReport, words: PartyWords) {
    val money = LocalMoneyFormatter.current
    val top = report.ranked(PartyRanking.MONTANT).take(TOP_SHARE)
    val rest = report.total - top.sumOf { it.total }
    val slices = top.map { it.name to it.total } + (if (rest > 0.005) listOf(stringResource(R.string.rapports_the_others) to rest) else emptyList())
    CardColumn {
        CardTitle(stringResource(R.string.rapports_concentration), words.shareInfo)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.rapports_top_five_share, report.topShare(TOP_SHARE)?.let { percent(it) } ?: percent(0.0)),
            fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        Spacer(Modifier.height(DsSpacing.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ShareRing(slices.mapIndexed { i, (_, v) -> v to ShareColors[i % ShareColors.size] }, report.total, stringResource(R.string.common_label_total), Modifier.weight(1f))
            Spacer(Modifier.width(DsSpacing.lg))
            Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                slices.forEachIndexed { i, (name, amount) ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 4.dp).size(10.dp).clip(DsShapes.pill).background(ShareColors[i % ShareColors.size]))
                        Spacer(Modifier.width(DsSpacing.sm))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(DsSpacing.xs))
                                Text(percentOf(amount, report.total) ?: percent(0.0), fontSize = DsTextSize.bodySmall,
                                    fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark)
                            }
                            FitText(money.da(amount), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

/** Rapports › Clients et fournisseurs › Voir tout: every party of the period, ranked, searchable. */
@Composable
fun PartiesScreen(onBack: () -> Unit, viewModel: PartyReportViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ranking by viewModel.ranking.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val words = wordsFor(state.side)
    val open = openParty(state.side)
    val report = state.report
    val ranked = remember(report, ranking) { report?.ranked(ranking).orEmpty() }
    // Each keeps its rank in the whole list, searched or not.
    val shown = remember(ranked, query) {
        val tokens = searchTokens(query)
        ranked.mapIndexed { i, p -> i + 1 to p }.filter { matchesAllTokens(tokens, it.second.name) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(query, ranking) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = words.listTitle, leading = DsTopBarLeading.Back(onBack))
        Column(Modifier.fillMaxWidth().background(DsColors.SurfaceMuted).padding(bottom = DsSpacing.sm)) {
            DsCompactSearchField(value = query, onValueChange = { viewModel.query.value = it },
                placeholder = stringResource(if (state.side == DebtSide.CLIENTS) R.string.common_search_client else R.string.purchases_search_supplier),
                modifier = Modifier.padding(horizontal = DsSpacing.lg))
            Spacer(Modifier.height(8.dp))
            ReportSegmented(words.rankings, ranking, { stringResource(words.rankingLabel(it)) }, { viewModel.ranking.value = it }, Modifier.padding(horizontal = DsSpacing.lg))
            Spacer(Modifier.height(6.dp))
            Text(if (query.isBlank()) words.people(ranked.size) else stringResource(R.string.common_shown_of_total, shown.size, ranked.size),
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary, modifier = Modifier.padding(horizontal = DsSpacing.lg))
        }
        when {
            report == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DsColors.Primary) }
            shown.isEmpty() -> ReportMessage(if (query.isBlank()) stringResource(R.string.rapports_nothing_in_period) else stringResource(R.string.rapports_no_result_for, query.trim()))
            else -> LazyColumn(
                state = listState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = DsSpacing.sm, bottom = DsSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
            ) {
                items(shown, key = { it.second.id }) { (rank, p) -> PartyLine(rank, p, ranking, words) { open(p.id) } }
            }
        }
    }
}

/** Rapports › Clients et fournisseurs › Clients inactifs: who bought before and not since, the biggest first. */
@Composable
fun InactiveClientsScreen(onBack: () -> Unit, viewModel: PartyReportViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val open = openParty(DebtSide.CLIENTS)
    val all = state.report?.inactive.orEmpty()
    val shown = remember(all, query) { val tokens = searchTokens(query); all.filter { matchesAllTokens(tokens, it.name) } }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = stringResource(R.string.rapports_inactive_clients), subtitle = stringResource(R.string.rapports_inactive_sub), leading = DsTopBarLeading.Back(onBack))
        Column(Modifier.fillMaxWidth().background(DsColors.SurfaceMuted).padding(bottom = DsSpacing.sm)) {
            DsCompactSearchField(value = query, onValueChange = { viewModel.query.value = it }, placeholder = stringResource(R.string.common_search_client),
                modifier = Modifier.padding(horizontal = DsSpacing.lg))
            Spacer(Modifier.height(8.dp))
            Text(if (query.isBlank()) plural(R.plurals.common_clients_count, all.size) else stringResource(R.string.common_shown_of_total, shown.size, all.size),
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary, modifier = Modifier.padding(horizontal = DsSpacing.lg))
        }
        when {
            state.report == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DsColors.Primary) }
            shown.isEmpty() -> ReportMessage(if (query.isBlank()) stringResource(R.string.rapports_all_bought) else stringResource(R.string.rapports_no_result_for, query.trim()))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = DsSpacing.sm, bottom = DsSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
            ) {
                items(shown, key = { it.id }) { c -> InactiveLine(c) { open(c.id) } }
            }
        }
    }
}

@Composable
private fun InactiveLine(c: InactiveClient, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    Row(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().clip(DsShapes.medium).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.medium)
            .clickable(role = Role.Button, onClick = onClick).padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntityAvatar(c.name, c.imageUri, size = 36.dp)
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(c.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rapports_last_purchase_on, c.lastSale.format(DAY)), fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(money.da(c.total), fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark, maxLines = 1, softWrap = false)
            }
        }
    }
}
