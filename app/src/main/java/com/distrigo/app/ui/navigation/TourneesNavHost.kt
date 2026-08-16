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
import com.distrigo.app.ui.clients.ClientViewModel
import com.distrigo.app.ui.products.ProductViewModel
import com.distrigo.app.ui.tournees.*
import com.distrigo.app.ui.ventes.VenteDetailScreen
import com.distrigo.app.ui.ventes.VenteViewModel

@Composable
fun TourneesNavHost(
    onFullScreenChange: (Boolean) -> Unit = {},
    onNavigateToChargement: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        onFullScreenChange(currentRoute != null && currentRoute != Screen.TourneesHome.route)
    }

    NavHost(
        navController      = navController,
        startDestination   = Screen.TourneesHome.route,
        route              = Screen.TourneesGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.TourneesHome.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.TourneesGraph.route) }
            val viewModel: TourneeViewModel = hiltViewModel(parentEntry)
            TourneesScreen(
                viewModel      = viewModel,
                onAddTournee   = { navController.navigate(Screen.TourneeForm.createRoute()) },
                onTourneeClick = { tourneeId -> navController.navigate(Screen.TourneesDetail.createRoute(tourneeId)) }
            )
        }

        composable(
            route     = Screen.TourneesDetail.route,
            arguments = listOf(navArgument("tourneeId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.TourneesGraph.route) }
            val viewModel: TourneeViewModel = hiltViewModel(parentEntry)
            val tourneeId = entry.arguments!!.getInt("tourneeId")

            TourneeDetailScreen(
                tourneeId             = tourneeId,
                viewModel              = viewModel,
                onBack                 = { navController.popBackStack() },
                onEditTournee          = { tournee -> navController.navigate(Screen.TourneeForm.createRoute(tournee.id)) },
                onAddClients           = { navController.navigate(Screen.TourneesAddClients.createRoute(tourneeId)) },
                onCreateVente          = { clientId ->
                    if (clientId != null) {
                        navController.navigate(Screen.TourneeVenteFormGraphDirect.createRoute(tourneeId, clientId))
                    } else {
                        navController.navigate(Screen.TourneeVenteFormGraph.createRoute(tourneeId))
                    }
                },
                onOpenVente            = { vente -> navController.navigate(Screen.TourneesVenteDetail.createRoute(tourneeId, vente.id)) },
                onNavigateToChargement = onNavigateToChargement
            )
        }

        composable(
            route     = Screen.TourneeForm.route,
            arguments = listOf(navArgument("tourneeId") { type = NavType.IntType; defaultValue = -1 })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.TourneesGraph.route) }
            val viewModel: TourneeViewModel = hiltViewModel(parentEntry)
            val tourneeIdArg = entry.arguments?.getInt("tourneeId")?.takeIf { it != -1 }
            val selectedTournee by viewModel.selectedTournee.collectAsState()
            val editingTournee = tourneeIdArg?.let { id -> selectedTournee?.takeIf { it.id == id } }

            TourneeFormScreen(
                tournee   = editingTournee,
                viewModel = viewModel,
                onBack    = { navController.popBackStack() },
                onSaved   = { navController.popBackStack(); viewModel.loadTournees() }
            )
        }

        composable(
            route     = Screen.TourneesAddClients.route,
            arguments = listOf(navArgument("tourneeId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.TourneesGraph.route) }
            val viewModel: TourneeViewModel = hiltViewModel(parentEntry)
            val tourneeId = entry.arguments!!.getInt("tourneeId")
            val selectedTournee by viewModel.selectedTournee.collectAsState()
            val tourneeClients by viewModel.tourneeClients.collectAsState()
            val current = selectedTournee

            if (current == null || current.id != tourneeId) {
                LaunchedEffect(tourneeId) { navController.popBackStack() }
            } else {
                AddClientsToTourneeScreen(
                    tourneeId         = tourneeId,
                    tourneeNom        = current.nom,
                    existingClientIds = tourneeClients.map { it.client.id }.toSet(),
                    onBack            = { navController.popBackStack() },
                    onSaved           = { navController.popBackStack() },
                    tourneeViewModel  = viewModel
                )
            }
        }

        // Client unknown at entry (defensive — current callers always pass one, see onCreateVente
        // above) → starts at the client-picker step.
        tourneeVenteFormGraph(
            navController    = navController,
            graphRoute       = Screen.TourneeVenteFormGraph.route,
            routePrefix      = "tournee_vente_form",
            viewModel        = { hiltViewModel(remember(navController) { navController.getBackStackEntry(Screen.TourneesGraph.route) }) },
            venteViewModel   = { hiltViewModel() },
            productViewModel = { hiltViewModel() },
            clientViewModel  = { hiltViewModel() },
            onBack  = { navController.popBackStack(Screen.TourneeVenteFormGraph.route, inclusive = true) },
            onSaved = { navController.popBackStack(Screen.TourneeVenteFormGraph.route, inclusive = true) }
        )

        // Client already known (Tournée → City → Client → "Créer une vente") → skip straight to
        // Products, no client step at all.
        tourneeVenteFormGraph(
            navController    = navController,
            graphRoute       = Screen.TourneeVenteFormGraphDirect.route,
            routePrefix      = "tournee_vente_form_direct",
            skipClientStep   = true,
            viewModel        = { hiltViewModel(remember(navController) { navController.getBackStackEntry(Screen.TourneesGraph.route) }) },
            venteViewModel   = { hiltViewModel() },
            productViewModel = { hiltViewModel() },
            clientViewModel  = { hiltViewModel() },
            onBack  = { navController.popBackStack(Screen.TourneeVenteFormGraphDirect.route, inclusive = true) },
            onSaved = { navController.popBackStack(Screen.TourneeVenteFormGraphDirect.route, inclusive = true) }
        )

        composable(
            route     = Screen.TourneesVenteDetail.route,
            arguments = listOf(
                navArgument("tourneeId") { type = NavType.IntType },
                navArgument("venteId")   { type = NavType.IntType }
            )
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.TourneesGraph.route) }
            val viewModel: TourneeViewModel = hiltViewModel(parentEntry)
            val venteViewModel: VenteViewModel = hiltViewModel()
            val tourneeId = entry.arguments!!.getInt("tourneeId")
            val venteId = entry.arguments!!.getInt("venteId")
            val selectedTournee by viewModel.selectedTournee.collectAsState()
            val vente = selectedTournee?.takeIf { it.id == tourneeId }?.ventes?.find { it.id == venteId }

            LaunchedEffect(venteId) { venteViewModel.loadVenteDetail(venteId) }

            if (vente != null) {
                VenteDetailScreen(
                    vente       = vente,
                    viewModel   = venteViewModel,
                    onBack      = { navController.popBackStack() },
                    onDelivered = {
                        navController.popBackStack()
                        viewModel.loadTourneeDetail(tourneeId)
                        viewModel.refreshAfterVenteChange(tourneeId)
                    },
                    onDeleted = {
                        navController.popBackStack()
                        viewModel.loadTourneeDetail(tourneeId)
                        viewModel.refreshAfterVenteChange(tourneeId)
                    }
                )
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
    }
}
