package com.distrigo.app.ui.navigation

import com.distrigo.app.diagnostics.rememberTrackedNavController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.distrigo.app.ui.charges.*
import androidx.compose.runtime.remember

@Composable
fun ChargesNavHost(
    onFullScreenChange: (Boolean) -> Unit = {},
    onBack            : (() -> Unit)? = null
) {
    val navController = rememberTrackedNavController()

    NavHost(
        navController      = navController,
        startDestination   = Screen.ChargesHome.route,
        route              = Screen.ChargesGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.ChargesHome.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ChargesGraph.route) }
            val viewModel: ChargeViewModel = hiltViewModel(parentEntry)
            ChargesScreen(
                viewModel   = viewModel,
                onBack      = onBack,
                onTypeClick = { typeId -> navController.navigate(Screen.ChargesSubTypes.createRoute(typeId)) },
                onAddCharge = { navController.navigate(Screen.ChargesForm.createRoute()) }
            )
        }

        composable(
            route     = Screen.ChargesSubTypes.route,
            arguments = listOf(navArgument("typeId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ChargesGraph.route) }
            val viewModel: ChargeViewModel = hiltViewModel(parentEntry)
            val typeId = entry.arguments!!.getInt("typeId")
            ChargeSubTypesScreen(
                typeId         = typeId,
                viewModel      = viewModel,
                onBack         = { navController.popBackStack() },
                onSubTypeClick = { subtypeId -> navController.navigate(Screen.ChargesList.createRoute(subtypeId)) }
            )
        }

        composable(
            route     = Screen.ChargesList.route,
            arguments = listOf(navArgument("subtypeId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ChargesGraph.route) }
            val viewModel: ChargeViewModel = hiltViewModel(parentEntry)
            val subtypeId = entry.arguments!!.getInt("subtypeId")
            ChargeListScreen(
                subtypeId    = subtypeId,
                viewModel    = viewModel,
                onBack       = { navController.popBackStack() },
                onAddCharge  = { navController.navigate(Screen.ChargesForm.createRoute(subtypeId)) },
                onOpenCharge = { charge -> navController.navigate(Screen.ChargesDetail.createRoute(charge.id)) }
            )
        }

        // Read-only: a record is looked at here, and changed only through "Modifier", confirmed.
        composable(
            route     = Screen.ChargesDetail.route,
            arguments = listOf(navArgument(ChargeDetailViewModel.ARG_CHARGE) { type = NavType.IntType })
        ) {
            val shared: ChargeViewModel = hiltViewModel(remember(navController) { navController.getBackStackEntry(Screen.ChargesGraph.route) })
            ChargeDetailScreen(
                onBack    = { navController.popBackStack() },
                onEdit    = { charge -> navController.navigate(Screen.ChargesForm.createRoute(charge.subtype_id, charge.id)) },
                onDeleted = { deleted -> shared.onChargeDeleted(deleted); navController.popBackStack() }
            )
        }

        composable(
            route     = Screen.ChargesForm.route,
            arguments = listOf(
                navArgument(ChargeEntryViewModel.ARG_SUBTYPE) { type = NavType.IntType; defaultValue = -1 },
                navArgument(ChargeEntryViewModel.ARG_CHARGE)  { type = NavType.IntType; defaultValue = -1 }
            )
        ) {
            val shared: ChargeViewModel = hiltViewModel(remember(navController) { navController.getBackStackEntry(Screen.ChargesGraph.route) })
            ChargeEntryScreen(
                onBack  = { navController.popBackStack() },
                onSaved = { saved -> shared.onChargeSaved(saved); navController.popBackStack() }
            )
        }
    }
}