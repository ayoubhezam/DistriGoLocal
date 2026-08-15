package com.distrigo.app.ui.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.distrigo.app.ui.ventes.*

@Composable
fun VentesNavHost(onFullScreenChange: (Boolean) -> Unit = {}) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        onFullScreenChange(currentRoute != null && currentRoute != Screen.VentesHome.route)
    }

    NavHost(
        navController      = navController,
        startDestination   = Screen.VentesHome.route,
        route              = Screen.VentesGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.VentesHome.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.VentesGraph.route) }
            val viewModel: VenteViewModel = viewModel(parentEntry)
            VentesScreen(
                viewModel    = viewModel,
                onAddVente   = { navController.navigate(Screen.VenteFormGraph.createRoute()) },
                onEditVente  = { venteId -> navController.navigate(Screen.VenteFormGraph.createRoute(venteId = venteId)) },
                onVenteClick = { venteId -> navController.navigate(Screen.VentesDetail.createRoute(venteId)) }
            )
        }

        venteFormGraph(
            navController    = navController,
            graphRoute        = Screen.VenteFormGraph.route,
            viewModel         = { viewModel(remember(navController) { navController.getBackStackEntry(Screen.VentesGraph.route) }) },
            productViewModel  = { viewModel() },
            clientViewModel   = { viewModel() },
            onBack  = { navController.popBackStack(Screen.VenteFormGraph.route, inclusive = true) },
            onSaved = { navController.popBackStack(Screen.VenteFormGraph.route, inclusive = true) }
        )

        composable(
            route     = Screen.VentesDetail.route,
            arguments = listOf(navArgument("venteId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.VentesGraph.route) }
            val viewModel: VenteViewModel = viewModel(parentEntry)
            val venteId = entry.arguments!!.getInt("venteId")
            val ventes by viewModel.ventes.collectAsState()
            val fallbackVente = ventes.find { it.id == venteId }

            LaunchedEffect(venteId) { viewModel.loadVenteDetail(venteId) }

            if (fallbackVente != null) {
                VenteDetailScreen(
                    vente       = fallbackVente,
                    viewModel   = viewModel,
                    onBack      = { navController.popBackStack() },
                    onDelivered = { navController.popBackStack() },
                    onDeleted   = { navController.popBackStack() }
                )
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
    }
}