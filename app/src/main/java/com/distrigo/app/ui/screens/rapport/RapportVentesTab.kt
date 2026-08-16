package com.distrigo.app.ui.screens.rapport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
@Composable
fun RapportVentesTab(
    modifier: Modifier = Modifier,
    viewModel: RapportVentesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    var drillDown by remember { mutableStateOf<VentesDrillDown>(VentesDrillDown.None) }

    BackHandler(enabled = drillDown != VentesDrillDown.None) {
        drillDown = when (val d = drillDown) {
            is VentesDrillDown.Secteurs -> VentesDrillDown.Communes(d.wilaya)
            is VentesDrillDown.Communes -> VentesDrillDown.None
            VentesDrillDown.None -> VentesDrillDown.None
        }
    }

    when (val d = drillDown) {
        is VentesDrillDown.Communes -> {
            // في RapportVentesTab (حالة VentesDrillDown.Communes):
            GeoDrillDownScreen(
                title = d.wilaya.name,
                items = d.wilaya.communes,
                valueLabel = { formatCurrency(it.amount.roundToInt()) },
                onBack = { drillDown = VentesDrillDown.None },
                onItemClick = { commune -> drillDown = VentesDrillDown.Secteurs(d.wilaya, commune) },
                modifier = modifier
            )
            return
        }
        is VentesDrillDown.Secteurs -> {
            // وحالة VentesDrillDown.Secteurs:
            GeoDrillDownScreen(
                title = d.commune.name,
                items = d.commune.secteurs,
                valueLabel = { formatCurrency(it.amount.roundToInt()) },
                onBack = { drillDown = VentesDrillDown.Communes(d.wilaya) },
                onItemClick = null,
                modifier = modifier
            )
            return
        }
        VentesDrillDown.None -> Unit
    }

    when (val state = uiState) {
        is RapportVentesUiState.Loading -> { /* TODO */ }
        is RapportVentesUiState.Empty -> {
            RapportVentesEmptyState(
                selectedPeriod = selectedPeriod,
                periodeLabel = state.periodeLabel,
                onPeriodSelected = viewModel::onPeriodSelected,
                onCustomRangeSelected = viewModel::onCustomRangeSelected,
                modifier = modifier
            )
        }
        is RapportVentesUiState.Content -> {
            RapportVentesContent(
                data = state.data,
                selectedPeriod = selectedPeriod,
                onPeriodSelected = viewModel::onPeriodSelected,
                onCustomRangeSelected = viewModel::onCustomRangeSelected,
                onWilayaClick = { wilaya -> drillDown = VentesDrillDown.Communes(wilaya) },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun RapportVentesContent(
    data: RapportVentesData,
    selectedPeriod: ReportPeriod,
    onPeriodSelected: (ReportPeriod) -> Unit,
    onCustomRangeSelected: (java.time.LocalDate, java.time.LocalDate) -> Unit,
    onWilayaClick: (com.distrigo.app.data.model.report.WilayaBreakdown) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(DsColors.SurfaceMuted),
        contentPadding = PaddingValues(bottom = DsSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
    ) {
        item(key = "period") {
            VentesPeriodSection(
                selectedPeriod = selectedPeriod,
                periodeLabel = data.periodeLabel,
                onPeriodSelected = onPeriodSelected,
                onCustomRangeSelected = onCustomRangeSelected,
                modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
            )
        }
        item(key = "kpis") {
            Column(
                modifier = Modifier.padding(horizontal = DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                    StatCard(
                        icon = Icons.Filled.TrendingUp,
                        iconTint = DsColors.Success,
                        iconBg = DsColors.SuccessLight,
                        value = formatCurrency(data.chiffreAffairesTotal.roundToInt()),
                        label = "Chiffre d'affaires total",
                        trend = data.chiffreAffairesTotalTrend,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Filled.ShoppingCart,
                        iconTint = DsColors.Primary,
                        iconBg = DsColors.PrimaryLight,
                        value = formatCurrency(data.panierMoyen.roundToInt()),
                        label = "Panier moyen",
                        trend = data.panierMoyenTrend,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                    StatCard(
                        icon = Icons.Filled.BarChart,
                        iconTint = DsColors.Primary,
                        iconBg = DsColors.PrimaryLight,
                        value = formatCurrency(data.chiffreAffairesMoyenParJour.roundToInt()),
                        label = "CA moyen / jour",
                        trend = data.chiffreAffairesMoyenParJourTrend,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Filled.Receipt,
                        iconTint = DsColors.Warning,
                        iconBg = DsColors.WarningLight,
                        value = data.nombreTickets.toString(),
                        label = "Nombre de tickets",
                        trend = data.nombreTicketsTrend,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "evolution") {
            SalesEvolutionChart(
                points = data.salesEvolution,
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
        }

        item(key = "sources") {
            SourcesVentesCard(
                sources = data.sourcesVentes,
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
        }
        item(key = "wilaya") {
            // في RapportVentesContent، بدل النداء القديم لـ RepartitionWilayaCard:
            RepartitionWilayaCard(
                title = "Répartition par Wilaya",
                wilayas = data.wilayaBreakdown,
                valueLabel = { formatCurrency(it.amount.roundToInt()) },
                onWilayaClick = onWilayaClick,
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
        }

        item(key = "top_secteurs") {
            TopSecteursCard(
                secteurs = data.topSecteurs,
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
        }
    }
}





@Composable
private fun RapportVentesEmptyState(
    selectedPeriod: ReportPeriod,
    periodeLabel: String,
    onPeriodSelected: (ReportPeriod) -> Unit,
    onCustomRangeSelected: (java.time.LocalDate, java.time.LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        VentesPeriodSection(
            selectedPeriod = selectedPeriod, periodeLabel = periodeLabel,
            onPeriodSelected = onPeriodSelected, onCustomRangeSelected = onCustomRangeSelected,
            modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(DsSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text("Aucune vente enregistrée pour cette période", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
        }
    }
}