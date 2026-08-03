package com.distrigo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.distrigo.app.ui.charges.*
import androidx.compose.runtime.remember

@Composable
fun ChargesNavHost(onFullScreenChange: (Boolean) -> Unit = {}) {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = Screen.ChargesHome.route,
        route            = Screen.ChargesGraph.route
    ) {
        composable(Screen.ChargesHome.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ChargesGraph.route) }
            val viewModel: ChargeViewModel = viewModel(parentEntry)
            ChargesScreen(
                viewModel   = viewModel,
                onTypeClick = { typeId -> navController.navigate(Screen.ChargesSubTypes.createRoute(typeId)) }
            )
        }

        composable(
            route     = Screen.ChargesSubTypes.route,
            arguments = listOf(navArgument("typeId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ChargesGraph.route) }
            val viewModel: ChargeViewModel = viewModel(parentEntry)
            val typeId = entry.arguments!!.getInt("typeId")
            ChargeSubTypesScreen(
                typeId         = typeId,
                viewModel      = viewModel,
                onBack         = { navController.popBackStack() },
                onSubTypeClick = { subtypeId -> navController.navigate(Screen.ChargesList.createRoute(subtypeId)) }
            )
        }

        composable(
            route     = Screen.ChargesList.route,
            arguments = listOf(navArgument("subtypeId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ChargesGraph.route) }
            val viewModel: ChargeViewModel = viewModel(parentEntry)
            val subtypeId = entry.arguments!!.getInt("subtypeId")
            ChargeListScreen(
                subtypeId    = subtypeId,
                viewModel    = viewModel,
                onBack       = { navController.popBackStack() },
                onAddCharge  = { navController.navigate(Screen.ChargesForm.createRoute(subtypeId)) },
                onEditCharge = { charge -> navController.navigate(Screen.ChargesForm.createRoute(subtypeId, charge.id)) }
            )
        }

        composable(
            route     = Screen.ChargesForm.route,
            arguments = listOf(
                navArgument("subtypeId") { type = NavType.IntType },
                navArgument("chargeId")  { type = NavType.IntType; defaultValue = -1 }
            )
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ChargesGraph.route) }
            val viewModel: ChargeViewModel = viewModel(parentEntry)
            val subtypeId = entry.arguments!!.getInt("subtypeId")
            val chargeId  = entry.arguments!!.getInt("chargeId").takeIf { it != -1 }
            val charges by viewModel.charges.collectAsState()
            val charge = chargeId?.let { id -> charges.find { it.id == id } }

            ChargeFormScreen(
                subtypeId = subtypeId,
                charge    = charge,
                viewModel = viewModel,
                onBack    = { navController.popBackStack() },
                onSaved   = {
                    viewModel.loadCharges(subtypeId)
                    viewModel.loadChargeTypes()
                    navController.popBackStack()
                }
            )
        }
    }
}