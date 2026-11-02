package com.distrigo.app.diagnostics

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The last screens shown, oldest first, for a report to say where the app was: a crash's stack trace
 * names the code that failed, rarely the screen the rep was on.
 */
object Breadcrumbs {

    private const val MAX = 20
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val recent = ArrayDeque<String>(MAX)

    fun record(what: String) {
        val line = "${LocalTime.now().format(TIME)}  $what"
        synchronized(recent) {
            if (recent.size == MAX) recent.removeFirst()
            recent.addLast(line)
        }
    }

    /** The screens as lines, oldest first; empty if none was recorded. */
    fun snapshot(): List<String> = synchronized(recent) { recent.toList() }
}

/**
 * A NavHost's controller that never pops its first screen. Back actions call a bare popBackStack() (and a screen's own
 * BackHandler too); a second one while the screen slides away — a double tap on the back arrow, Back pressed twice —
 * popped the host's start destination and left the tab blank under its bottom bar. Nothing pops a host empty on purpose
 * (switchToTab and the drill-down loops stop at the first screen), so refusing it here guards every host at once.
 */
private class FirstScreenKeepingNavController(context: Context) : NavHostController(context) {
    override fun popBackStack(): Boolean = previousBackStackEntry != null && super.popBackStack()
}

private fun createNavController(context: Context) = FirstScreenKeepingNavController(context).apply {
    navigatorProvider.addNavigator(ComposeNavigator())
    navigatorProvider.addNavigator(DialogNavigator())
}

/** As navigation-compose's rememberNavController (2.8): kept across a rebuild, and through a saved instance state. */
private fun navControllerSaver(context: Context) = Saver<FirstScreenKeepingNavController, Bundle>(
    save    = { it.saveState() },
    restore = { createNavController(context).apply { restoreState(it) } },
)

/** navigation-compose's rememberNavController with a [FirstScreenKeepingNavController], each screen it shows recorded in [Breadcrumbs]. */
@Composable
fun rememberTrackedNavController(): NavHostController {
    val context = LocalContext.current
    val navController = rememberSaveable(saver = navControllerSaver(context)) { createNavController(context) }
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, arguments ->
            val args = arguments?.keySet()?.filter { it != "android-support-nav:controller:deepLinkIntent" }
                ?.joinToString(", ") { "$it=${arguments.get(it)}" }.orEmpty()
            Breadcrumbs.record(destination.route.orEmpty() + if (args.isEmpty()) "" else " ($args)")
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    return navController
}
