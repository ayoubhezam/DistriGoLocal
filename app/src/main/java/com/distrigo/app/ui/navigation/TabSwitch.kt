package com.distrigo.app.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

/** The bottom bar's five tabs, left to right. */
val TAB_ROUTES: Set<String> = linkedSetOf(
    Screen.TabDashboard.route,
    Screen.TabDepotVente.route,
    Screen.TabTournees.route,
    Screen.TabProduits.route,
    Screen.TabAchats.route,
)

/**
 * A bottom-tab switch, the standard "multiple back stacks" pattern: pop to the start destination (saving each tab's
 * state) before navigating, then restore the target tab's saved state if it has any.
 *
 * A screen opened on the root over a tab while the bar still shows (Stock Camion) is left first, without saving: saved
 * with the tab under it, it came back on top of that tab the next time the tab was chosen (Produits showed Stock
 * Camion). Back from it still returns to the tab or tournée it was opened from.
 */
fun NavController.switchToTab(route: String) {
    while (currentDestination?.route !in TAB_ROUTES && previousBackStackEntry != null) {
        popBackStack()
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
