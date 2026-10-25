package com.distrigo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    /** A tournée's page: its clients, its sales, the truck (the Dashboard's « Tournée du jour »). */
    data class Tournee(override val id: Int) : DrillTarget
    /** A secteur's page: its quartiers (Paramètres › Secteurs). */
    data class Secteur(override val id: Int) : DrillTarget
    /** A quartier's page: its clients. */
    data class Quartier(override val id: Int) : DrillTarget
}

/** What a page notes it shows: its kind and id, as « Client:10 ». */
internal val DrillTarget.key: String get() = "${this::class.simpleName}:$id"

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
    is DrillTarget.Secteur -> Screen.DrillSecteur
    is DrillTarget.Quartier -> Screen.DrillQuartier
}

/**
 * Opens a record from any screen, whichever section's NavHost it sits in: `LocalDrillDown.current(
 * DrillTarget.Client(id))`. Provided once, at the root; a nested NavHost cannot reach another section's
 * routes, the root can. Outside a provider — a preview — it does nothing.
 */
val LocalDrillDown = staticCompositionLocalOf<DrillDown> { DrillDown.None }

/** The root's drill-down: opens a record, and hears which record a section's page shows. */
interface DrillDown {
    /** Opens [target]: back to it if it is already open on the stack, on top of the screen otherwise. */
    operator fun invoke(target: DrillTarget)

    /**
     * A record's page, inside a section, says it is on screen ([ShowsRecord]), so opening that record again goes back to
     * it rather than opening a copy. Returns what to run when the page leaves.
     */
    fun showing(target: DrillTarget): () -> Unit

    /** Outside a provider: nothing opens, nothing is noted. */
    object None : DrillDown {
        override fun invoke(target: DrillTarget) = Unit
        override fun showing(target: DrillTarget): () -> Unit = {}
    }
}

/**
 * A record's page inside a section (a client opened from the Clients list, a quartier from its secteur) is not a drill
 * route, so [drillDown] cannot see it by its route. It notes the record it shows on its section's root entry instead, for
 * as long as it is that section's page: covered by another section, the note stays; left for another page of its own
 * section, it goes.
 */
@Composable
fun ShowsRecord(target: DrillTarget) {
    val drill = LocalDrillDown.current
    DisposableEffect(drill, target.key) {
        val leave = drill.showing(target)
        onDispose(leave)
    }
}

/** [DrillDown] on the root [navController]. */
class RootDrillDown(private val navController: NavHostController) : DrillDown {
    override fun invoke(target: DrillTarget) = drillDown(navController, target)

    override fun showing(target: DrillTarget): () -> Unit {
        // The page is composing now, so the root's current entry is the section it sits in
        val entry = navController.currentBackStackEntry ?: return {}
        val notes = runCatching { entry.savedStateHandle }.getOrNull() ?: return {}
        notes[SHOWN] = target.key
        return {
            // Left for another page of the same section, not covered by another section: the note goes, unless the
            // next page has written its own already
            if (navController.currentBackStackEntry === entry && notes.get<String>(SHOWN) == target.key) notes.remove<String>(SHOWN)
        }
    }
}

/** The key of the note a section's root entry keeps of the record its page shows. */
private const val SHOWN = "drill_shown"

/** One root back-stack entry as [reopenIndex] reads it: its route, its drill id if it is a drill route, its note. */
internal data class OpenEntry(val route: String?, val drillId: Int?, val shown: String?)

/**
 * Where [target] is already open on [stack], as a drill route of [route] or as a section's page that noted it ([ShowsRecord]):
 * the nearest such entry, null when nowhere. A drill route whose section has moved on to another record (a secteur's
 * page, then one of its quartiers) no longer shows its own, so it does not count for it. Pure, so it is tested without a
 * device.
 */
internal fun reopenIndex(stack: List<OpenEntry>, target: DrillTarget, route: String): Int? =
    stack.indexOfLast {
        it.shown == target.key || (it.route == route && it.drillId == target.id && it.shown == null)
    }.takeIf { it >= 0 }

/** The argument every drill route carries its record's id in. */
const val DRILL_ID = "id"

/**
 * Opens [target] on the root [navController], on top of the screen that asked.
 *
 * - If that very record is already open somewhere on the stack — report → client → bon → the same
 *   client — the stack is popped back to it rather than given a copy, so Back can never loop. Open
 *   means by its drill route, or as a section's page that said so ([ShowsRecord]): a client opened from
 *   the Clients list, then its quartier, then the same client, is the first page again.
 * - Otherwise its route is pushed. A second tap finds it open and does nothing, so a double tap
 *   opens it once.
 */
fun drillDown(navController: NavHostController, target: DrillTarget) {
    val screen = target.screen()
    val stack = navController.currentBackStack.value
    val read = stack.map { entry ->
        val route = entry.destination.route
        OpenEntry(
            route   = route,
            drillId = entry.arguments?.takeIf { route == screen.route && it.containsKey(DRILL_ID) }?.getInt(DRILL_ID),
            shown   = runCatching { entry.savedStateHandle.get<String>(SHOWN) }.getOrNull(),
        )
    }
    val open = reopenIndex(read, target, screen.route)?.let(stack::get)
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
