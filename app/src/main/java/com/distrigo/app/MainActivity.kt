package com.distrigo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.distrigo.app.ui.clients.ClientsScreen
import com.distrigo.app.ui.common.PlaceholderScreen
import com.distrigo.app.ui.dashboard.DashboardScreen
import com.distrigo.app.ui.more.MoreScreen
import com.distrigo.app.ui.navigation.Screen
import com.distrigo.app.ui.products.ProductsScreen
import com.distrigo.app.ui.purchases.PurchasesScreen
import com.distrigo.app.ui.suppliers.SuppliersScreen
import com.distrigo.app.ui.tournees.TourneesHubScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.distrigo.app.data.geo.GeoRepository.init(this)
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    var hideBottomBar by remember { mutableStateOf(false) }
                    val navController = rememberNavController()
                    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                    val isTabRoute = currentRoute == Screen.TabDashboard.route ||
                        currentRoute == Screen.TabVentes.route ||
                        currentRoute == Screen.TabProduits.route ||
                        currentRoute == Screen.TabAchats.route

                    // ── Bottom-tab switch: standard "multiple back stacks" pattern —
                    // pop to the graph's start destination (saving each tab's state) before
                    // navigating, then restore the target tab's saved state if it has any. ──
                    fun navigateToTab(route: String) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    Scaffold(
                        bottomBar = {
                            if (isTabRoute && !hideBottomBar) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Transparent)
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(28.dp)),
                                    color         = Color.White,
                                    shadowElevation = 10.dp,
                                    tonalElevation  = 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        BottomNavItem(
                                            selected = currentRoute == Screen.TabDashboard.route,
                                            icon     = Icons.Default.Home,
                                            label    = "Dashboard",
                                            onClick  = { navigateToTab(Screen.TabDashboard.route) }
                                        )
                                        BottomNavItem(
                                            selected = currentRoute == Screen.TabVentes.route,
                                            icon     = Icons.Default.LocalShipping,
                                            label    = "Ventes",
                                            onClick  = { navigateToTab(Screen.TabVentes.route) }
                                        )
                                        BottomNavItem(
                                            selected = currentRoute == Screen.TabProduits.route,
                                            icon     = Icons.Default.ShoppingCart,
                                            label    = "Produits",
                                            onClick  = { navigateToTab(Screen.TabProduits.route) }
                                        )
                                        BottomNavItem(
                                            selected = currentRoute == Screen.TabAchats.route,
                                            icon     = Icons.Default.Receipt,
                                            label    = "Achats",
                                            onClick  = { navigateToTab(Screen.TabAchats.route) }
                                        )
                                        BottomNavItem(
                                            selected = false,
                                            icon     = Icons.Default.Menu,
                                            label    = "Plus",
                                            onClick  = { navController.navigate(Screen.Plus.route) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        NavHost(
                            navController    = navController,
                            startDestination = Screen.TabDashboard.route
                        ) {
                            // ── Bottom-tab destinations: real siblings of Plus in the same graph ──
                            composable(Screen.TabDashboard.route) {
                                com.distrigo.app.ui.navigation.DashboardNavHost()
                            }

                            composable(Screen.TabVentes.route) {
                                TourneesHubScreen(
                                    onFullScreenChange = { hideBottomBar = it },
                                    onOpenClientDetail = { clientId ->
                                        navController.navigate(Screen.PlusClients.createRoute(clientId))
                                    }
                                )
                            }

                            composable(Screen.TabProduits.route) {
                                com.distrigo.app.ui.navigation.ProduitsNavHost(onFullScreenChange = { hideBottomBar = it })
                            }

                            composable(Screen.TabAchats.route) {
                                com.distrigo.app.ui.navigation.AchatsNavHost(onFullScreenChange = { hideBottomBar = it })
                            }

                            // ── Plus: a real navigation destination, not an overlay ──
                            composable(Screen.Plus.route) {
                                MoreScreen(onNavigate = { route ->
                                    when (route) {
                                        "clients"      -> navController.navigate(Screen.PlusClients.createRoute())
                                        "fournisseurs" -> navController.navigate(Screen.PlusFournisseurs.route)
                                        "charges"      -> navController.navigate(Screen.PlusCharges.route)
                                        "pertes"       -> navController.navigate(Screen.PlusPertes.route)
                                        "inventaire"   -> navController.navigate(Screen.PlusInventaire.route)
                                        "rapports"     -> navController.navigate(Screen.PlusRapports.route)
                                        "parametres"   -> navController.navigate(Screen.PlusParametres.route)
                                    }
                                })
                            }

                            composable(
                                route     = Screen.PlusClients.route,
                                arguments = listOf(navArgument("clientId") { type = NavType.IntType; defaultValue = -1 })
                            ) { entry ->
                                val clientId = entry.arguments!!.getInt("clientId").takeIf { it != -1 }
                                com.distrigo.app.ui.navigation.ClientsNavHost(
                                    preSelectedClientId = clientId,
                                    onFullScreenChange  = { hideBottomBar = it },
                                    onBack              = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.PlusFournisseurs.route) {
                                com.distrigo.app.ui.navigation.SuppliersNavHost(onFullScreenChange = { hideBottomBar = it })
                            }

                            composable(Screen.PlusCharges.route) {
                                com.distrigo.app.ui.navigation.ChargesNavHost(onFullScreenChange = { hideBottomBar = it })
                            }

                            composable(Screen.PlusPertes.route) {
                                com.distrigo.app.ui.navigation.PertesNavHost(onFullScreenChange = { hideBottomBar = it })
                            }

                            composable(Screen.PlusInventaire.route) {
                                com.distrigo.app.ui.navigation.InventoryNavHost(
                                    onBack              = { navController.popBackStack() },
                                    onFullScreenChange  = { hideBottomBar = it }
                                )
                            }

                            composable(Screen.PlusRapports.route) {
                                PlaceholderScreen(
                                    title  = "Rapports",
                                    icon   = Icons.Default.BarChart,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.PlusParametres.route) {
                                com.distrigo.app.ui.settings.ParametresScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun BottomNavItem(
    selected : Boolean,
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    label    : String,
    onClick  : () -> Unit
) {
    val selectedColor   = Color(0xFF2196F3)
    val unselectedColor = Color(0xFFB0B3B8)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { onClick() }
            .padding(horizontal = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint     = if (selected) selectedColor else unselectedColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize   = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color      = if (selected) selectedColor else unselectedColor
        )
    }
}
