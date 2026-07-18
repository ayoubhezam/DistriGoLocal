package com.distrigo.app.ui.screens.rapport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
/**
 * محتوى تبويب "Tableau de bord".
 * بـ ViewModel حقيقي يجلب البيانات عبر UseCase من Room.
 */

@Composable
fun TableauDeBordTab(
    modifier: Modifier = Modifier,
    viewModel: TableauDeBordViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    val uiState by viewModel.uiState.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()

    when (val state = uiState) {
        is TableauDeBordUiState.Loading -> {
            // TODO: حالة تحميل (Shimmer/CircularProgressIndicator)
        }
        is TableauDeBordUiState.Empty -> {
            TableauDeBordEmptyState(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = viewModel::onPeriodSelected,
                modifier = modifier
            )
        }
        is TableauDeBordUiState.Content -> {
            TableauDeBordContent(
                data = state.data,
                selectedPeriod = selectedPeriod,
                onPeriodSelected = viewModel::onPeriodSelected,
                onSansVenteClick = { /* TODO */ },
                onAtRiskClientClick = { /* TODO */ },
                onInsightCtaClick = { /* TODO */ },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun TableauDeBordContent(
    data: TableauDeBordData,
    selectedPeriod: ReportPeriod,
    onPeriodSelected: (ReportPeriod) -> Unit,
    onSansVenteClick: () -> Unit,
    onAtRiskClientClick: (AtRiskClient) -> Unit,
    onInsightCtaClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DsColors.SurfaceMuted),
        contentPadding = PaddingValues(bottom = DsSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.xxl)
    ) {
        item {
            PeriodSelectorChips(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = onPeriodSelected,
                modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
            )
        }

        item(key = "insight") {
            InsightCard(
                message = data.insightMessage,
                ctaLabel = "Voir les clients concernés",
                onCtaClick = onInsightCtaClick,
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
        }

        item(key = "hero") {
            HeroKpiCard(data = data, modifier = Modifier.padding(horizontal = DsSpacing.lg))
        }

        item(key = "ventes_section") {
            Column(
                modifier = Modifier.padding(horizontal = DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                SectionHeader("Ventes")

                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                    StatCard(
                        icon = Icons.Filled.TrendingUp,
                        iconTint = DsColors.Primary,
                        iconBg = DsColors.PrimaryLight,
                        value = formatCurrency(data.ventesRealisees),
                        label = "Ventes réalisées",
                        trend = data.ventesTrend,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Filled.Receipt,
                        iconTint = DsColors.Primary,
                        iconBg = DsColors.PrimaryLight,
                        value = data.nombreTickets.toString(),
                        label = "Nombre de tickets",
                        trend = data.ticketsTrend,
                        modifier = Modifier.weight(1f)
                    )
                }

                StatCard(
                    icon = Icons.Filled.AttachMoney,
                    iconTint = DsColors.Primary,
                    iconBg = DsColors.PrimaryLight,
                    value = formatCurrency(data.caMoyenParTicket),
                    label = "Chiffre d'affaires moyen par ticket",
                    trend = data.caMoyenTrend,
                    modifier = Modifier.fillMaxWidth()
                )

                SalesTrendChart(bars = data.salesTrend)
            }
        }

        item(key = "visites_section") {
            Column(
                modifier = Modifier.padding(horizontal = DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                SectionHeader("Visites")

                StatCard(
                    icon = Icons.Filled.Groups,
                    iconTint = DsColors.Primary,
                    iconBg = DsColors.PrimaryLight,
                    value = data.clientsVisites.toString(),
                    label = "Clients visités",
                    trend = data.visitesTrend,
                    modifier = Modifier.fillMaxWidth()
                )

                VisitsBreakdownCard(
                    clientsAvecVente = data.clientsAvecVente,
                    clientsSansVente = data.clientsSansVente,
                    onSansVenteClick = onSansVenteClick
                )

                if (data.atRiskClients.isNotEmpty()) {
                    Text(
                        text = "Achètent habituellement, n'ont pas acheté cette période",
                        fontSize = DsTextSize.caption,
                        color = DsColors.TextTertiary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.atRiskClients.forEach { client ->
                            AtRiskClientRow(client = client, onClick = onAtRiskClientClick)
                        }
                    }
                }
            }
        }

        item(key = "performance_section") {
            Column(
                modifier = Modifier.padding(horizontal = DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                SectionHeader("Performance")

                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                    StatCard(
                        icon = Icons.Filled.Inventory2,
                        iconTint = DsColors.TagWholesale.first,
                        iconBg = DsColors.TagWholesale.first.copy(alpha = 0.1f),
                        value = data.produitsVendus.toString(),
                        label = "Produits vendus",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Filled.AltRoute,
                        iconTint = DsColors.TagWholesale.first,
                        iconBg = DsColors.TagWholesale.first.copy(alpha = 0.1f),
                        value = data.tourneesRealisees.toString(),
                        label = "Tournées réalisées",
                        modifier = Modifier.weight(1f)
                    )
                }

                StatCard(
                    icon = Icons.Filled.Schedule,
                    iconTint = DsColors.TagWholesale.first,
                    iconBg = DsColors.TagWholesale.first.copy(alpha = 0.1f),
                    value = formatMinutesAsDuration(data.tempsMoyenParTourneeMinutes),
                    label = "Temps moyen par tournée",
                    modifier = Modifier.fillMaxWidth()
                )

                BestClientCard(
                    clientName = data.meilleurClientNom,
                    amount = data.meilleurClientMontant,
                    icon = Icons.Filled.EmojiEvents
                )
            }
        }
    }
}

@Composable
private fun TableauDeBordEmptyState(
    selectedPeriod: ReportPeriod,
    onPeriodSelected: (ReportPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        PeriodSelectorChips(
            selectedPeriod = selectedPeriod,
            onPeriodSelected = onPeriodSelected,
            modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(DsSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(88.dp).background(DsColors.SurfaceSunken, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.BarChart, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.height(DsSpacing.xl))
            Text(
                text = "Aucune donnée pour cette période",
                fontSize = DsTextSize.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = DsColors.TextPrimary
            )
            Text(
                text = "Aucune tournée n'a été complétée sur cette période.",
                fontSize = DsTextSize.bodySmall,
                color = DsColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

