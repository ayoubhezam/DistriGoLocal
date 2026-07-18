package com.distrigo.app.ui.screens.rapport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material.icons.filled.Receipt
/**
 * محتوى تبويب "Rapport client". لا يعتمد على فترة زمنية بل على اختيار عميل.
 * حاليًا: عميل تجريبي محدد افتراضيًا لعرض التصميم كاملًا (sampleClientData)،
 * مع زر "×" لإظهار حالة "لا يوجد عميل محدد".
 */
@Composable
fun RapportClientTab(modifier: Modifier = Modifier) {
    val hasSelectedClient = remember { mutableStateOf(true) }
    val uiState: RapportClientUiState = remember(hasSelectedClient.value) {
        if (hasSelectedClient.value) {
            RapportClientUiState.Content(sampleClientData())
        } else {
            RapportClientUiState.NoClientSelected
        }
    }

    when (uiState) {
        is RapportClientUiState.Loading -> {
            // TODO: حالة تحميل
        }
        is RapportClientUiState.NoClientSelected -> {
            RapportClientEmptyState(
                onSearchBarClick = { hasSelectedClient.value = true },
                modifier = modifier
            )
        }
        is RapportClientUiState.Content -> {
            RapportClientContent(
                data = uiState.data,
                onClearSelection = { hasSelectedClient.value = false },
                onSearchBarClick = { /* TODO: فتح قائمة بحث العملاء */ },
                onPrintClick = { /* TODO: ربط بنظام الطباعة عند بناء طبقة البيانات */ },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun RapportClientContent(
    data: RapportClientData,
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
                selectedClientName = data.clientName,
                onSearchBarClick = onSearchBarClick,
                onClearSelection = onClearSelection,
                modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
            )
        }

        item(key = "profile") {
            ClientProfileHeaderCard(
                initials = data.clientInitials,
                name = data.clientName,
                category = data.category,
                address = data.address,
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
                    value = formatCurrency(data.totalAchats),
                    label = "Total des achats",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Filled.Receipt,
                    iconTint = DsColors.Primary,
                    iconBg = DsColors.PrimaryLight,
                    value = formatCurrency(data.panierMoyen),
                    label = "Panier moyen",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "secondary_metrics") {
            Row(
                modifier = Modifier.padding(horizontal = DsSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                StatCard(
                    icon = Icons.Filled.CalendarMonth,
                    iconTint = DsColors.TagWholesale.first,
                    iconBg = DsColors.TagWholesale.first.copy(alpha = 0.1f),
                    value = data.nombreVisites.toString(),
                    label = "Nombre de visites",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Filled.Schedule,
                    iconTint = DsColors.TagWholesale.first,
                    iconBg = DsColors.TagWholesale.first.copy(alpha = 0.1f),
                    value = data.derniereVisiteLabel,
                    label = "Dernière visite",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "top_products") {
            Column(
                modifier = Modifier.padding(horizontal = DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                SectionHeader("Produits les plus achetés")
                TopProductsCard(products = data.topProducts)
            }
        }

        item(key = "evolution") {
            Column(
                modifier = Modifier.padding(horizontal = DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                SectionHeader("Évolution mensuelle des achats")
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
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
        }
    }
}

@Composable
private fun RapportClientEmptyState(
    onSearchBarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        ClientSearchBar(
            selectedClientName = null,
            onSearchBarClick = onSearchBarClick,
            onClearSelection = {},
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
                text = "Sélectionnez un client pour voir son rapport",
                fontSize = DsTextSize.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = DsColors.TextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = "Recherchez par nom ou numéro de téléphone",
                fontSize = DsTextSize.bodySmall,
                color = DsColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private fun sampleClientData() = RapportClientData(
    clientId = 1,
    clientName = "Épicerie Al Amal",
    clientInitials = "EA",
    category = ClientCategoryTag(
        textColor = DsColors.TagWholesale.first,
        bgColor = DsColors.TagWholesale.second,
        label = "Gros"
    ),
    address = "Rue des Frères Boulahia, Bab El Oued",
    totalAchats = 187400,
    panierMoyen = 512,
    nombreVisites = 64,
    derniereVisiteLabel = "Il y a 3 jours",
    topProducts = listOf(
        TopProduct(1, "Huile Elio 5L", "142 unités", 100),
        TopProduct(2, "Sucre Cristal 50kg", "38 unités", 27),
        TopProduct(3, "Farine Extra 25kg", "31 unités", 22),
        TopProduct(4, "Lait UHT 1L (pack 12)", "27 unités", 19)
    ),
    monthlyPurchases = listOf(
        MonthlyPurchase("Fév", 22100), MonthlyPurchase("Mars", 19800), MonthlyPurchase("Avril", 28300),
        MonthlyPurchase("Mai", 24600), MonthlyPurchase("Juin", 31200), MonthlyPurchase("Juillet", 18750)
    ),
    evolutionTrendLabel = "En baisse sur les 2 derniers mois",
    evolutionTrendIsUp = false
)