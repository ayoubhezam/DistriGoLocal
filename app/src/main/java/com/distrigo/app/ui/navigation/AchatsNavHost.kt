package com.distrigo.app.ui.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
                onAddOrder         = { navController.navigate(Screen.PurchaseFormGraph.createRoute()) },
                onEditOrder        = { orderId -> navController.navigate(Screen.PurchaseFormGraph.createRoute(orderId = orderId)) },
                onOrderClick       = { orderId -> navController.navigate(Screen.AchatsDetail.createRoute(orderId)) }
            )
        }

        purchaseFormGraph(
            navController     = navController,
            graphRoute        = Screen.PurchaseFormGraph.route,
            viewModel         = { viewModel(remember(navController) { navController.getBackStackEntry(Screen.AchatsGraph.route) }) },
            productViewModel  = { viewModel() },
            supplierViewModel = { viewModel() },
            onBack  = { navController.popBackStack(Screen.PurchaseFormGraph.route, inclusive = true) },
            onSaved = { navController.popBackStack(Screen.PurchaseFormGraph.route, inclusive = true) }
        )

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
                    onEdit     = { navController.navigate(Screen.PurchaseFormGraph.createRoute(orderId = orderId)) },
                    onReceived = { navController.popBackStack() }
                )
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
    }
}