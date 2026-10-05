package com.distrigo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.distrigo.app.diagnostics.rememberTrackedNavController
import com.distrigo.app.ui.charges.ChargeDetailScreen
import com.distrigo.app.ui.charges.ChargeDetailViewModel
import com.distrigo.app.ui.charges.ChargeHistoryScreen

/**
 * Charges: the history of expenses, "+" for a new one in a centred dialog, and each charge's detail,
 * where "Modifier" opens the same dialog. Types and sub-types are chosen — and new sub-types made — in
 * that dialog's list.
 */
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
        composable(Screen.ChargesHome.route) {
            ChargeHistoryScreen(
                onBack = onBack,
                onOpen = { charge -> navController.navigate(Screen.ChargesDetail.createRoute(charge.id)) }
            )
        }

        // Read-only: a record is looked at here, and changed only through "Modifier", confirmed.
        // The history is live: what is changed or deleted here shows there on the way back.
        composable(
            route     = Screen.ChargesDetail.route,
            arguments = listOf(navArgument(ChargeDetailViewModel.ARG_CHARGE) { type = NavType.IntType })
        ) {
            ChargeDetailScreen(
                onBack    = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() }
            )
        }
    }
}
