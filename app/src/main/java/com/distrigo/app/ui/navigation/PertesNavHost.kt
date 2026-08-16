package com.distrigo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.distrigo.app.ui.pertes.PerteListScreen
import com.distrigo.app.ui.pertes.PerteViewModel
import com.distrigo.app.ui.pertes.PertesScreen

@Composable
fun PertesNavHost(onFullScreenChange: (Boolean) -> Unit = {}) {
    val navController = rememberNavController()

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
                onEditPerte = { perte -> navController.navigate(Screen.PertesFormGraph.createRoute(typeId, perte.id)) }
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