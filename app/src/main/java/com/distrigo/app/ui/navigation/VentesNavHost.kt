package com.distrigo.app.ui.navigation

import com.distrigo.app.diagnostics.rememberTrackedNavController
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.compose.runtime.rememberCoroutineScope
import com.distrigo.app.ui.ventes.*
import kotlinx.coroutines.launch


/** Opens the vente form graph, carrying the resume decision made before it is entered. */
@Composable
private fun openVenteFormAction(
    navController: androidx.navigation.NavHostController
): (Int?, Int?) -> Unit = { venteId, draftId ->
    // A vente being edited is already known, so it takes the direct graph and skips the client
    // step; a resumed new-vente draft still has a client step to come back to.
    if (venteId != null)
        navController.navigate(Screen.VenteFormGraphDirect.createRoute(venteId = venteId, draftId = draftId))
    else
        navController.navigate(Screen.VenteFormGraph.createRoute(draftId = draftId))
}

/**
 * "Modifier la vente" has to ask whether that vente already has an unsaved edit draft before it
 * opens a second, invisible one — otherwise two parallel edits race and whichever saves last
 * silently wins. Every entry point into edit mode goes through here.
 */
@Composable
private fun editVenteAction(
    viewModel  : VenteViewModel,
    openForm   : (Int?, Int?) -> Unit,
    resumeDraft: (com.distrigo.app.data.model.VenteDraft) -> Unit
): (Int) -> Unit {
    val scope = rememberCoroutineScope()
    return { venteId ->
        scope.launch {
            val existing = viewModel.draftForVente(venteId)
            if (existing == null) openForm(venteId, null) else resumeDraft(existing)
        }
        Unit
    }
}

@Composable
fun VentesNavHost(
    onFullScreenChange: (Boolean) -> Unit = {},
    onBack            : (() -> Unit)? = null
) {
    val navController = rememberTrackedNavController()
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
            val openForm = openVenteFormAction(navController)
            val resumeDraft = venteDraftResumeAction(
                viewModel   = viewModel,
                onOpenForm  = openForm,
                onViewVente = { venteId -> navController.navigate(Screen.VentesDetail.createRoute(venteId)) }
            )
            val editVente = editVenteAction(viewModel, openForm, resumeDraft)

            VentesScreen(
                viewModel        = viewModel,
                onBack           = onBack,
                onAddVente       = { navController.navigate(Screen.VenteFormGraph.createRoute()) },
                onEditVente      = editVente,
                onVenteClick     = { venteId -> navController.navigate(Screen.VentesDetail.createRoute(venteId)) },
                onResumeDraft    = resumeDraft,
                onOpenBrouillons = { navController.navigate(Screen.VentesBrouillons.route) }
            )
        }

        composable(Screen.VentesBrouillons.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.VentesGraph.route) }
            val viewModel: VenteViewModel = hiltViewModel(parentEntry)
            val resumeDraft = venteDraftResumeAction(
                viewModel   = viewModel,
                onOpenForm  = openVenteFormAction(navController),
                onViewVente = { venteId -> navController.navigate(Screen.VentesDetail.createRoute(venteId)) }
            )
            VenteBrouillonsScreen(
                viewModel = viewModel,
                onBack    = { navController.popBackStack() },
                onResume  = resumeDraft
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
            // Observed on its own: the list holds only the page on screen, so the sale may not be in it.
            val lookup by remember(venteId) { viewModel.observeVente(venteId) }
                .collectAsState(initial = VenteLookup.Loading)
            val fallbackVente = (lookup as? VenteLookup.Found)?.vente

            LaunchedEffect(venteId) { viewModel.loadVenteDetail(venteId) }

            if (fallbackVente != null) {
                VenteDetailScreen(
                    vente       = fallbackVente,
                    viewModel   = viewModel,
                    onBack      = { navController.popBackStack() },
                    onDelivered = { navController.popBackStack() },
                    onDeleted   = { navController.popBackStack() }
                )
            } else if (lookup == VenteLookup.Gone) {
                LeaveWhenGone(navController, entry)
            }
        }
    }
}