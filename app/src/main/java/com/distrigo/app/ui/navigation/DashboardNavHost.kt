package com.distrigo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.distrigo.app.ui.dashboard.DashboardScreen

@Composable
fun DashboardNavHost(
    onOpenMenu           : (() -> Unit)? = null,
    onNotificationsClick : () -> Unit = {},
    onProfileClick       : () -> Unit = {}
) {
    val navController = rememberNavController()
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
                onOpenMenu           = onOpenMenu,
                onNotificationsClick = onNotificationsClick,
                onProfileClick       = onProfileClick
            )
        }
    }
}