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
import com.distrigo.app.ui.retours.NewRetourFournisseurViewModel
import com.distrigo.app.ui.retours.NewRetourListScreen
import com.distrigo.app.ui.retours.NewRetourSummaryScreen

/**
 * A return to a supplier: what they delivered, a centred dialog per product, the selection and a dated
 * summary — the same flow as a client's return. Its ViewModel belongs to this graph, keyed by the
 * supplier in its route, so each return starts empty. [onDone] leaves it, saved or not.
 */
fun NavGraphBuilder.retourFournisseurFormGraph(navController: NavHostController, graphRoute: String, onDone: () -> Unit) {
    navigation(
        startDestination = Screen.SuppliersRetourFormProducts.route,
        route = graphRoute,
        arguments = listOf(navArgument("supplierId") { type = NavType.IntType })
    ) {
        composable(Screen.SuppliersRetourFormProducts.route) { entry ->
            val graph = remember(entry) { navController.getBackStackEntry(graphRoute) }
            NewRetourListScreen(
                viewModel = hiltViewModel<NewRetourFournisseurViewModel>(graph),
                onBack = onDone,
                onOpenCart = { navController.navigate(Screen.SuppliersRetourFormCart.route) }
            )
        }
        composable(Screen.SuppliersRetourFormCart.route) { entry ->
            val graph = remember(entry) { navController.getBackStackEntry(graphRoute) }
            NewRetourCartScreen(
                viewModel = hiltViewModel<NewRetourFournisseurViewModel>(graph),
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(Screen.SuppliersRetourFormSummary.route) }
            )
        }
        composable(Screen.SuppliersRetourFormSummary.route) { entry ->
            val graph = remember(entry) { navController.getBackStackEntry(graphRoute) }
            NewRetourSummaryScreen(
                viewModel = hiltViewModel<NewRetourFournisseurViewModel>(graph),
                onBack = { navController.popBackStack() },
                onDone = onDone
            )
        }
    }
}
