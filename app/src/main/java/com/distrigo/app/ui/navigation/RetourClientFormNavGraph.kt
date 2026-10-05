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
import com.distrigo.app.ui.retours.NewRetourSummaryScreen

/**
 * A client's new return: what was delivered to them, a centred dialog per product, the selection and a
 * dated summary — the Inventaire's and the pertes' flow. Its ViewModel belongs to this graph, keyed by
 * the client in its route, so each return starts empty. [onDone] leaves it, saved or not.
 */
fun NavGraphBuilder.retourClientFormGraph(navController: NavHostController, graphRoute: String, onDone: () -> Unit) {
    navigation(
        startDestination = Screen.ClientsRetourFormProducts.route,
        route = graphRoute,
        arguments = listOf(navArgument("clientId") { type = NavType.IntType })
    ) {
        composable(Screen.ClientsRetourFormProducts.route) { entry ->
            val graph = remember(entry) { navController.getBackStackEntry(graphRoute) }
            NewRetourListScreen(
                viewModel = hiltViewModel<NewRetourClientViewModel>(graph),
                onBack = onDone,
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
