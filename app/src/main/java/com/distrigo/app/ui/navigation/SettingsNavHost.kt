package com.distrigo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.distrigo.app.ui.settings.ParametresScreen
import com.distrigo.app.ui.settings.data.DataBackupScreen
import com.distrigo.app.ui.settings.data.export.ExportScreen
import com.distrigo.app.ui.settings.data.importer.ImportScreen
import com.distrigo.app.ui.settings.incentive.CommissionPolicyScreen
import com.distrigo.app.ui.settings.receipt.ReceiptSettingsScreen
import com.distrigo.app.ui.settings.trash.TrashScreen

/**
 * Paramètres and the screens under it, each a destination: Back and the stack are the navigation's, a screen
 * left and returned to is composed again (so it reads its data again), and nothing is held in a boolean.
 */
@Composable
fun SettingsNavHost(onBack: () -> Unit) {
    val navController = rememberNavController()

    NavHost(
        navController      = navController,
        startDestination   = Screen.SettingsHome.route,
        route              = Screen.SettingsGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.SettingsHome.route) {
            ParametresScreen(
                onBack       = onBack,
                onReceipt    = { navController.navigate(Screen.SettingsReceipt.route) },
                onCommission = { navController.navigate(Screen.SettingsCommission.route) },
                onData       = { navController.navigate(Screen.SettingsData.route) },
                onTrash      = { navController.navigate(Screen.SettingsTrash.route) }
            )
        }
        composable(Screen.SettingsReceipt.route) {
            ReceiptSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.SettingsCommission.route) {
            CommissionPolicyScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.SettingsData.route) {
            DataBackupScreen(
                onBack   = { navController.popBackStack() },
                onExport = { navController.navigate(Screen.SettingsExport.route) },
                onImport = { navController.navigate(Screen.SettingsImport.route) }
            )
        }
        composable(Screen.SettingsExport.route) {
            ExportScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.SettingsImport.route) {
            ImportScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.SettingsTrash.route) {
            TrashScreen(onBack = { navController.popBackStack() })
        }
    }
}
