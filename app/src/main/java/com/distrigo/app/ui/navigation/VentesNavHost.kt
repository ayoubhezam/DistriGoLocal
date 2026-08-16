package com.distrigo.app.ui.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
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
            val viewModel: VenteViewModel = hiltViewModel(parentEntry)
            VentesScreen(
                viewModel    = viewModel,
                onAddVente   = { navController.navigate(Screen.VenteFormGraph.createRoute()) },
                onEditVente  = { venteId -> navController.navigate(Screen.VenteFormGraphDirect.createRoute(venteId = venteId)) },
                onVenteClick = { venteId -> navController.navigate(Screen.VentesDetail.createRoute(venteId)) }
            )
        }

        // onAddVente: no client/vente known yet → starts at the client-picker step.
        venteFormGraph(
            navController    = navController,
            graphRoute        = Screen.VenteFormGraph.route,
            routePrefix       = "ventes_vente_form",
            viewModel         = { hiltViewModel(remember(navController) { navController.getBackStackEntry(Screen.VentesGraph.route) }) },
            productViewModel  = { hiltViewModel() },
            clientViewModel   = { hiltViewModel() },
            onBack  = { navController.popBackStack(Screen.VenteFormGraph.route, inclusive = true) },
            onSaved = { navController.popBackStack(Screen.VenteFormGraph.route, inclusive = true) }
        )

        // onEditVente: venteId already known → skip straight to Products, no client step at all.
        venteFormGraph(
            navController    = navController,
            graphRoute        = Screen.VenteFormGraphDirect.route,
            routePrefix       = "ventes_vente_form_direct",
            skipClientStep    = true,
            viewModel         = { hiltViewModel(remember(navController) { navController.getBackStackEntry(Screen.VentesGraph.route) }) },
            productViewModel  = { hiltViewModel() },
            clientViewModel   = { hiltViewModel() },
            onBack  = { navController.popBackStack(Screen.VenteFormGraphDirect.route, inclusive = true) },
            onSaved = { navController.popBackStack(Screen.VenteFormGraphDirect.route, inclusive = true) }
        )

        composable(
            route     = Screen.VentesDetail.route,
            arguments = listOf(navArgument("venteId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.VentesGraph.route) }
            val viewModel: VenteViewModel = hiltViewModel(parentEntry)
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