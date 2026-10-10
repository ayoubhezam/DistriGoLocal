package com.distrigo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.distrigo.app.diagnostics.rememberTrackedNavController
import com.distrigo.app.ui.account.AccountViewModel
import com.distrigo.app.ui.account.CompteScreen
import com.distrigo.app.ui.account.SignInScreen
import com.distrigo.app.ui.account.SignUpScreen

/** Compte, and its sign-in and sign-up screens, sharing one AccountViewModel. */
@Composable
fun AccountNavHost(onBack: () -> Unit) {
    val navController = rememberTrackedNavController()
    val viewModel: AccountViewModel = hiltViewModel()
    val backToCompte: () -> Unit = { navController.popBackStack(Screen.AccountHome.route, inclusive = false) }

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
                viewModel = viewModel,
                onBack    = onBack,
                onSignIn  = { navController.navigate(Screen.AccountSignIn.route) { launchSingleTop = true } },
                onSignUp  = { navController.navigate(Screen.AccountSignUp.route) { launchSingleTop = true } },
            )
        }
        composable(Screen.AccountSignIn.route) {
            SignInScreen(
                viewModel  = viewModel,
                onBack     = backToCompte,
                onSignUp   = {
                    navController.navigate(Screen.AccountSignUp.route) { popUpTo(Screen.AccountHome.route) }
                },
                onSignedIn = backToCompte,
            )
        }
        composable(Screen.AccountSignUp.route) {
            SignUpScreen(
                viewModel  = viewModel,
                onBack     = backToCompte,
                onSignIn   = {
                    navController.navigate(Screen.AccountSignIn.route) { popUpTo(Screen.AccountHome.route) }
                },
                onSignedIn = backToCompte,
            )
        }
    }
}
