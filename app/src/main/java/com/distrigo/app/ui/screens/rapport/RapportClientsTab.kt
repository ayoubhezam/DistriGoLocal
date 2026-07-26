package com.distrigo.app.ui.screens.rapport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing

@Composable
fun RapportClientsTab(
    modifier: Modifier = Modifier,
    viewModel: RapportClientsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()

    when (val state = uiState) {
        is RapportClientsUiState.Loading -> { /* TODO */ }
        is RapportClientsUiState.Content -> {
            RapportClientsContent(
                data = state.data,
                selectedPeriod = selectedPeriod,
                onPeriodSelected = viewModel::onPeriodSelected,
                onCustomRangeSelected = viewModel::onCustomRangeSelected,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun RapportClientsContent(
    data: RapportClientsData,
    selectedPeriod: ReportPeriod,
    onPeriodSelected: (ReportPeriod) -> Unit,
    onCustomRangeSelected: (java.time.LocalDate, java.time.LocalDate) -> Unit,
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
                    ClientKpiCard(
                        icon = Icons.Filled.Groups, iconTint = DsColors.Success, iconBg = DsColors.SuccessLight,
                        title = "Clients actifs", value = data.clientsActifs.toString(),
                        infoText = "Clients ayant acheté au moins une fois durant la période sélectionnée.",
                        modifier = Modifier.weight(1f)
                    )
                    ClientKpiCard(
                        icon = Icons.Filled.PersonAdd, iconTint = DsColors.Primary, iconBg = DsColors.PrimaryLight,
                        title = "Nouveaux clients", value = data.nouveauxClients.toString(),
                        infoText = "Clients ayant effectué leur premier achat durant la période sélectionnée.",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                    ClientKpiCard(
                        icon = Icons.Filled.PersonOff, iconTint = DsColors.Warning, iconBg = DsColors.WarningLight,
                        title = "Clients perdus (90 jours)", value = data.clientsPerdus.toString(),
                        infoText = "Clients n'ayant pas acheté depuis plus de 90 jours.",
                        modifier = Modifier.weight(1f)
                    )
                    ClientKpiCard(
                        icon = Icons.Filled.RemoveShoppingCart, iconTint = DsColors.Danger, iconBg = DsColors.DangerLight,
                        title = "Clients sans achat (30 jours)", value = data.clientsSansAchat.toString(),
                        infoText = "Clients n'ayant effectué aucun achat au cours des 30 derniers jours.",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        // TODO Part B: Répartition des clients
        // TODO Part C: Top 5 clients + Voir tout
        // TODO Part D: Paiements
        // TODO Part E: Créances clients
    }
}