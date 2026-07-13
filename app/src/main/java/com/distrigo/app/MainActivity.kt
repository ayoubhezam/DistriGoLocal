package com.distrigo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import com.distrigo.app.ui.clients.ClientsScreen
import com.distrigo.app.ui.common.PlaceholderScreen
import com.distrigo.app.ui.dashboard.DashboardScreen
import com.distrigo.app.ui.more.MoreScreen
import com.distrigo.app.ui.products.ProductsScreen
import com.distrigo.app.ui.purchases.PurchasesScreen
import com.distrigo.app.ui.suppliers.SuppliersScreen
import com.distrigo.app.ui.tournees.TourneesHubScreen
import com.distrigo.app.ui.ventes.VentesScreen
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    var selectedTab   by remember { mutableStateOf(0) }
                    var hideBottomBar by remember { mutableStateOf(false) }
                    var moreScreen    by remember { mutableStateOf<String?>(null) }

                    // ── "Plus" sub-screen navigation (takes over full screen) ──
                    moreScreen?.let { screen ->
                        BackHandler(enabled = !hideBottomBar) {
                            moreScreen = null
                        }
                        when (screen) {
                            "depot_vente"  -> VentesScreen(onFullScreenChange = { hideBottomBar = it })
                            "clients"      -> ClientsScreen(onFullScreenChange = { hideBottomBar = it })
                            "fournisseurs" -> SuppliersScreen(onFullScreenChange = { hideBottomBar = it })
                            "rapports" -> PlaceholderScreen(
                                title  = "Rapports",
                                icon   = Icons.Default.BarChart,
                                onBack = { moreScreen = null }
                            )
                            "stock_inventaire" -> PlaceholderScreen(
                                title  = "Stock & Inventaire",
                                icon   = Icons.Default.Inventory2,
                                onBack = { moreScreen = null }
                            )
                            "parametres" -> com.distrigo.app.ui.settings.ParametresScreen(
                                onBack = { moreScreen = null }
                            )
                        }
                        return@Box   // ← تنبيه: تغيّر من return@MaterialTheme إلى return@Box
                    }

                    Scaffold(
                        bottomBar = {
                            if (!hideBottomBar) {
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
                                            selected = selectedTab == 0,
                                            icon     = Icons.Default.Home,
                                            label    = "Dashboard",
                                            onClick  = { selectedTab = 0 }
                                        )
                                        BottomNavItem(
                                            selected = selectedTab == 1,
                                            icon     = Icons.Default.LocalShipping,
                                            label    = "Tournées",
                                            onClick  = { selectedTab = 1 }
                                        )
                                        BottomNavItem(
                                            selected = selectedTab == 2,
                                            icon     = Icons.Default.ShoppingCart,
                                            label    = "Produits",
                                            onClick  = { selectedTab = 2 }
                                        )
                                        BottomNavItem(
                                            selected = selectedTab == 3,
                                            icon     = Icons.Default.Receipt,
                                            label    = "Achats",
                                            onClick  = { selectedTab = 3 }
                                        )
                                        BottomNavItem(
                                            selected = selectedTab == 4,
                                            icon     = Icons.Default.Menu,
                                            label    = "Plus",
                                            onClick  = { selectedTab = 4 }
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        when (selectedTab) {
                            0 -> DashboardScreen()
                            1 -> TourneesHubScreen(onFullScreenChange = { hideBottomBar = it })
                            2 -> ProductsScreen(onFullScreenChange = { hideBottomBar = it })
                            3 -> PurchasesScreen(onFullScreenChange = { hideBottomBar = it })
                            4 -> MoreScreen(onNavigate = { route -> moreScreen = route })
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
}