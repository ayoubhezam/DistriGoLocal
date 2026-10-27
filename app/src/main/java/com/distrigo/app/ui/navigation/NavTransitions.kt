package com.distrigo.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.navigation.NavBackStackEntry

// Start/End, not Left/Right: forward slides toward the start edge, which is the right one in Arabic.
// NavHost's own default (unset enter/exitTransition) is a 700ms cross-fade, not a slide — every
// NavHost in the app passes these explicitly to get the standard push/pop feel instead.
private const val NavTransitionMs = 300

val navEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(NavTransitionMs))
}
val navExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(NavTransitionMs))
}
val navPopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(NavTransitionMs))
}
val navPopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(NavTransitionMs))
}
