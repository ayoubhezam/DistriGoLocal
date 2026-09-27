package com.distrigo.app.ui.navigation

import com.distrigo.app.ui.pertes.PerteDetailViewModel
import com.distrigo.app.ui.pertes.PerteDetailScreen
import com.distrigo.app.diagnostics.rememberTrackedNavController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.distrigo.app.ui.pertes.PerteListScreen
import com.distrigo.app.ui.pertes.PerteViewModel
import com.distrigo.app.ui.pertes.PertesScreen

@Composable
fun PertesNavHost(
    onFullScreenChange: (Boolean) -> Unit = {},
    onBack            : (() -> Unit)? = null
) {
    val navController = rememberTrackedNavController()

    NavHost(
        navController      = navController,
        startDestination   = Screen.PertesHome.route,
        route              = Screen.PertesGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.PertesHome.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.PertesGraph.route) }
            val viewModel: PerteViewModel = hiltViewModel(parentEntry)
            PertesScreen(
                viewModel   = viewModel,
                onBack      = onBack,
                onTypeClick = { typeId -> navController.navigate(Screen.PertesList.createRoute(typeId)) }
            )
        }

        composable(
            route     = Screen.PertesList.route,
            arguments = listOf(navArgument("typeId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.PertesGraph.route) }
            val viewModel: PerteViewModel = hiltViewModel(parentEntry)
            val typeId = entry.arguments!!.getInt("typeId")
            PerteListScreen(
                typeId      = typeId,
                viewModel   = viewModel,
                onBack      = { navController.popBackStack() },
                onAddPerte  = { navController.navigate(Screen.PertesFormGraph.createRoute(typeId)) },
                onOpenPerte = { perte -> navController.navigate(Screen.PertesDetail.createRoute(perte.id)) }
            )
        }

        // Read-only: a perte is looked at here, and changed only through "Modifier", confirmed.
        composable(
            route     = Screen.PertesDetail.route,
            arguments = listOf(navArgument(PerteDetailViewModel.ARG_PERTE) { type = NavType.IntType })
        ) {
            val shared: PerteViewModel = hiltViewModel(remember(navController) { navController.getBackStackEntry(Screen.PertesGraph.route) })
            PerteDetailScreen(
                onBack    = { navController.popBackStack() },
                onEdit    = { perte -> navController.navigate(Screen.PertesFormGraph.createRoute(perte.type_id, perte.id)) },
                onDeleted = { typeId -> shared.refreshAfterChange(typeId); navController.popBackStack() }
            )
        }

        pertesFormGraph(
            navController = navController,
            graphRoute    = Screen.PertesFormGraph.route,
            viewModel     = { hiltViewModel(remember(navController) { navController.getBackStackEntry(Screen.PertesGraph.route) }) },
            onBack  = { navController.popBackStack(Screen.PertesFormGraph.route, inclusive = true) },
            onSaved = {
                navController.popBackStack(Screen.PertesFormGraph.route, inclusive = true)
            }
        )
    }
}