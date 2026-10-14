package com.distrigo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.distrigo.app.diagnostics.rememberTrackedNavController
import com.distrigo.app.ui.account.AccountViewModel
import com.distrigo.app.ui.account.CompteScreen
import com.distrigo.app.ui.onboarding.OnboardingStart

/**
 * Compte, the account and this phone's subscription. Signing in, signing up and activation codes are the
 * onboarding's (ui/onboarding): [onOpenOnboarding] opens it on that screen, drawn by the activity above everything.
 */
@Composable
fun AccountNavHost(onBack: () -> Unit, onOpenOnboarding: (OnboardingStart) -> Unit) {
    val navController = rememberTrackedNavController()
    val viewModel: AccountViewModel = hiltViewModel()

    NavHost(
        navController      = navController,
        startDestination   = Screen.AccountHome.route,
        route              = Screen.AccountGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.AccountHome.route) {
            CompteScreen(
                viewModel        = viewModel,
                onBack           = onBack,
                onOpenOnboarding = onOpenOnboarding,
            )
        }
    }
}
