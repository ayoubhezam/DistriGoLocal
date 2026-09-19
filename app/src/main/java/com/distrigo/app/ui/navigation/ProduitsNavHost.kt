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
import androidx.navigation.compose.currentBackStackEntryAsState
import com.distrigo.app.ui.mouvements.MouvementsScreen
import androidx.navigation.compose.navigation
import com.distrigo.app.ui.mouvements.*
@Composable
fun ProduitsNavHost(
    onFullScreenChange   : (Boolean) -> Unit = {},
    onOpenMenu           : (() -> Unit)? = null,
    onNotificationsClick : () -> Unit = {},
    onProfileClick       : () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // Two things can want the bottom nav out of the way: being on any route other than the list,
    // and the list itself opening the photo viewer over the top of it. Combining them here keeps
    // one source of truth — two separate callers would race, and whichever fired last would win.
    var photoViewerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(currentRoute, photoViewerOpen) {
        onFullScreenChange(currentRoute != Screen.ProduitsHome.route || photoViewerOpen)
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
                onOpenMenu           = onOpenMenu,
                onNotificationsClick = onNotificationsClick,
                onProfileClick       = onProfileClick,
                onFullScreenChange   = { photoViewerOpen = it },

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

            if (product != null) {
                ProductDetailScreen(
                    product         = product,
                    viewModel       = viewModel,
                    onBack          = { navController.popBackStack() },
                    // Deletes, and does not pop. The screen owns the confirmation, and the branch
                    // below already pops once the product is gone from the catalogue.
                    //
                    // Doing both raced, and the race is what left the Produits tab blank: the row
                    // vanished, `product` went null, the branch below popped the detail — and the
                    // pop that used to sit here took a second destination off the stack with it.
                    // A second dialog lived here too, asking what the screen had just asked.
                    onDelete        = { viewModel.deleteProduct(productId) },
                    onEdit          = { navController.navigate(Screen.ProduitsForm.createRoute(productId)) },
                    onViewMovements = { navController.navigate(Screen.ProduitsMovementsGraph.createRoute(productId)) },
                    // The detail lives inside the movements graph, which has to be entered first:
                    // Back from it then passes through the full list on its way to the product.
                    onOpenMovement  = { movementId ->
                        navController.navigate(Screen.ProduitsMovementsGraph.createRoute(productId))
                        navController.navigate(Screen.ProduitsMovementDetail.createRoute(movementId))
                    }
                )
            } else {
                LeaveWhenGone(navController, entry)
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
                    LeaveWhenGone(navController, entry)
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
    }


}