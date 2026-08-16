package com.distrigo.app.ui.screens.rapport

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.ventes.VenteDetailScreen
import kotlin.math.roundToInt

@Composable
fun RapportClientsTab(
    modifier: Modifier = Modifier,
    onOpenClientDetail: (Int) -> Unit = {},   // محتفَظ به لأجل سلسلة MainActivity القديمة — غير مستخدَم حاليًا في هذا الملف
    viewModel: RapportClientsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val wilayaBreakdown by viewModel.wilayaBreakdown.collectAsState()
    val clientInvoices by viewModel.clientInvoices.collectAsState()
    val selectedVente by viewModel.selectedVente.collectAsState()
    var drillDown by remember { mutableStateOf<ClientsDrillDown>(ClientsDrillDown.None) }

    BackHandler(enabled = drillDown != ClientsDrillDown.None) {
        drillDown = when (val d = drillDown) {
            is ClientsDrillDown.Secteurs -> ClientsDrillDown.Communes(d.wilaya)
            is ClientsDrillDown.Communes -> ClientsDrillDown.None
            ClientsDrillDown.AllClients -> ClientsDrillDown.None
            is ClientsDrillDown.Invoices -> ClientsDrillDown.None
            is ClientsDrillDown.VenteDetail -> ClientsDrillDown.Invoices(d.clientId, d.clientName)
            ClientsDrillDown.None -> ClientsDrillDown.None
        }
    }

    when (val state = uiState) {
        is RapportClientsUiState.Loading -> { /* TODO */ }
        is RapportClientsUiState.Content -> {
            when (val d = drillDown) {
                is ClientsDrillDown.Communes -> {
                    GeoDrillDownScreen(
                        title = d.wilaya.name,
                        items = d.wilaya.communes,
                        valueLabel = { "${it.amount.roundToInt()} clients" },
                        onBack = { drillDown = ClientsDrillDown.None },
                        onItemClick = { commune -> drillDown = ClientsDrillDown.Secteurs(d.wilaya, commune) },
                        modifier = modifier
                    )
                }
                is ClientsDrillDown.Secteurs -> {
                    GeoDrillDownScreen(
                        title = d.commune.name,
                        items = d.commune.secteurs,
                        valueLabel = { "${it.amount.roundToInt()} clients" },
                        onBack = { drillDown = ClientsDrillDown.Communes(d.wilaya) },
                        onItemClick = null,
                        modifier = modifier
                    )
                }
                is ClientsDrillDown.Invoices -> {
                    LaunchedEffect(d.clientId) { viewModel.loadClientInvoices(d.clientId) }
                    ClientInvoicesScreen(
                        clientName = d.clientName,
                        invoices = clientInvoices,
                        onBack = { drillDown = ClientsDrillDown.None },
                        onViewReceipt = { venteId ->
                            viewModel.loadVenteDetail(venteId)
                            drillDown = ClientsDrillDown.VenteDetail(venteId, d.clientId, d.clientName)
                        },
                        modifier = modifier
                    )
                }
                is ClientsDrillDown.VenteDetail -> {
                    val vente = selectedVente
                    if (vente != null && vente.id == d.venteId) {
                        VenteDetailScreen(
                            vente = vente,
                            onBack = {
                                viewModel.clearSelectedVente()
                                drillDown = ClientsDrillDown.Invoices(d.clientId, d.clientName)
                            },
                            onDelivered = { viewModel.loadClientInvoices(d.clientId) },
                            onDeleted = {
                                viewModel.clearSelectedVente()
                                drillDown = ClientsDrillDown.Invoices(d.clientId, d.clientName)
                                viewModel.loadClientInvoices(d.clientId)
                            }
                        )
                    } else {
                        Box(
                            modifier = modifier.fillMaxSize().background(DsColors.SurfaceMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = DsColors.Primary)
                        }
                    }
                }
                ClientsDrillDown.AllClients -> {
                    TopClientsFullListScreen(
                        clients = state.data.topClients,
                        onBack = { drillDown = ClientsDrillDown.None },
                        onClientClick = { item -> drillDown = ClientsDrillDown.Invoices(item.clientId, item.name) },
                        modifier = modifier
                    )
                }
                ClientsDrillDown.None -> {
                    RapportClientsContent(
                        data = state.data,
                        selectedPeriod = selectedPeriod,
                        wilayaBreakdown = wilayaBreakdown,
                        onPeriodSelected = viewModel::onPeriodSelected,
                        onCustomRangeSelected = viewModel::onCustomRangeSelected,
                        onWilayaClick = { wilaya -> drillDown = ClientsDrillDown.Communes(wilaya) },
                        onVoirToutClick = { drillDown = ClientsDrillDown.AllClients },
                        onClientClick = { item -> drillDown = ClientsDrillDown.Invoices(item.clientId, item.name) },
                        modifier = modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun RapportClientsContent(
    data: RapportClientsData,
    selectedPeriod: ReportPeriod,
    wilayaBreakdown: List<com.distrigo.app.data.model.report.WilayaBreakdown>,
    onPeriodSelected: (ReportPeriod) -> Unit,
    onCustomRangeSelected: (java.time.LocalDate, java.time.LocalDate) -> Unit,
    onWilayaClick: (com.distrigo.app.data.model.report.WilayaBreakdown) -> Unit,
    onVoirToutClick: () -> Unit,
    onClientClick: (com.distrigo.app.data.model.report.ClientRankItem) -> Unit,
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
        item(key = "wilaya") {
            RepartitionWilayaCard(
                title = "Répartition des clients",
                wilayas = wilayaBreakdown,
                valueLabel = { "${it.amount.roundToInt()} clients" },
                onWilayaClick = onWilayaClick,
                headerIcon = Icons.Filled.LocationOn,
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
        }
        item(key = "top_clients") {
            TopClientsCard(
                clients = data.topClients,
                onVoirTout = onVoirToutClick,
                onClientClick = onClientClick,
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
        }
        // TODO Part D: Paiements
        // TODO Part E: Créances clients
    }
}