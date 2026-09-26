package com.distrigo.app.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
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

/** [rememberNavController], with each screen it shows recorded in [Breadcrumbs]. */
@Composable
fun rememberTrackedNavController(): NavHostController {
    val navController = rememberNavController()
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
