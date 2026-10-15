package com.distrigo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.distrigo.app.diagnostics.rememberTrackedNavController
import com.distrigo.app.ui.account.AccountViewModel
import com.distrigo.app.ui.account.CompteScreen
import com.distrigo.app.ui.admin.AdminHomeScreen
import com.distrigo.app.ui.admin.AdminViewModel
import com.distrigo.app.ui.admin.ClientsScreen
import com.distrigo.app.ui.admin.CreatedCodesScreen
import com.distrigo.app.ui.admin.NewBatchScreen
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
                onOpenAdmin      = { navController.navigate(Screen.AdminHome.route) { launchSingleTop = true } },
            )
        }
        // The owner's « Administration » (C4): its view model lives with its own entry, so opening Compte never
        // asks codes-admin anything; the new batch and its codes share it.
        composable(Screen.AdminHome.route) { entry ->
            AdminHomeScreen(
                viewModel  = hiltViewModel(entry),
                onBack     = { navController.popBackStack() },
                onNewBatch = { navController.navigate(Screen.AdminNewBatch.route) { launchSingleTop = true } },
                onClients  = { navController.navigate(Screen.AdminClients.route) { launchSingleTop = true } },
            )
        }
        composable(Screen.AdminClients.route) { entry ->
            val admin: AdminViewModel = hiltViewModel(remember(entry) { navController.getBackStackEntry(Screen.AdminHome.route) })
            ClientsScreen(viewModel = admin, onBack = { navController.popBackStack() })
        }
        composable(Screen.AdminNewBatch.route) { entry ->
            val admin: AdminViewModel = hiltViewModel(remember(entry) { navController.getBackStackEntry(Screen.AdminHome.route) })
            NewBatchScreen(
                viewModel = admin,
                onBack    = { navController.popBackStack() },
                // Back from the codes goes to the batches, not to the form
                onCreated = {
                    navController.navigate(Screen.AdminCreated.route) {
                        popUpTo(Screen.AdminNewBatch.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Screen.AdminCreated.route) { entry ->
            val admin: AdminViewModel = hiltViewModel(remember(entry) { navController.getBackStackEntry(Screen.AdminHome.route) })
            CreatedCodesScreen(
                viewModel = admin,
                // Once: leaving, the screen is still drawn after its codes are forgotten, and asks again
                onDone    = {
                    if (navController.currentBackStackEntry?.destination?.route == Screen.AdminCreated.route) navController.popBackStack()
                },
            )
        }
    }
}
