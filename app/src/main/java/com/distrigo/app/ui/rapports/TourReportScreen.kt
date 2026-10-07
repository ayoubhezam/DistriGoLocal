package com.distrigo.app.ui.rapports

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PersonPinCircle
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.repository.TourFigures
import com.distrigo.app.data.repository.TourReport
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
import java.util.Locale

private val TourPink = Color(0xFFE91E63)
private val DAY = DateTimeFormatter.ofPattern("EEE dd/MM/yyyy", Locale.FRENCH)

private val INFO_TOTAL = ReportInfo(
    "Ventes en tournée",
    "Ce que les tournées commencées sur la période ont vendu, et combien il y en a eu.\n\n" +
        "Une tournée compte en entier au jour où elle a commencé, même si elle finit le lendemain.\n\n" +
        "« Payé à la vente » : ce que les clients ont réglé au moment de la vente ; « À crédit » : le reste, à encaisser plus tard."
)
private val INFO_TOURS = ReportInfo(
    "Tournées",
    "Le nombre de tournées commencées sur la période, et combien sont encore ouvertes."
)
private val INFO_PER_TOUR = ReportInfo(
    "Moyenne par tournée",
    "Ce qu'une tournée vend en moyenne : ventes en tournée ÷ nombre de tournées."
)
private val INFO_VISITS = ReportInfo(
    "Clients visités",
    "Les clients marqués « visité » sur ceux prévus dans les tournées de la période. Un client prévu dans deux tournées compte deux fois."
)
private val INFO_SALES = ReportInfo(
    "Ventes",
    "Le nombre de ventes faites pendant les tournées de la période."
)
private val INFO_LIST = ReportInfo(
    "Tournées de la période",
    "Chaque tournée, la plus récente en premier : son jour, ses clients visités sur les prévus, ses ventes et ce qu'elle a vendu. Touchez-en une pour l'ouvrir."
)

/** Rapports › Tournées: what the tournées sold, collected and visited, over the shared period. */
@Composable
fun TourReportScreen(onBack: () -> Unit, viewModel: TourReportViewModel) {
    val state by viewModel.state.collectAsState()
    val drill = LocalDrillDown.current

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = "Tournées", subtitle = "Ventes, encaissements et visites", leading = DsTopBarLeading.Back(onBack))
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
            // Tournées are the camion's: no Tout / Dépôt / Camion switch.
            item { ReportFilterBar(state.filter, viewModel::setFilter, showSource = false) }
            val report = state.report
            when {
                report == null && state.error != null -> item { ReportMessage(state.error!!) }
                report == null -> item {
                    Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DsColors.Primary)
                    }
                }
                report.tours.isEmpty() -> item { ReportMessage("Aucune tournée commencée sur cette période.") }
                else -> {
                    item { TotalCard(report) }
                    item { Tiles(report) }
                    item { TitleWithInfo("Tournées de la période", INFO_LIST) }
                    items(report.tours, key = { "tour_${it.id}" }) { t ->
                        TourLine(t) { drill(DrillTarget.Tournee(t.id)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalCard(report: TourReport) {
    val money = LocalMoneyFormatter.current
    // The count of tournées is in its own card.
    ReportHeroCard(
        title = "Ventes en tournée",
        amount = money.da(report.total),
        caption = null,
        info = INFO_TOTAL,
        halves = listOf(
            HeroHalf("Payé à la vente", money.da(report.paid)),
            HeroHalf("À crédit", money.da(report.total - report.paid)),
        ),
    )
}

@Composable
private fun Tiles(report: TourReport) {
    val money = LocalMoneyFormatter.current
    Column(Modifier.padding(horizontal = DsSpacing.lg), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile("Tournées", report.tours.size.toString(), DsColors.TextPrimary, Modifier.weight(1f),
                if (report.openCount > 0) "${report.openCount} en cours" else null, Icons.Default.LocalShipping, INFO_TOURS,
                accent = TourPink)
            KpiTile("Par tournée", report.perTour?.let { money.da(it) } ?: "—", DsColors.TextPrimary, Modifier.weight(1f),
                null, Icons.Default.Route, INFO_PER_TOUR, accent = DsColors.Primary)
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile("Clients visités", "${report.visited} / ${report.planned}", DsColors.TextPrimary, Modifier.weight(1f),
                report.visitRate?.let { percent(it) }, Icons.Default.PersonPinCircle, INFO_VISITS, accent = DsColors.Success)
            KpiTile("Ventes", report.sales.toString(), DsColors.TextPrimary, Modifier.weight(1f),
                null, Icons.Default.Receipt, INFO_SALES, accent = Color(0xFF0E9384))
        }
    }
}

/**
 * A tournée: its name, "En cours" while it is open, and what it sold on the right; under it, the whole
 * width for its day, its visits and its sales.
 */
@Composable
private fun TourLine(t: TourFigures, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    Row(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().clip(DsShapes.medium).background(DsColors.Surface)
            .clickable(role = Role.Button, onClick = onClick).padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(DsShapes.small).background(TourPink.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.LocalShipping, contentDescription = null, tint = TourPink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The name and its pill share what the amount leaves; a long name gives way first.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (t.open) {
                        Spacer(Modifier.width(DsSpacing.sm))
                        Text("En cours", fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Success,
                            maxLines = 1, softWrap = false,
                            modifier = Modifier.clip(DsShapes.pill).background(DsColors.Success.copy(alpha = 0.12f))
                                .padding(horizontal = DsSpacing.sm, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.width(DsSpacing.sm))
                Text(money.da(t.total), fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary,
                    maxLines = 1, softWrap = false)
            }
            val day = t.day?.format(DAY)?.replaceFirstChar { it.uppercase() } ?: "—"
            Text("$day · ${t.visited}/${t.planned} visités · ${plural(t.sales, "vente", "ventes")}",
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
