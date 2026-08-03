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
import com.distrigo.app.ui.pertes.PerteFormScreen
import com.distrigo.app.ui.pertes.PerteListScreen
import com.distrigo.app.ui.pertes.PerteViewModel
import com.distrigo.app.ui.pertes.PertesScreen

@Composable
fun PertesNavHost(onFullScreenChange: (Boolean) -> Unit = {}) {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = Screen.PertesHome.route,
        route            = Screen.PertesGraph.route
    ) {
        composable(Screen.PertesHome.route) {
            val viewModel: PerteViewModel = viewModel(navController.getBackStackEntry(Screen.PertesGraph.route))
            PertesScreen(
                viewModel   = viewModel,
                onTypeClick = { typeId -> navController.navigate(Screen.PertesList.createRoute(typeId)) }
            )
        }

        composable(
            route     = Screen.PertesList.route,
            arguments = listOf(navArgument("typeId") { type = NavType.IntType })
        ) { entry ->
            val viewModel: PerteViewModel = viewModel(navController.getBackStackEntry(Screen.PertesGraph.route))
            val typeId = entry.arguments!!.getInt("typeId")
            PerteListScreen(
                typeId      = typeId,
                viewModel   = viewModel,
                onBack      = { navController.popBackStack() },
                onAddPerte  = { navController.navigate(Screen.PertesForm.createRoute(typeId)) },
                onEditPerte = { perte -> navController.navigate(Screen.PertesForm.createRoute(typeId, perte.id)) }
            )
        }

        composable(
            route     = Screen.PertesForm.route,
            arguments = listOf(
                navArgument("typeId")  { type = NavType.IntType },
                navArgument("perteId") { type = NavType.IntType; defaultValue = -1 }
            )
        ) { entry ->
            val viewModel: PerteViewModel = viewModel(navController.getBackStackEntry(Screen.PertesGraph.route))
            val typeId    = entry.arguments!!.getInt("typeId")
            val perteId   = entry.arguments!!.getInt("perteId").takeIf { it != -1 }
            val pertes by viewModel.pertes.collectAsState()
            val perte = perteId?.let { id -> pertes.find { it.id == id } }

            PerteFormScreen(
                typeId    = typeId,
                perte     = perte,
                viewModel = viewModel,
                onBack    = { navController.popBackStack() },
                onSaved   = { navController.popBackStack() }
            )
        }
    }
}