package com.distrigo.app.ui.screens.rapport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
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

/**
 * محتوى تبويب "Rapport produit". لا يعتمد على فترة زمنية بل على اختيار منتج.
 * حاليًا: منتج تجريبي محدد افتراضيًا لعرض التصميم كاملًا (sampleProduitData).
 */
@Composable
fun RapportProduitTab(modifier: Modifier = Modifier) {
    val hasSelectedProduit = remember { mutableStateOf(true) }
    val uiState: RapportProduitUiState = remember(hasSelectedProduit.value) {
        if (hasSelectedProduit.value) {
            RapportProduitUiState.Content(sampleProduitData())
        } else {
            RapportProduitUiState.NoProduitSelected
        }
    }

    when (uiState) {
        is RapportProduitUiState.Loading -> {
            // TODO: حالة تحميل
        }
        is RapportProduitUiState.NoProduitSelected -> {
            RapportProduitEmptyState(
                onSearchBarClick = { hasSelectedProduit.value = true },
                modifier = modifier
            )
        }
        is RapportProduitUiState.Content -> {
            RapportProduitContent(
                data = uiState.data,
                onClearSelection = { hasSelectedProduit.value = false },
                onSearchBarClick = { /* TODO: فتح قائمة بحث المنتجات */ },
                onPrintClick = { /* TODO: ربط بنظام الطباعة عند بناء طبقة البيانات */ },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun RapportProduitContent(
    data: RapportProduitData,
    onClearSelection: () -> Unit,
    onSearchBarClick: () -> Unit,
    onPrintClick: () -> Unit,
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
            ClientSearchBar(
                selectedClientName = data.produitName,
                onSearchBarClick = onSearchBarClick,
                onClearSelection = onClearSelection,
                placeholder = "Rechercher un produit...",
                modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
            )
        }

        item(key = "profile") {
            ProductProfileHeaderCard(
                productName = data.produitName,
                categoryLabel = data.categoryLabel,
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
        }

        item(key = "primary_metrics") {
            Row(
                modifier = Modifier.padding(horizontal = DsSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                StatCard(
                    icon = Icons.Filled.Inventory2,
                    iconTint = DsColors.Primary,
                    iconBg = DsColors.PrimaryLight,
                    value = data.quantiteVendueLabel,
                    label = "Quantité vendue",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Filled.AccountBalanceWallet,
                    iconTint = DsColors.Primary,
                    iconBg = DsColors.PrimaryLight,
                    value = formatCurrency(data.chiffreAffairesGenere),
                    label = "Chiffre d'affaires généré",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "clients_count") {
            StatCard(
                icon = Icons.Filled.Groups,
                iconTint = DsColors.TagWholesale.first,
                iconBg = DsColors.TagWholesale.first.copy(alpha = 0.1f),
                value = data.nombreClientsAcheteurs.toString(),
                label = "Clients ayant acheté ce produit",
                modifier = Modifier
                    .padding(horizontal = DsSpacing.lg)
                    .fillMaxWidth()
            )
        }

        item(key = "meilleurs_clients") {
            Column(
                modifier = Modifier.padding(horizontal = DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                SectionHeader("Meilleurs clients pour ce produit")
                RankedClientsCard(clients = data.meilleursClients)
            }
        }

        item(key = "evolution") {
            Column(
                modifier = Modifier.padding(horizontal = DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                SectionHeader("Évolution mensuelle des ventes")
                MonthlyEvolutionChart(
                    monthlyPurchases = data.monthlyPurchases,
                    trendLabel = data.evolutionTrendLabel,
                    trendIsUp = data.evolutionTrendIsUp
                )
            }
        }

        item(key = "print") {
            PrintClientReportButton(
                onClick = onPrintClick,
                label = "Imprimer le rapport produit",
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
        }
    }
}

@Composable
private fun RapportProduitEmptyState(
    onSearchBarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        ClientSearchBar(
            selectedClientName = null,
            onSearchBarClick = onSearchBarClick,
            onClearSelection = {},
            placeholder = "Rechercher un produit...",
            modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(DsSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(88.dp).background(DsColors.SurfaceSunken, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.height(DsSpacing.xl))
            Text(
                text = "Sélectionnez un produit pour voir son rapport",
                fontSize = DsTextSize.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = DsColors.TextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = "Recherchez par nom ou référence",
                fontSize = DsTextSize.bodySmall,
                color = DsColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private fun sampleProduitData() = RapportProduitData(
    produitId = 1,
    produitName = "Huile Elio 5L",
    categoryLabel = "Huiles — Bidon 5L",
    quantiteVendueLabel = "1 240 unités",
    chiffreAffairesGenere = 148800,
    nombreClientsAcheteurs = 57,
    meilleursClients = listOf(
        RankedClient(1, "Épicerie Al Amal", "142 unités", isTopRank = true),
        RankedClient(2, "Superette Bounoua", "98 unités", isTopRank = false),
        RankedClient(3, "Alimentation Générale Kaci", "76 unités", isTopRank = false),
        RankedClient(4, "Dépôt Frères Meziane", "61 unités", isTopRank = false)
    ),
    monthlyPurchases = listOf(
        MonthlyPurchase("Fév", 165), MonthlyPurchase("Mars", 178), MonthlyPurchase("Avril", 190),
        MonthlyPurchase("Mai", 205), MonthlyPurchase("Juin", 240), MonthlyPurchase("Juillet", 214)
    ),
    evolutionTrendLabel = "Meilleur mois : Juin",
    evolutionTrendIsUp = true
)