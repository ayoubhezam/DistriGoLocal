package com.distrigo.app.ui.tournees

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsTopBarSize
import com.distrigo.app.ui.designsystem.DsTopBarRootActions
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.navigation.Screen
import com.distrigo.app.ui.navigation.navEnterTransition
import com.distrigo.app.ui.navigation.navExitTransition
import com.distrigo.app.ui.navigation.navPopEnterTransition
import com.distrigo.app.ui.navigation.navPopExitTransition
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
@Composable
fun TourneesHubScreen(
    tourneeViewModel   : TourneeViewModel = hiltViewModel(),
    onFullScreenChange : (Boolean) -> Unit = {},
    onOpenMenu           : (() -> Unit)? = null,
    onNotificationsClick : () -> Unit = {},
    onProfileClick       : () -> Unit = {},

    onOpenClientDetail : (Int) -> Unit = {}   // ← جديد
) {
    val navController = rememberNavController()

    LaunchedEffect(Unit) { tourneeViewModel.loadTournees() }
    val tournees by tourneeViewModel.tournees.collectAsState()

    NavHost(
        navController      = navController,
        startDestination   = Screen.VentesHubMenu.route,
        route              = Screen.VentesHubGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.VentesHubDepotVente.route) {
            com.distrigo.app.ui.navigation.VentesNavHost(
                onFullScreenChange = onFullScreenChange,
                onBack             = { navController.popBackStack() }
            )
        }

        composable(Screen.VentesHubTournees.route) {
            com.distrigo.app.ui.navigation.TourneesNavHost(
                onFullScreenChange = onFullScreenChange,
                onNavigateToChargement = { navController.navigate(Screen.VentesHubStockCamion.route) },
                onBack                 = { navController.popBackStack() }
            )
        }

        composable(Screen.VentesHubStockCamion.route) {
            StockCamionScreen(
                onBack             = { navController.popBackStack() },
                onFullScreenChange = onFullScreenChange
            )
        }

        composable(Screen.VentesHubRapports.route) {
            com.distrigo.app.ui.screens.rapport.RapportTourneesScreen(
                onBack = { navController.popBackStack() },
                onOpenClientDetail = onOpenClientDetail   // ← جديد
            )
        }

        composable(Screen.VentesHubMenu.route) {
            val activeTournees = tournees.count { it.status == "ouverte" }
            val closedTournees = tournees.count { it.status == "fermée" }
            val totalRevenue   = tournees.sumOf { it.total_ventes ?: 0.0 }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DsColors.Surface)
            ) {
                // The shipping badge used to ride in the leading slot as the hub's identity cue.
                // The menu is a navigation control and outranks it, and the other three tab roots
                // carry no badge either, so the four now open identically.
                //
                // The subtitle went with it: the menu plus two trailing controls cost enough title
                // width that "Gérez vos ventes et vos opérations" ellipsized, and no other tab root
                // carries one.
                DsTopAppBar(
                    title   = "Ventes",
                    leading = onOpenMenu?.let { DsTopBarLeading.Menu(it) } ?: DsTopBarLeading.None,
                    size    = DsTopBarSize.Large
                ) {
                    DsTopBarRootActions(
                        onNotificationsClick = onNotificationsClick,
                        onProfileClick       = onProfileClick
                    )
                }

                // The screen-wide inset moved off the root so the bar can run edge to edge; the
                // scrolling body carries it instead.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(DsSpacing.lg)
                ) {
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
                            icon        = Icons.Default.Storefront,
                            iconBg      = DsColors.WarningLight,
                            iconTint    = DsColors.Warning,
                            title       = "Dépôt Vente",
                            subtitle    = "Ventes depuis le dépôt",
                            onClick     = { navController.navigate(Screen.VentesHubDepotVente.route) }
                        )
                        HubNavCard(
                            icon        = Icons.Default.LocalShipping,
                            iconBg      = DsColors.PrimaryLight,
                            iconTint    = DsColors.Primary,
                            title       = "Tournées",
                            subtitle    = "Voir et gérer vos tournées actives et fermées",
                            onClick     = { navController.navigate(Screen.VentesHubTournees.route) }
                        )
                        HubNavCard(
                            icon        = Icons.Default.Inventory2,
                            iconBg      = DsColors.SuccessLight,
                            iconTint    = DsColors.Success,
                            title       = "Stock Camion",
                            subtitle    = "Consulter le stock et les produits disponibles dans les camions",
                            onClick     = { navController.navigate(Screen.VentesHubStockCamion.route) }
                        )
                        HubNavCard(
                            icon        = Icons.Default.PieChart,
                            iconBg      = DsColors.PrimaryLight,
                            iconTint    = DsColors.Primary,
                            title       = "Rapports",
                            subtitle    = "Analysez vos performances de vente",
                            onClick     = { navController.navigate(Screen.VentesHubRapports.route) }
                        )
                    }
                }
            }
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
