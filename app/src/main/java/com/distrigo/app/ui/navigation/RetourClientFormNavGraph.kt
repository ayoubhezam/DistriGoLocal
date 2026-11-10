package com.distrigo.app.ui.navigation

import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.distrigo.app.ui.retours.NewRetourCartScreen
import com.distrigo.app.ui.retours.NewRetourClientViewModel
import com.distrigo.app.ui.retours.NewRetourListScreen
import com.distrigo.app.ui.retours.NewRetourSaleScreen
import com.distrigo.app.ui.retours.NewRetourSummaryScreen

/**
 * A client's new return: the sale it is made from (FIN-02), that sale's lines, a centred dialog per product, the
 * selection and a dated summary where the goods' place is chosen (STK-02) — the Inventaire's and the pertes' flow. Its ViewModel belongs to this graph, keyed by
 * the client in its route, so each return starts empty. [onDone] leaves it, saved or not.
 */
fun NavGraphBuilder.retourClientFormGraph(navController: NavHostController, graphRoute: String, onDone: () -> Unit) {
    navigation(
        startDestination = Screen.ClientsRetourFormSale.route,
        route = graphRoute,
        arguments = listOf(navArgument("clientId") { type = NavType.IntType })
    ) {
        composable(Screen.ClientsRetourFormSale.route) { entry ->
            val graph = remember(entry) { navController.getBackStackEntry(graphRoute) }
            NewRetourSaleScreen(
                viewModel = hiltViewModel<NewRetourClientViewModel>(graph),
                onBack = onDone,
                // Once: a double tap on a sale would stack its products twice
                onPicked = {
                    if (navController.currentDestination?.route == Screen.ClientsRetourFormSale.route) {
                        navController.navigate(Screen.ClientsRetourFormProducts.route)
                    }
                }
            )
        }
        composable(Screen.ClientsRetourFormProducts.route) { entry ->
            val graph = remember(entry) { navController.getBackStackEntry(graphRoute) }
            NewRetourListScreen(
                viewModel = hiltViewModel<NewRetourClientViewModel>(graph),
                // Back to the sales: another one may be picked
                onBack = { navController.popBackStack() },
                onOpenCart = { navController.navigate(Screen.ClientsRetourFormCart.route) }
            )
        }
        composable(Screen.ClientsRetourFormCart.route) { entry ->
            val graph = remember(entry) { navController.getBackStackEntry(graphRoute) }
            NewRetourCartScreen(
                viewModel = hiltViewModel<NewRetourClientViewModel>(graph),
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(Screen.ClientsRetourFormSummary.route) }
            )
        }
        composable(Screen.ClientsRetourFormSummary.route) { entry ->
            val graph = remember(entry) { navController.getBackStackEntry(graphRoute) }
            NewRetourSummaryScreen(
                viewModel = hiltViewModel<NewRetourClientViewModel>(graph),
                onBack = { navController.popBackStack() },
                onDone = onDone
            )
        }
    }
}
