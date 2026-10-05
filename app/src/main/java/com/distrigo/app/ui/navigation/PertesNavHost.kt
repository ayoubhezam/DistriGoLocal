package com.distrigo.app.ui.navigation

import com.distrigo.app.ui.pertes.PerteDetailViewModel
import com.distrigo.app.ui.pertes.PerteDetailScreen
import com.distrigo.app.diagnostics.rememberTrackedNavController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.distrigo.app.ui.pertes.NewPerteCartScreen
import com.distrigo.app.ui.pertes.NewPerteListScreen
import com.distrigo.app.ui.pertes.NewPerteSummaryScreen
import com.distrigo.app.ui.pertes.NewPerteViewModel
import com.distrigo.app.ui.pertes.PerteHistoryScreen
import com.distrigo.app.ui.products.ProductViewModel

/**
 * Pertes: the history of losses, "+" for a new one — a list, a centred dialog per product, a
 * selection and a dated summary, as the Inventaire records its counts — and each perte's detail.
 * New types are made in the dialog's "Type de perte" list.
 */
@Composable
fun PertesNavHost(
    /** Opens the section on that perte instead of the history — a drill-down: Back then leaves. */
    openPerteId       : Int? = null,
    onFullScreenChange: (Boolean) -> Unit = {},
    onBack            : (() -> Unit)? = null
) {
    val navController = rememberTrackedNavController()
    val exit = { onBack?.invoke(); Unit }

    NavHost(
        navController      = navController,
        startDestination   = if (openPerteId != null) Screen.PertesDetail.route else Screen.PertesHome.route,
        route              = Screen.PertesGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.PertesHome.route) {
            PerteHistoryScreen(
                onBack  = onBack,
                onNew   = { navController.navigate(Screen.PertesNewGraph.route) },
                onOpen  = { perte -> navController.navigate(Screen.PertesDetail.createRoute(perte.id)) }
            )
        }

        // Read-only: a perte is looked at here, and changed only through "Modifier", confirmed.
        composable(
            route     = Screen.PertesDetail.route,
            // Started on, it has no route to read its id from: the drill-down's id is the default.
            arguments = listOf(navArgument(PerteDetailViewModel.ARG_PERTE) { type = NavType.IntType; openPerteId?.let { defaultValue = it } })
        ) {
            // The history is live: a perte deleted here is gone from it on the way back.
            PerteDetailScreen(
                onBack    = { navController.popOr(exit) },
                onDeleted = { navController.popOr(exit) }
            )
        }

        // A new perte. Its ViewModel belongs to this graph: leaving it, the next one starts empty.
        navigation(startDestination = Screen.PertesNewList.route, route = Screen.PertesNewGraph.route) {
            composable(Screen.PertesNewList.route) { entry ->
                val graph = remember(entry) { navController.getBackStackEntry(Screen.PertesNewGraph.route) }
                val viewModel: NewPerteViewModel = hiltViewModel(graph)
                val productViewModel: ProductViewModel = hiltViewModel()
                val categories by productViewModel.categories.collectAsState()
                val sousCategories by productViewModel.sousCategories.collectAsState()
                val marques by productViewModel.marques.collectAsState()
                val suppliers by productViewModel.suppliers.collectAsState()
                NewPerteListScreen(
                    viewModel = viewModel, categories = categories, sousCategories = sousCategories,
                    marques = marques, suppliers = suppliers,
                    onBack = { navController.popBackStack(Screen.PertesNewGraph.route, inclusive = true) },
                    onOpenCart = { navController.navigate(Screen.PertesNewCart.route) }
                )
            }
            composable(Screen.PertesNewCart.route) { entry ->
                val graph = remember(entry) { navController.getBackStackEntry(Screen.PertesNewGraph.route) }
                NewPerteCartScreen(
                    viewModel = hiltViewModel<NewPerteViewModel>(graph),
                    onBack = { navController.popBackStack() },
                    onNext = { navController.navigate(Screen.PertesNewSummary.route) }
                )
            }
            composable(Screen.PertesNewSummary.route) { entry ->
                val graph = remember(entry) { navController.getBackStackEntry(Screen.PertesNewGraph.route) }
                NewPerteSummaryScreen(
                    viewModel = hiltViewModel<NewPerteViewModel>(graph),
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack(Screen.PertesNewGraph.route, inclusive = true) }
                )
            }
        }
    }
}
