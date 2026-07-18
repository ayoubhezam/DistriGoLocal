package com.distrigo.app.ui.screens.rapport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing

/**
 * محتوى تبويب "Rapport des ventes".
 * تقرير رسمي بلا Insight/Trend/Drill-down — بيانات ثابتة تجريبية حاليًا (sampleRapportVentesData).
 */
@Composable
fun RapportVentesTab(modifier: Modifier = Modifier) {
    val selectedPeriod = remember { mutableStateOf(ReportPeriod.SEMAINE) }
    val uiState: RapportVentesUiState = remember {
        RapportVentesUiState.Content(sampleRapportVentesData())
    }

    when (uiState) {
        is RapportVentesUiState.Loading -> {
            // TODO: حالة تحميل
        }
        is RapportVentesUiState.Empty -> {
            RapportVentesEmptyState(
                selectedPeriod = selectedPeriod.value,
                onPeriodSelected = { selectedPeriod.value = it },
                modifier = modifier
            )
        }
        is RapportVentesUiState.Content -> {
            RapportVentesContent(
                data = uiState.data,
                selectedPeriod = selectedPeriod.value,
                onPeriodSelected = { selectedPeriod.value = it },
                onPrintClick = { /* TODO: ربط بنظام الطباعة عند بناء طبقة البيانات */ },
                onExportPdfClick = { /* TODO */ },
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
    onPrintClick: () -> Unit,
    onExportPdfClick: () -> Unit,
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

        item(key = "header") {
            ReportHeaderCard(
                periodeLabel = data.periodeLabel,
                genereLeLabel = data.genereLeLabel,
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
        }

        item(key = "primary_metrics") {
            Row(
                modifier = Modifier.padding(horizontal = DsSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                StatCard(
                    icon = Icons.Filled.AccountBalanceWallet,
                    iconTint = DsColors.Primary,
                    iconBg = DsColors.PrimaryLight,
                    value = formatCurrency(data.chiffreAffaires),
                    label = "Chiffre d'affaires",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Filled.ShoppingCart,
                    iconTint = DsColors.Primary,
                    iconBg = DsColors.PrimaryLight,
                    value = formatCurrency(data.panierMoyen),
                    label = "Panier moyen",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "activite_section") {
            Column(
                modifier = Modifier.padding(horizontal = DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                SectionHeader("Activité commerciale")
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                    StatCard(
                        icon = Icons.Filled.Receipt,
                        iconTint = DsColors.Primary,
                        iconBg = DsColors.PrimaryLight,
                        value = data.nombreTickets.toString(),
                        label = "Nombre de tickets",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Filled.Inventory2,
                        iconTint = DsColors.Primary,
                        iconBg = DsColors.PrimaryLight,
                        value = data.produitsVendus.toString(),
                        label = "Produits vendus",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "couverture_section") {
            Column(
                modifier = Modifier.padding(horizontal = DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                SectionHeader("Couverture clients")
                StatCard(
                    icon = Icons.Filled.Groups,
                    iconTint = DsColors.Primary,
                    iconBg = DsColors.PrimaryLight,
                    value = data.clientsVisites.toString(),
                    label = "Clients visités",
                    modifier = Modifier.fillMaxWidth()
                )
                ClientsCoverageBar(
                    clientsAvecVente = data.clientsAvecVente,
                    clientsSansVente = data.clientsSansVente,
                    avecFraction = data.avecVenteFraction
                )
            }
        }

        item(key = "export_actions") {
            ExportActionsRow(
                onPrintClick = onPrintClick,
                onExportPdfClick = onExportPdfClick,
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
        }
    }
}

@Composable
private fun RapportVentesEmptyState(
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
            modifier = Modifier.fillMaxSize().padding(DsSpacing.xxxl),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            androidx.compose.material3.Text(
                text = "Aucune vente enregistrée pour cette période",
                fontSize = com.distrigo.app.ui.designsystem.DsTextSize.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = DsColors.TextPrimary
            )
        }
        ExportActionsRow(onPrintClick = {}, onExportPdfClick = {}, enabled = false, modifier = Modifier.padding(DsSpacing.lg))
    }
}

private fun sampleRapportVentesData() = RapportVentesData(
    periodeLabel = "09 — 15 Juillet 2026",
    genereLeLabel = "Généré le 15/07/2026",
    chiffreAffaires = 18750,
    panierMoyen = 493,
    nombreTickets = 38,
    produitsVendus = 214,
    clientsVisites = 42,
    clientsAvecVente = 31,
    clientsSansVente = 11
)