package com.distrigo.app.ui.navigation

import com.distrigo.app.diagnostics.rememberTrackedNavController
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.distrigo.app.ui.dashboard.DashboardScreen

@Composable
fun DashboardNavHost(
    /** Opens a report straight from one of the Dashboard's cards. */
    onOpenReport         : (ReportEntry) -> Unit = {},
    onOpenMenu           : (() -> Unit)? = null,
    onNotificationsClick : () -> Unit = {},
    onProfileClick       : () -> Unit = {}
) {
    val navController = rememberTrackedNavController()
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
                onProfileClick       = onProfileClick
            )
        }
    }
}