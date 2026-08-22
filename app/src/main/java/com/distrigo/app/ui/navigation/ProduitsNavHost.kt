package com.distrigo.app.ui.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.distrigo.app.ui.products.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.distrigo.app.ui.designsystem.DsColors
import androidx.compose.material3.Text
import androidx.navigation.compose.currentBackStackEntryAsState
import com.distrigo.app.ui.mouvements.MouvementsScreen
import androidx.navigation.compose.navigation
import com.distrigo.app.ui.mouvements.*
@Composable
fun ProduitsNavHost(onFullScreenChange: (Boolean) -> Unit = {}) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        onFullScreenChange(currentRoute != Screen.ProduitsHome.route)
    }
    NavHost(
        navController      = navController,
        startDestination   = Screen.ProduitsHome.route,
        route              = Screen.ProduitsGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.ProduitsHome.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ProduitsGraph.route) }
            val viewModel: ProductViewModel = hiltViewModel(parentEntry)
            ProductsScreen(
                viewModel      = viewModel,
                onAddProduct   = { navController.navigate(Screen.ProduitsForm.createRoute()) },
                onEditProduct  = { productId -> navController.navigate(Screen.ProduitsForm.createRoute(productId)) },
                onProductClick = { productId -> navController.navigate(Screen.ProduitsDetail.createRoute(productId)) }
            )
        }

        composable(
            route     = Screen.ProduitsForm.route,
            arguments = listOf(navArgument("productId") { type = NavType.IntType; defaultValue = -1 })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ProduitsGraph.route) }
            val viewModel: ProductViewModel = hiltViewModel(parentEntry)
            val productId = entry.arguments!!.getInt("productId").takeIf { it != -1 }
            val products by viewModel.products.collectAsState()
            val product = productId?.let { id -> products.find { it.id == id } }

            ProductFormScreen(
                product   = product,
                viewModel = viewModel,
                onBack    = { navController.popBackStack() },
                onSaved   = { navController.popBackStack() }
            )
        }

        composable(
            route     = Screen.ProduitsDetail.route,
            arguments = listOf(navArgument("productId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ProduitsGraph.route) }
            val viewModel: ProductViewModel = hiltViewModel(parentEntry)
            val productId = entry.arguments!!.getInt("productId")
            val products by viewModel.products.collectAsState()
            val product = products.find { it.id == productId }
            var showDeleteConfirm by remember { mutableStateOf(false) }

            if (product != null) {
                ProductDetailScreen(
                    product         = product,
                    viewModel       = viewModel,
                    onBack          = { navController.popBackStack() },
                    onDelete        = { showDeleteConfirm = true },
                    onEdit          = { navController.navigate(Screen.ProduitsForm.createRoute(productId)) },
                    onViewMovements = { navController.navigate(Screen.ProduitsMovementsGraph.createRoute(productId)) },
                    onInfoGenerales = { navController.navigate(Screen.ProduitsInfoGenerales.createRoute(productId)) },
                    onStockPrix     = { navController.navigate(Screen.ProduitsStockPrix.createRoute(productId)) }
                )
                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Supprimer le produit") },
                        text  = { Text("Voulez-vous supprimer \"${product.name}\" ?") },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.deleteProduct(productId)
                                showDeleteConfirm = false
                                navController.popBackStack()
                            }) { Text("Supprimer", color = DsColors.Danger) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") }
                        },
                        containerColor    = DsColors.Surface,
                        titleContentColor = DsColors.TextPrimary,
                        textContentColor  = DsColors.TextSecondary
                    )
                }
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }

        navigation(
            startDestination = Screen.ProduitsMovementsList.route,
            route            = Screen.ProduitsMovementsGraph.route,
            arguments        = listOf(navArgument("productId") { type = NavType.IntType })
        ) {
            composable(Screen.ProduitsMovementsList.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ProduitsMovementsGraph.route) }
                val movementsViewModel: StockMovementViewModel = hiltViewModel(parentEntry)
                val movementsProductId = parentEntry.arguments!!.getInt("productId")
                val parentProductsEntry = remember(entry) { navController.getBackStackEntry(Screen.ProduitsGraph.route) }
                val productsViewModel: ProductViewModel = hiltViewModel(parentProductsEntry)
                val products by productsViewModel.products.collectAsState()
                val product = products.find { it.id == movementsProductId }

                if (product != null) {
                    MouvementsScreen(
                        product         = product,
                        viewModel       = movementsViewModel,
                        onBack          = { navController.popBackStack() },
                        onMovementClick = { movement -> navController.navigate(Screen.ProduitsMovementDetail.createRoute(movement.id)) },
                        onFilterClick   = { navController.navigate(Screen.ProduitsMovementFilters.route) }
                    )
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }

            composable(Screen.ProduitsMovementFilters.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ProduitsMovementsGraph.route) }
                val movementsViewModel: StockMovementViewModel = hiltViewModel(parentEntry)
                val sources by movementsViewModel.availableSources.collectAsState()
                MovementFiltersView(
                    viewModel        = movementsViewModel,
                    availableSources = sources,
                    onBack           = { navController.popBackStack() }
                )
            }

            composable(
                route     = Screen.ProduitsMovementDetail.route,
                arguments = listOf(navArgument("movementId") { type = NavType.IntType })
            ) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ProduitsMovementsGraph.route) }
                val movementsViewModel: StockMovementViewModel = hiltViewModel(parentEntry)
                val movementId = entry.arguments!!.getInt("movementId")
                MovementDetailView(
                    movementId = movementId,
                    viewModel  = movementsViewModel,
                    onBack     = { navController.popBackStack() }
                )
            }
        }

        composable(
            route     = Screen.ProduitsInfoGenerales.route,
            arguments = listOf(navArgument("productId") { type = NavType.IntType })
        ) { entry ->
            val productId = entry.arguments!!.getInt("productId")
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ProduitsGraph.route) }
            val viewModel: ProductViewModel = hiltViewModel(parentEntry)
            val products by viewModel.products.collectAsState()
            val product = products.find { it.id == productId }

            if (product != null) {
                InfoGeneralesDetailScreen(product = product, onBack = { navController.popBackStack() })
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }

        composable(
            route     = Screen.ProduitsStockPrix.route,
            arguments = listOf(navArgument("productId") { type = NavType.IntType })
        ) { entry ->
            val productId = entry.arguments!!.getInt("productId")
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ProduitsGraph.route) }
            val viewModel: ProductViewModel = hiltViewModel(parentEntry)
            val products by viewModel.products.collectAsState()
            val product = products.find { it.id == productId }

            LaunchedEffect(productId) { viewModel.loadPriceHistory(productId) }
            val priceHistory by viewModel.priceHistory.collectAsState()

            if (product != null) {
                StockPrixDetailScreen(
                    product      = product,
                    priceHistory = priceHistory,
                    onBack       = { navController.popBackStack() },
                    onEdit       = { navController.navigate(Screen.ProduitsForm.createRoute(productId)) }
                )
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
    }


}