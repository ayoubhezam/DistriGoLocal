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
                onOpenDettes = { navController.navigate(Screen.RapportsDettes.route) }
            )
        }
        composable(Screen.RapportsVentes.route) {
            VentesReportScreen(onBack = { navController.popBackStack() })
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
