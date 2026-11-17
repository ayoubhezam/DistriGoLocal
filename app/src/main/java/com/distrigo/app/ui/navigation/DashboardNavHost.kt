package com.distrigo.app.ui.navigation

import com.distrigo.app.diagnostics.rememberTrackedNavController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.distrigo.app.ui.settings.data.CloudBackupScreen
import com.distrigo.app.ui.settings.data.DataBackupScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.distrigo.app.data.repository.TourneeDay
import com.distrigo.app.ui.dashboard.DashboardScreen
import com.distrigo.app.ui.settings.incentive.CommissionPolicyScreen
import com.distrigo.app.ui.tournees.AddClientsToTourneeScreen

private const val DASHBOARD_ADD_CLIENTS = "dashboard_tournee_add_clients"
private const val DASHBOARD_MON_OBJECTIF = "dashboard_mon_objectif"
private const val DASHBOARD_CLOUD_RESTORE = "dashboard_cloud_restore_file"

/**
 * The Dashboard and what its quick actions open, in place (docs/design/dashboard_objectif_plan.md, Part 1): the
 * sale (tournée or dépôt, D14), the bon d'achat, the clients to add to the tournée, and « Fixez un objectif ». The
 * forms are the sections' own graphs, so a sale made here is the same as one made in Ventes.
 */
