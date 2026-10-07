package com.distrigo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.distrigo.app.diagnostics.rememberTrackedNavController
import com.distrigo.app.ui.rapports.DebtReportScreen
import com.distrigo.app.ui.rapports.DebtReportViewModel
import com.distrigo.app.ui.rapports.DebtorsScreen
import com.distrigo.app.ui.rapports.RapportsHomeScreen
import com.distrigo.app.ui.rapports.VentesReportScreen

/** Rapports: the list of reports, and each report under it. They share one filter (ReportFilterStore). */
@Composable
fun RapportsNavHost(onBack: () -> Unit) {
    val navController = rememberTrackedNavController()

    NavHost(
        navController      = navController,
        startDestination   = Screen.RapportsHome.route,
        route              = Screen.RapportsGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.RapportsHome.route) {
            RapportsHomeScreen(
                onBack       = onBack,
                onOpenVentes = { navController.navigate(Screen.RapportsVentes.route) },
                onOpenDettes = { navController.navigate(Screen.RapportsDettes.route) },
                onOpenProduits = { navController.navigate(Screen.RapportsProduits.route) },
                onOpenStock = { navController.navigate(Screen.RapportsStock.route) },
                onOpenTiers = { navController.navigate(Screen.RapportsTiers.route) },
                onOpenResultat = { navController.navigate(Screen.RapportsResultat.route) }
            )
        }
        composable(Screen.RapportsResultat.route) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(Screen.RapportsGraph.route) }
            com.distrigo.app.ui.rapports.ProfitReportScreen(
                onBack    = { navController.popBackStack() },
                viewModel = hiltViewModel<com.distrigo.app.ui.rapports.ProfitReportViewModel>(parent)
            )
        }
        composable(Screen.RapportsTiers.route) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(Screen.RapportsGraph.route) }
            com.distrigo.app.ui.rapports.PartyReportScreen(
                onBack     = { navController.popBackStack() },
                onSeeAll   = { navController.navigate(Screen.RapportsTiersTous.route) },
                onInactive = { navController.navigate(Screen.RapportsInactifs.route) },
                viewModel  = hiltViewModel<com.distrigo.app.ui.rapports.PartyReportViewModel>(parent)
            )
        }
        composable(Screen.RapportsTiersTous.route) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(Screen.RapportsGraph.route) }
            com.distrigo.app.ui.rapports.PartiesScreen(
                onBack    = { navController.popBackStack() },
                viewModel = hiltViewModel<com.distrigo.app.ui.rapports.PartyReportViewModel>(parent)
            )
        }
        composable(Screen.RapportsInactifs.route) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(Screen.RapportsGraph.route) }
            com.distrigo.app.ui.rapports.InactiveClientsScreen(
                onBack    = { navController.popBackStack() },
                viewModel = hiltViewModel<com.distrigo.app.ui.rapports.PartyReportViewModel>(parent)
            )
        }
        composable(Screen.RapportsStock.route) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(Screen.RapportsGraph.route) }
            com.distrigo.app.ui.rapports.StockReportScreen(
                onBack       = { navController.popBackStack() },
                onSeeRestock = { navController.navigate(Screen.RapportsReappro.route) },
                viewModel    = hiltViewModel<com.distrigo.app.ui.rapports.StockReportViewModel>(parent)
            )
        }
        composable(Screen.RapportsReappro.route) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(Screen.RapportsGraph.route) }
            com.distrigo.app.ui.rapports.RestockScreen(
                onBack    = { navController.popBackStack() },
                viewModel = hiltViewModel<com.distrigo.app.ui.rapports.StockReportViewModel>(parent)
            )
        }
        // The report and its two lists share one ViewModel, held by the graph, as Créances et dettes'.
        composable(Screen.RapportsProduits.route) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(Screen.RapportsGraph.route) }
            com.distrigo.app.ui.rapports.ProduitsReportScreen(
                onBack      = { navController.popBackStack() },
                onSeeAll    = { navController.navigate(Screen.RapportsProduitsTous.route) },
                onSansVente = { navController.navigate(Screen.RapportsSansVente.route) },
                viewModel   = hiltViewModel<com.distrigo.app.ui.rapports.ProduitsReportViewModel>(parent)
            )
        }
        composable(Screen.RapportsProduitsTous.route) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(Screen.RapportsGraph.route) }
            com.distrigo.app.ui.rapports.ProductRankingScreen(
                onBack    = { navController.popBackStack() },
                viewModel = hiltViewModel<com.distrigo.app.ui.rapports.ProduitsReportViewModel>(parent)
            )
        }
        composable(Screen.RapportsSansVente.route) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(Screen.RapportsGraph.route) }
            com.distrigo.app.ui.rapports.DormantProductsScreen(
                onBack    = { navController.popBackStack() },
                viewModel = hiltViewModel<com.distrigo.app.ui.rapports.ProduitsReportViewModel>(parent)
            )
        }
        composable(Screen.RapportsVentes.route) {
            VentesReportScreen(
                onBack    = { navController.popBackStack() },
                onOpenDay = { day -> navController.navigate(Screen.RapportsVentesJour.createRoute(day)) }
            )
        }
        composable(
            route     = Screen.RapportsVentesJour.route,
            arguments = listOf(androidx.navigation.navArgument("day") { type = androidx.navigation.NavType.StringType })
        ) {
            com.distrigo.app.ui.rapports.DaySalesScreen(onBack = { navController.popBackStack() })
        }
        // The report and its "Voir tout" share one ViewModel, held by the graph: the full list shows
        // the report already loaded, on the same side, without reading the database again.
        composable(Screen.RapportsDettes.route) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(Screen.RapportsGraph.route) }
            DebtReportScreen(
                onBack    = { navController.popBackStack() },
                onSeeAll  = { navController.navigate(Screen.RapportsDebiteurs.route) },
                viewModel = hiltViewModel<DebtReportViewModel>(parent)
            )
        }
        composable(Screen.RapportsDebiteurs.route) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(Screen.RapportsGraph.route) }
            DebtorsScreen(
                onBack    = { navController.popBackStack() },
                viewModel = hiltViewModel<DebtReportViewModel>(parent)
            )
        }
    }
}
