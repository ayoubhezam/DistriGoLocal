package com.distrigo.app.ui.rapports

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.repository.ProfitReport
import com.distrigo.app.ui.common.FitText
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.format.LocalMoneyFormatter
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

private val ChargeColors = listOf(DsColors.Warning, DsColors.Primary, DsColors.AccentTeal, DsColors.AccentRose, DsColors.AccentSky, DsColors.TextTertiary)

private val INFO_NET: ReportInfo get() = ReportInfo(reportText(R.string.rapports_net_result), reportText(R.string.rapports_info_net_result))
private val INFO_GROSS: ReportInfo get() = ReportInfo(reportText(R.string.rapports_gross_margin), reportText(R.string.rapports_info_gross_margin))
private val INFO_CHARGES: ReportInfo get() = ReportInfo(reportText(R.string.rapports_charges), reportText(R.string.rapports_info_charges))
private val INFO_LOSSES: ReportInfo get() = ReportInfo(reportText(R.string.rapports_losses), reportText(R.string.rapports_info_losses))
private val INFO_SHARE: ReportInfo get() = ReportInfo(reportText(R.string.rapports_profitability), reportText(R.string.rapports_info_profitability))
private val INFO_STATEMENT: ReportInfo get() = ReportInfo(reportText(R.string.rapports_statement), reportText(R.string.rapports_info_statement))
private val INFO_CHARGE_TYPES: ReportInfo get() = ReportInfo(reportText(R.string.rapports_charges_by_type), reportText(R.string.rapports_info_charges_by_type))

/** Rapports › Résultat: from the chiffre d'affaires to what is left, over the shared period. */
@Composable
fun ProfitReportScreen(onBack: () -> Unit, viewModel: ProfitReportViewModel) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = stringResource(R.string.rapports_profit), subtitle = stringResource(R.string.rapports_profit_sub), leading = DsTopBarLeading.Back(onBack))
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
            val report = state.report
            when {
                report == null && state.error != null -> item { ReportMessage(state.error!!) }
                report == null -> item {
                    Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DsColors.Primary)
                    }
                }
                else -> {
                    item { NetCard(report) }
                    item { Tiles(report) }
                    item { Statement(report) }
                    if (report.charges.isNotEmpty()) item { ChargeTypes(report) }
                }
            }
        }
    }
}

@Composable
private fun NetCard(report: ProfitReport) {
    val money = LocalMoneyFormatter.current
    ReportHeroCard(
        title = stringResource(R.string.rapports_net_result),
        amount = money.figure(report.net),
        caption = stringResource(if (report.net >= 0) R.string.rapports_profit_in_period else R.string.rapports_loss_in_period),
        info = INFO_NET,
        halves = listOf(
            HeroHalf(stringResource(R.string.rapports_gross_margin), money.figure(report.grossMargin)),
            HeroHalf(stringResource(R.string.rapports_taken_off), money.figure(report.returnsMargin + report.chargesTotal + report.lossesTotal, prefix = "− ")),
        ),
    )
}

@Composable
private fun Tiles(report: ProfitReport) {
    val money = LocalMoneyFormatter.current
    val grossLoss = report.grossMargin < 0
    val netLoss = report.net < 0
    Column(Modifier.padding(horizontal = DsSpacing.lg), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile(stringResource(R.string.rapports_gross_margin), money.figure(report.grossMargin), if (grossLoss) DsColors.Danger else DsColors.PrimaryDark,
                Modifier.weight(1f), null,
                if (grossLoss) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp, INFO_GROSS,
                accent = if (grossLoss) DsColors.Danger else DsColors.Primary)
            KpiTile(stringResource(R.string.rapports_profitability), rateFigure(report.netShare), if (netLoss) DsColors.Danger else DsColors.PrimaryDark,
                Modifier.weight(1f), null, Icons.Default.Percent, INFO_SHARE,
                accent = if (netLoss) DsColors.Danger else DsColors.Success)
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile(stringResource(R.string.rapports_charges), money.figure(report.chargesTotal), DsColors.PrimaryDark, Modifier.weight(1f),
                plural(R.plurals.charges_count, report.chargesCount), Icons.Default.Payments, INFO_CHARGES, accent = DsColors.Warning)
            KpiTile(stringResource(R.string.rapports_losses), money.figure(report.lossesTotal), DsColors.PrimaryDark, Modifier.weight(1f),
                plural(R.plurals.pertes_count, report.lossesCount), Icons.Default.RemoveShoppingCart, INFO_LOSSES, accent = DsColors.Danger)
        }
    }
}

