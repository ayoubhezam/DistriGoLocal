package com.distrigo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/**
 * Leaves a screen whose subject is gone — a product, a bon, a session that vanished from the list — but only
 * while the screen is still the one on top. Deleting pops the screen too, and a second pop from here would take
 * the list and the graph with it: "No destination with route … is on the NavController's back stack".
 */
@Composable
fun LeaveWhenGone(navController: NavController, entry: NavBackStackEntry, exit: () -> Unit = {}) {
    LaunchedEffect(Unit) {
        // A screen a drill-down started on has nothing under it: it leaves the section through [exit].
        if (navController.currentBackStackEntry?.id == entry.id) navController.popOr(exit)
    }
}
