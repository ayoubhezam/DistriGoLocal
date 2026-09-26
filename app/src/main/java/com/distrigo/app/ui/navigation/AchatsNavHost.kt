package com.distrigo.app.ui.navigation

import com.distrigo.app.diagnostics.rememberTrackedNavController
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.compose.runtime.rememberCoroutineScope
import com.distrigo.app.ui.purchases.*
import kotlinx.coroutines.launch

/** Opens the purchase form graph, carrying the resume decision made before it is entered. */
@Composable
private fun openFormAction(navController: androidx.navigation.NavHostController): (Int?, Int?) -> Unit =
    { orderId, draftId ->
        navController.navigate(Screen.PurchaseFormGraph.createRoute(orderId = orderId, draftId = draftId))
    }

/**
 * "Modifier" on a bon has to ask whether that bon already has an unsaved edit draft before it opens
 * a second, invisible one — otherwise two parallel edits race and whichever saves last silently
 * wins. Both entry points into edit mode go through here.
 */
@Composable
private fun editOrderAction(
    viewModel  : PurchaseViewModel,
    openForm   : (Int?, Int?) -> Unit,
    resumeDraft: (com.distrigo.app.data.model.PurchaseDraft) -> Unit
): (Int) -> Unit {
    val scope = rememberCoroutineScope()
    return { orderId ->
        scope.launch {
            val existing = viewModel.draftForOrder(orderId)
            if (existing == null) openForm(orderId, null) else resumeDraft(existing)
        }
        Unit
    }
}

@Composable
fun AchatsNavHost(
    onFullScreenChange   : (Boolean) -> Unit = {},
    onOpenMenu           : (() -> Unit)? = null,
    onNotificationsClick : () -> Unit = {},
    onProfileClick       : () -> Unit = {}
) {
    val navController = rememberTrackedNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        onFullScreenChange(currentRoute != null && currentRoute != Screen.AchatsHome.route)
    }

    NavHost(
        navController      = navController,
        startDestination   = Screen.AchatsHome.route,
        route              = Screen.AchatsGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.AchatsHome.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.AchatsGraph.route) }
            val viewModel: PurchaseViewModel = hiltViewModel(parentEntry)
            val openForm = openFormAction(navController)
            val resumeDraft = draftResumeAction(
                viewModel   = viewModel,
                onOpenForm  = openForm,
                onViewOrder = { orderId -> navController.navigate(Screen.AchatsDetail.createRoute(orderId)) }
            )
            val editOrder = editOrderAction(viewModel, openForm, resumeDraft)

            PurchasesScreen(
                viewModel          = viewModel,
                onOpenMenu           = onOpenMenu,
                onNotificationsClick = onNotificationsClick,
                onProfileClick       = onProfileClick,

                onFullScreenChange = onFullScreenChange,
                onAddOrder         = { openForm(null, null) },
                onEditOrder        = editOrder,
                onOrderClick       = { orderId -> navController.navigate(Screen.AchatsDetail.createRoute(orderId)) },
                onResumeDraft      = resumeDraft,
                onOpenBrouillons   = { navController.navigate(Screen.AchatsBrouillons.route) }
            )
        }

        composable(Screen.AchatsBrouillons.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.AchatsGraph.route) }
            val viewModel: PurchaseViewModel = hiltViewModel(parentEntry)
            val resumeDraft = draftResumeAction(
                viewModel   = viewModel,
                onOpenForm  = openFormAction(navController),
                onViewOrder = { orderId -> navController.navigate(Screen.AchatsDetail.createRoute(orderId)) }
            )
            BrouillonsScreen(
                viewModel = viewModel,
                onBack    = { navController.popBackStack() },
                onResume  = resumeDraft
            )
        }

        purchaseFormGraph(
            navController     = navController,
            graphRoute        = Screen.PurchaseFormGraph.route,
            viewModel         = { hiltViewModel(remember(navController) { navController.getBackStackEntry(Screen.AchatsGraph.route) }) },
            productViewModel  = { hiltViewModel() },
            supplierViewModel = { hiltViewModel() },
            onBack  = { navController.popBackStack(Screen.PurchaseFormGraph.route, inclusive = true) },
            onSaved = { navController.popBackStack(Screen.PurchaseFormGraph.route, inclusive = true) }
        )

        composable(
            route     = Screen.AchatsDetail.route,
            arguments = listOf(navArgument("orderId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.AchatsGraph.route) }
            val viewModel: PurchaseViewModel = hiltViewModel(parentEntry)
            val orderId = entry.arguments!!.getInt("orderId")
            // Observed on its own: the list holds only the page on screen, so the bon may not be in it.
            val lookup by remember(orderId) { viewModel.observeOrder(orderId) }
                .collectAsState(initial = OrderLookup.Loading)

            LaunchedEffect(orderId) { viewModel.loadOrderDetail(orderId) }

            val openForm = openFormAction(navController)
            val editOrder = editOrderAction(
                viewModel   = viewModel,
                openForm    = openForm,
                resumeDraft = draftResumeAction(
                    viewModel   = viewModel,
                    onOpenForm  = openForm,
                    onViewOrder = { }   // already on the bon
                )
            )

            val found = lookup as? OrderLookup.Found
            if (found != null) {
                PurchaseOrderDetailScreen(
                    order      = found.order,
                    viewModel  = viewModel,
                    onBack     = { navController.popBackStack() },
                    onEdit     = { editOrder(orderId) },
                    onReceived = { navController.popBackStack() }
                )
            } else if (lookup == OrderLookup.Gone) {
                LeaveWhenGone(navController, entry)
            }
        }
    }
}