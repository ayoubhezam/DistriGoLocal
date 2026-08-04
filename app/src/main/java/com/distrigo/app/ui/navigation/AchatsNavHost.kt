package com.distrigo.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.purchases.*

@Composable
fun AchatsNavHost(onFullScreenChange: (Boolean) -> Unit = {}) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        onFullScreenChange(currentRoute != null && currentRoute != Screen.AchatsHome.route)
    }

    NavHost(
        navController    = navController,
        startDestination = Screen.AchatsHome.route,
        route            = Screen.AchatsGraph.route
    ) {
        composable(Screen.AchatsHome.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.AchatsGraph.route) }
            val viewModel: PurchaseViewModel = viewModel(parentEntry)
            PurchasesScreen(
                viewModel          = viewModel,
                onFullScreenChange = onFullScreenChange,
                onAddOrder         = { navController.navigate(Screen.AchatsForm.createRoute()) },
                onEditOrder        = { orderId -> navController.navigate(Screen.AchatsForm.createRoute(orderId)) },
                onOrderClick       = { orderId -> navController.navigate(Screen.AchatsDetail.createRoute(orderId)) }
            )
        }

        composable(
            route     = Screen.AchatsForm.route,
            arguments = listOf(navArgument("orderId") { type = NavType.IntType; defaultValue = -1 })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.AchatsGraph.route) }
            val viewModel: PurchaseViewModel = viewModel(parentEntry)
            val orderId = entry.arguments!!.getInt("orderId").takeIf { it != -1 }
            val selectedOrder by viewModel.selectedOrder.collectAsState()

            LaunchedEffect(orderId) {
                if (orderId != null) viewModel.loadOrderDetail(orderId)
            }

            when {
                orderId == null -> PurchaseFormScreen(
                    order             = null,
                    purchaseViewModel = viewModel,
                    onBack            = { navController.popBackStack() },
                    onSaved           = { navController.popBackStack() }
                )
                selectedOrder?.id == orderId -> PurchaseFormScreen(
                    order             = selectedOrder,
                    purchaseViewModel = viewModel,
                    onBack            = { navController.popBackStack() },
                    onSaved           = { navController.popBackStack() }
                )
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
            }
        }

        composable(
            route     = Screen.AchatsDetail.route,
            arguments = listOf(navArgument("orderId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.AchatsGraph.route) }
            val viewModel: PurchaseViewModel = viewModel(parentEntry)
            val orderId = entry.arguments!!.getInt("orderId")
            val orders by viewModel.orders.collectAsState()
            val fallbackOrder = orders.find { it.id == orderId }

            LaunchedEffect(orderId) { viewModel.loadOrderDetail(orderId) }

            if (fallbackOrder != null) {
                PurchaseOrderDetailScreen(
                    order      = fallbackOrder,
                    viewModel  = viewModel,
                    onBack     = { navController.popBackStack() },
                    onEdit     = { navController.navigate(Screen.AchatsForm.createRoute(orderId)) },
                    onReceived = { navController.popBackStack() }
                )
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
    }
}