@Composable
fun DashboardNavHost(
    /** Opens a report straight from one of the Dashboard's cards. */
    onOpenReport         : (ReportEntry) -> Unit = {},
    onOpenMenu           : (() -> Unit)? = null,
    onNotificationsClick : () -> Unit = {},
    onProfileClick       : () -> Unit = {},
    /** The bottom bar goes while a form is open, as in the other tabs. */
    onFullScreenChange   : (Boolean) -> Unit = {},
) {
    val navController = rememberTrackedNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        onFullScreenChange(currentRoute != null && currentRoute != Screen.Dashboard.route)
    }
    // The tournée whose clients « Ajouter » adds to, as the Dashboard last showed it
    var addingTo by remember { mutableStateOf<TourneeDay?>(null) }
    // The forms' view models live with the Dashboard's entry, as they live with their section's graph elsewhere
    val dashboardEntry: @Composable () -> NavBackStackEntry = {
        remember(navController) { navController.getBackStackEntry(Screen.Dashboard.route) }
    }

    NavHost(
        navController      = navController,
        startDestination   = Screen.Dashboard.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onOpenReport         = onOpenReport,
                onOpenMenu           = onOpenMenu,
                onNotificationsClick = onNotificationsClick,
                onProfileClick       = onProfileClick,
                onNewSale            = { tourneeId ->
                    if (tourneeId != null) navController.navigate(Screen.TourneeVenteFormGraph.createRoute(tourneeId))
                    else navController.navigate(Screen.VenteFormGraph.createRoute())
                },
                onNewPurchase        = { navController.navigate(Screen.PurchaseFormGraph.createRoute()) },
                onAddTourneeClients  = { tournee ->
                    addingTo = tournee
                    navController.navigate(DASHBOARD_ADD_CLIENTS) { launchSingleTop = true }
                },
                onSetObjective       = { navController.navigate(Screen.SettingsCommission.route) { launchSingleTop = true } },
                onOpenGoal           = { navController.navigate(DASHBOARD_MON_OBJECTIF) { launchSingleTop = true } },
                onOpenCloudBackup    = { navController.navigate(Screen.SettingsCloudBackup.route) { launchSingleTop = true } },
                onTourneeClientSale  = { tourneeId, clientId, clientName ->
                    navController.navigate(Screen.TourneeVenteFormGraphDirect.createRoute(tourneeId, clientId, clientName))
                },
            )
        }

        // D14: Dépôt Vente when no tournée is open
        venteFormGraph(
            navController    = navController,
            graphRoute       = Screen.VenteFormGraph.route,
            routePrefix      = "dashboard_vente_form",
            viewModel        = { hiltViewModel(dashboardEntry()) },
            productViewModel = { hiltViewModel() },
            clientViewModel  = { hiltViewModel() },
            onBack  = { navController.popBackStack(Screen.VenteFormGraph.route, inclusive = true) },
            onSaved = { navController.popBackStack(Screen.VenteFormGraph.route, inclusive = true) }
        )

        // D14: the tournée sale while one is open
        tourneeVenteFormGraph(
            navController    = navController,
            graphRoute       = Screen.TourneeVenteFormGraph.route,
            routePrefix      = "dashboard_tournee_vente_form",
            viewModel        = { hiltViewModel(dashboardEntry()) },
            venteViewModel   = { hiltViewModel() },
            productViewModel = { hiltViewModel() },
            clientViewModel  = { hiltViewModel() },
            onBack  = { navController.popBackStack(Screen.TourneeVenteFormGraph.route, inclusive = true) },
            onSaved = { navController.popBackStack(Screen.TourneeVenteFormGraph.route, inclusive = true) }
        )

        // A client of the day's sale: straight to the products, as from the tournée
        tourneeVenteFormGraph(
            navController    = navController,
            graphRoute       = Screen.TourneeVenteFormGraphDirect.route,
            routePrefix      = "dashboard_tournee_vente_form_direct",
            skipClientStep   = true,
            viewModel        = { hiltViewModel(dashboardEntry()) },
            venteViewModel   = { hiltViewModel() },
            productViewModel = { hiltViewModel() },
            clientViewModel  = { hiltViewModel() },
            onBack  = { navController.popBackStack(Screen.TourneeVenteFormGraphDirect.route, inclusive = true) },
            onSaved = { navController.popBackStack(Screen.TourneeVenteFormGraphDirect.route, inclusive = true) }
        )

        purchaseFormGraph(
            navController     = navController,
            graphRoute        = Screen.PurchaseFormGraph.route,
            viewModel         = { hiltViewModel(dashboardEntry()) },
            productViewModel  = { hiltViewModel() },
            supplierViewModel = { hiltViewModel() },
            onBack  = { navController.popBackStack(Screen.PurchaseFormGraph.route, inclusive = true) },
            onSaved = { navController.popBackStack(Screen.PurchaseFormGraph.route, inclusive = true) }
        )

        composable(DASHBOARD_ADD_CLIENTS) {
            val tournee = addingTo
            if (tournee == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                AddClientsToTourneeScreen(
                    tourneeId         = tournee.id,
                    tourneeNom        = tournee.label,
                    existingClientIds = tournee.clients.map { it.clientId }.toSet(),
                    onBack            = { navController.popBackStack() },
                    onSaved           = { navController.popBackStack() },
                )
            }
        }

        // The cloud backup's alert line: its screen, and « Sauvegarde et restauration » for a backup it downloads
        composable(Screen.SettingsCloudBackup.route) {
            CloudBackupScreen(
                onBack = { navController.popBackStack() },
                onRestoreFile = { uri ->
                    navController.popBackStack()
                    navController.navigate(Screen.SettingsData.route)
                    navController.currentBackStackEntry?.savedStateHandle?.set(DASHBOARD_CLOUD_RESTORE, uri.toString())
                },
            )
        }
        composable(Screen.SettingsData.route) { entry ->
            val cloudRestore by entry.savedStateHandle.getStateFlow<String?>(DASHBOARD_CLOUD_RESTORE, null).collectAsState()
            DataBackupScreen(
                onBack = { navController.popBackStack() },
                onCloud = { navController.navigate(Screen.SettingsCloudBackup.route) { launchSingleTop = true } },
                cloudRestore = cloudRestore,
                onCloudRestoreTaken = { entry.savedStateHandle[DASHBOARD_CLOUD_RESTORE] = null },
            )
        }

        composable(DASHBOARD_MON_OBJECTIF) {
            com.distrigo.app.ui.goal.MonObjectifScreen(
                onBack       = { navController.popBackStack() },
                onEditPolicy = { navController.navigate(Screen.SettingsCommission.route) { launchSingleTop = true } },
            )
        }

        composable(Screen.SettingsCommission.route) {
            CommissionPolicyScreen(onBack = { navController.popBackStack() })
        }
    }
}