/** One line of the statement: its label, and its amount — taken off when [minus], a total when [total]. */
@Composable
private fun StatementLine(label: String, amount: Double, minus: Boolean = false, total: Boolean = false, color: Color = DsColors.PrimaryDark) {
    val money = LocalMoneyFormatter.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = if (total) DsTextSize.body else DsTextSize.bodySmall,
            fontWeight = if (total) FontWeight.Bold else FontWeight.Normal,
            color = if (total) DsColors.TextPrimary else DsColors.TextSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(DsSpacing.sm))
        Text((if (minus) "− " else "") + money.da(amount),
            fontSize = if (total) DsTextSize.body else DsTextSize.bodySmall,
            fontWeight = if (total) FontWeight.Bold else FontWeight.Medium, color = color, maxLines = 1, softWrap = false)
    }
}

/** From the chiffre d'affaires down to the résultat net. */
@Composable
private fun Statement(report: ProfitReport) {
    CardColumn {
        CardTitle(stringResource(R.string.rapports_statement), INFO_STATEMENT)
        Spacer(Modifier.height(DsSpacing.sm))
        StatementLine(stringResource(R.string.rapports_turnover), report.sales)
        StatementLine(stringResource(R.string.rapports_cost_of_sales), report.cost, minus = true)
        HorizontalDivider(color = DsColors.Border)
        StatementLine(stringResource(R.string.rapports_gross_margin), report.grossMargin, total = true, color = if (report.grossMargin < 0) DsColors.Danger else DsColors.PrimaryDark)
        if (report.returnsCount > 0) {
            StatementLine(stringResource(R.string.rapports_client_returns_n, report.returnsCount), report.returnsMargin, minus = true)
        }
        StatementLine(stringResource(R.string.rapports_charges), report.chargesTotal, minus = true)
        StatementLine(stringResource(R.string.rapports_losses), report.lossesTotal, minus = true)
        HorizontalDivider(color = DsColors.Border)
        // Colour is news here: green when the period earned, red when it lost.
        StatementLine(stringResource(R.string.rapports_net_result), report.net, total = true, color = if (report.net < 0) DsColors.Danger else DsColors.Success)
    }
}

/** The charges by type: a ring of the first five and the rest, with their shares. */
@Composable
private fun ChargeTypes(report: ProfitReport) {
    val money = LocalMoneyFormatter.current
    val shown = report.charges.take(5)
    val rest = report.charges.drop(5)
    val slices = shown.map { com.distrigo.app.ui.common.BuiltInNames.chargeType(it.type) to it.value } + (if (rest.isNotEmpty()) listOf(stringResource(R.string.rapports_other_types) to rest.sumOf { it.value }) else emptyList())
    val total = report.chargesTotal
    CardColumn {
        CardTitle(stringResource(R.string.rapports_charges_by_type), INFO_CHARGE_TYPES)
        Spacer(Modifier.height(DsSpacing.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ShareRing(slices.mapIndexed { i, (_, v) -> v to ChargeColors[i % ChargeColors.size] }, total, stringResource(R.string.rapports_charges_total), Modifier.weight(1f))
            Spacer(Modifier.width(DsSpacing.lg))
            Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                slices.forEachIndexed { i, (name, amount) ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 4.dp).size(10.dp).clip(DsShapes.pill).background(ChargeColors[i % ChargeColors.size]))
                        Spacer(Modifier.width(DsSpacing.sm))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(DsSpacing.xs))
                                Text(percentOf(amount, total) ?: percent(0.0), fontSize = DsTextSize.bodySmall,
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
