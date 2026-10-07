package com.distrigo.app.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import androidx.navigation.NavHostController

/**
 * What a drill-down opens: a record, by its id. A screen says *what* to open — a client, a bon — never
 * which route: routes are the root's business (see [drillDown]), so every caller follows one change.
 *
 * Add a target here, with its Screen.Drill and its case in [screen], to make it openable from
 * anywhere in the app.
 */
sealed interface DrillTarget {
    val id: Int

    data class Client(override val id: Int) : DrillTarget
    data class Supplier(override val id: Int) : DrillTarget
    /** A purchase order — a bon d'achat. */
    data class Bon(override val id: Int) : DrillTarget
    /** A sale, from the dépôt or a tournée's camion. */
    data class Vente(override val id: Int) : DrillTarget
    /** A perte, a loss recorded on its own or by a return. */
    data class Perte(override val id: Int) : DrillTarget
    /** An inventory session — a count, its lines and their écarts. */
    data class Inventaire(override val id: Int) : DrillTarget
    /** A client's returns: a return has no page of its own, its client's list of returns is it. */
    data class RetoursClient(override val id: Int) : DrillTarget
    /** A supplier's returns, as for a client's. */
    data class RetoursFournisseur(override val id: Int) : DrillTarget
    /** A product's page: its stock, prices and movements. */
    data class Produit(override val id: Int) : DrillTarget
    /** A tournée's page: its clients, visits and sales. */
    data class Tournee(override val id: Int) : DrillTarget
}

/** The root destination that opens this kind of record. */
private fun DrillTarget.screen(): Screen.Drill = when (this) {
    is DrillTarget.Client -> Screen.DrillClient
    is DrillTarget.Supplier -> Screen.DrillSupplier
    is DrillTarget.Bon -> Screen.DrillBon
    is DrillTarget.Vente -> Screen.DrillVente
    is DrillTarget.Perte -> Screen.DrillPerte
    is DrillTarget.Inventaire -> Screen.DrillInventaire
    is DrillTarget.RetoursClient -> Screen.DrillRetoursClient
    is DrillTarget.RetoursFournisseur -> Screen.DrillRetoursFournisseur
    is DrillTarget.Produit -> Screen.DrillProduit
    is DrillTarget.Tournee -> Screen.DrillTournee
}

/**
 * Opens a record from any screen, whichever section's NavHost it sits in: `LocalDrillDown.current(
 * DrillTarget.Client(id))`. Provided once, at the root; a nested NavHost cannot reach another section's
 * routes, the root can. Outside a provider — a preview — it does nothing.
 */
val LocalDrillDown = staticCompositionLocalOf<(DrillTarget) -> Unit> { {} }

/** The argument every drill route carries its record's id in. */
const val DRILL_ID = "id"

/**
 * Opens [target] on the root [navController], on top of the screen that asked.
 *
 * - If that very record is already open somewhere on the stack — report → client → bon → the same
 *   client — the stack is popped back to it rather than given a copy, so Back can never loop.
 * - Otherwise its route is pushed. A second tap finds it open and does nothing, so a double tap
 *   opens it once.
 */
fun drillDown(navController: NavHostController, target: DrillTarget) {
    val screen = target.screen()
    val open = navController.currentBackStack.value.lastOrNull { entry ->
        entry.destination.route == screen.route && entry.arguments?.getInt(DRILL_ID) == target.id
    }
    if (open != null) {
        while (navController.currentBackStackEntry?.id != open.id && navController.popBackStack()) Unit
    } else {
        navController.navigate(screen.createRoute(target.id))
    }
}

/**
 * Back within a section that may have been opened on a record: pops this NavHost when it has a screen
 * to go back to, and otherwise leaves it through [exit] — a drill-down started on a detail has nothing
 * under it, and popping its only screen would leave a blank page.
 */
fun NavController.popOr(exit: () -> Unit) {
    if (previousBackStackEntry != null) popBackStack() else exit()
}
