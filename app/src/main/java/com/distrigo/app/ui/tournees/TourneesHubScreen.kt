package com.distrigo.app.ui.tournees

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

@Composable
fun TourneesHubScreen(
    tourneeViewModel   : TourneeViewModel = viewModel(),
    onFullScreenChange : (Boolean) -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf("hub") }

    LaunchedEffect(Unit) { tourneeViewModel.loadTournees() }
    val tournees by tourneeViewModel.tournees.collectAsState()

    if (currentScreen == "tournees") {
        BackHandler { currentScreen = "hub" }
        TourneesScreen(onFullScreenChange = onFullScreenChange)
        return
    }

    if (currentScreen == "stock_camion") {
        BackHandler { currentScreen = "hub" }
        StockCamionScreen(onBack = { currentScreen = "hub" })
        return
    }

    if (currentScreen == "chargement") {
        BackHandler { currentScreen = "hub" }
        com.distrigo.app.ui.chargements.ChargementSessionsScreen(onFullScreenChange = onFullScreenChange)
        return
    }

    val activeTournees = tournees.count { it.status == "ouverte" }
    val closedTournees = tournees.count { it.status == "fermée" }
    val totalRevenue   = tournees.sumOf { it.total_ventes ?: 0.0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
            .padding(DsSpacing.lg)
    ) {
        // ── Header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier         = Modifier.size(48.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(DsSpacing.md))
            Column {
                Text("Tournées", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Text("Gérez vos tournées et vos opérations", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
        }

        Spacer(Modifier.height(DsSpacing.lg))

        // ── Stats banner ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DsShapes.large)
                .background(DsColors.Primary)
                .padding(DsSpacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HubStatColumn(
                icon  = Icons.Default.LocalShipping,
                value = "$activeTournees",
                label = "Tournées actives"
            )
            HubStatColumn(
                icon  = Icons.Default.CheckCircle,
                value = "$closedTournees",
                label = "Tournées fermées"
            )
            HubStatColumn(
                icon  = Icons.Default.TrendingUp,
                value = "${"%.0f".format(totalRevenue)} DA",
                label = "Chiffre d'affaires"
            )
        }

        Text(
            "Gestion",
            fontSize   = DsTextSize.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color      = DsColors.TextSecondary,
            modifier   = Modifier.padding(top = DsSpacing.lg, bottom = DsSpacing.sm)
        )

        Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            HubNavCard(
                icon        = Icons.Default.LocalShipping,
                iconBg      = DsColors.PrimaryLight,
                iconTint    = DsColors.Primary,
                title       = "Tournées",
                subtitle    = "Voir et gérer vos tournées actives et fermées",
                onClick     = { currentScreen = "tournees" }
            )
            HubNavCard(
                icon        = Icons.Default.Inventory2,
                iconBg      = DsColors.SuccessLight,
                iconTint    = DsColors.Success,
                title       = "Stock Camion",
                subtitle    = "Consulter le stock et les produits disponibles dans les camions",
                onClick     = { currentScreen = "stock_camion" }
            )
            HubNavCard(
                icon        = Icons.Default.SwapVert,
                iconBg      = DsColors.WarningLight,
                iconTint    = DsColors.Warning,
                title       = "Chargement",
                subtitle    = "Gérer le chargement et déchargement des camions",
                onClick     = { currentScreen = "chargement" }
            )
            HubNavCard(
                icon        = Icons.Default.PieChart,
                iconBg      = DsColors.SurfaceMuted,
                iconTint    = DsColors.TextTertiary,
                title       = "Rapport des tournées",
                subtitle    = "Bientôt disponible",
                onClick     = null
            )
        }
    }
}

@Composable
private fun HubStatColumn(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = DsTextSize.caption, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun HubNavCard(
    icon     : ImageVector,
    iconBg   : Color,
    iconTint : Color,
    title    : String,
    subtitle : String,
    onClick  : (() -> Unit)?
) {
    val enabled = onClick != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(DsSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier.size(44.dp).clip(DsShapes.medium).background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }

        Spacer(Modifier.width(DsSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize   = DsTextSize.body,
                fontWeight = FontWeight.Bold,
                color      = if (enabled) DsColors.TextPrimary else DsColors.TextTertiary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = DsTextSize.caption,
                color    = if (enabled) DsColors.TextSecondary else DsColors.TextTertiary
            )
        }

        if (enabled) {
            Spacer(Modifier.width(DsSpacing.sm))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}
