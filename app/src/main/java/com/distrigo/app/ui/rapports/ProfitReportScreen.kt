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

private val ChargeColors = listOf(Color(0xFFF79009), Color(0xFF6366F1), Color(0xFF0E9384), Color(0xFFE91E63), Color(0xFF0BA5EC), Color(0xFF98A2B3))

private val INFO_NET = ReportInfo(
    "Résultat net",
    "Ce que la période a vraiment rapporté, une fois tout retiré :\n" +
        "marge brute − marge des retours − charges − pertes.\n\n" +
        "Toute l'activité, dépôt et camion ensemble : une charge n'appartient ni à l'un ni à l'autre."
)
private val INFO_GROSS = ReportInfo(
    "Marge brute",
    "Chiffre d'affaires − ce que les produits vendus avaient coûté, chaque ligne au prix d'achat qu'elle avait au moment de la vente."
)
private val INFO_CHARGES = ReportInfo(
    "Charges",
    "Les dépenses de la période — véhicule, personnel, bureau, distribution… — telles qu'enregistrées dans Charges, et leur nombre."
)
private val INFO_LOSSES = ReportInfo(
    "Pertes",
    "Ce que les pertes de la période ont coûté — casse, péremption, vol… — chaque perte au prix d'achat qu'avait le produit, et leur nombre."
)
private val INFO_SHARE = ReportInfo(
    "Rentabilité",
    "Résultat net ÷ chiffre d'affaires × 100 : pour 100 DA vendus, ce qui reste une fois les produits, les retours, les charges et les pertes payés.\n\n" +
        "Ce n'est pas le taux de marge, qui se compte sur le prix d'achat (voir le rapport Ventes)."
)
private val INFO_STATEMENT = ReportInfo(
    "Compte de résultat",
    "Du chiffre d'affaires au résultat net, ligne par ligne. « Coût des ventes » : ce que les produits vendus avaient coûté, chaque ligne au prix d'achat de son moment.\n\n" +
        "« Retours clients » : la marge que les retours ont annulée — ce qui a été remboursé, moins ce que valent les produits revenus, au prix d'achat actuel (une ligne de retour ne garde pas son coût). " +
        "Les produits revenus puis jetés (périmés, défectueux) sont en plus dans les pertes : leur marge est annulée, puis la marchandise est perdue."
)
private val INFO_CHARGE_TYPES = ReportInfo(
    "Charges par type",
    "Les charges de la période réparties par type, selon leur montant. Les cinq premiers, le reste dans « Autres types »."
)

/** Rapports › Résultat: from the chiffre d'affaires to what is left, over the shared period. */
@Composable
fun ProfitReportScreen(onBack: () -> Unit, viewModel: ProfitReportViewModel) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = "Résultat", subtitle = "Ce qui reste une fois tout payé", leading = DsTopBarLeading.Back(onBack))
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
        title = "Résultat net",
        amount = money.da(report.net),
        caption = if (report.net >= 0) "Bénéfice sur la période" else "Perte sur la période",
        info = INFO_NET,
        halves = listOf(
            HeroHalf("Marge brute", money.da(report.grossMargin)),
            HeroHalf("Retiré", "− ${money.da(report.returnsMargin + report.chargesTotal + report.lossesTotal)}"),
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
            KpiTile("Marge brute", money.da(report.grossMargin), if (grossLoss) DsColors.Danger else DsColors.TextPrimary,
                Modifier.weight(1f), null,
                if (grossLoss) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp, INFO_GROSS,
                accent = if (grossLoss) DsColors.Danger else DsColors.Primary)
            KpiTile("Rentabilité", report.netShare?.let { percent(it) } ?: "—", if (netLoss) DsColors.Danger else DsColors.TextPrimary,
                Modifier.weight(1f), null, Icons.Default.Percent, INFO_SHARE,
                accent = if (netLoss) DsColors.Danger else DsColors.Success)
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile("Charges", money.da(report.chargesTotal), DsColors.TextPrimary, Modifier.weight(1f),
                plural(report.chargesCount, "charge", "charges"), Icons.Default.Payments, INFO_CHARGES, accent = DsColors.Warning)
            KpiTile("Pertes", money.da(report.lossesTotal), DsColors.TextPrimary, Modifier.weight(1f),
                plural(report.lossesCount, "perte", "pertes"), Icons.Default.RemoveShoppingCart, INFO_LOSSES, accent = DsColors.Danger)
        }
    }
}

/** One line of the statement: its label, and its amount — taken off when [minus], a total when [total]. */
@Composable
private fun StatementLine(label: String, amount: Double, minus: Boolean = false, total: Boolean = false, color: Color = DsColors.TextPrimary) {
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
        CardTitle("Compte de résultat", INFO_STATEMENT)
        Spacer(Modifier.height(DsSpacing.sm))
        StatementLine("Chiffre d'affaires", report.sales)
        StatementLine("Coût des ventes", report.cost, minus = true)
        HorizontalDivider(color = DsColors.Border)
        StatementLine("Marge brute", report.grossMargin, total = true, color = if (report.grossMargin < 0) DsColors.Danger else DsColors.TextPrimary)
        if (report.returnsCount > 0) {
            StatementLine("Retours clients (${report.returnsCount})", report.returnsMargin, minus = true)
        }
        StatementLine("Charges", report.chargesTotal, minus = true)
        StatementLine("Pertes", report.lossesTotal, minus = true)
        HorizontalDivider(color = DsColors.Border)
        // Colour is news here: green when the period earned, red when it lost.
        StatementLine("Résultat net", report.net, total = true, color = if (report.net < 0) DsColors.Danger else DsColors.Success)
    }
}

/** The charges by type: a ring of the first five and the rest, with their shares. */
@Composable
private fun ChargeTypes(report: ProfitReport) {
    val money = LocalMoneyFormatter.current
    val shown = report.charges.take(5)
    val rest = report.charges.drop(5)
    val slices = shown.map { it.type to it.value } + (if (rest.isNotEmpty()) listOf("Autres types" to rest.sumOf { it.value }) else emptyList())
    val total = report.chargesTotal
    CardColumn {
        CardTitle("Charges par type", INFO_CHARGE_TYPES)
        Spacer(Modifier.height(DsSpacing.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ShareRing(slices.mapIndexed { i, (_, v) -> v to ChargeColors[i % ChargeColors.size] }, total, "Total des charges", Modifier.weight(1f))
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
                                    fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                            }
                            FitText(money.da(amount), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
