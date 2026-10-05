package com.distrigo.app.ui.navigation

import com.distrigo.app.data.model.Quantity
import com.distrigo.app.diagnostics.rememberTrackedNavController
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import com.distrigo.app.ui.products.ProductLookup
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

import com.distrigo.app.data.model.hasBarcode
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.inventory.*
import com.distrigo.app.ui.scanner.BarcodeScannerScreen
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.products.ProductViewModel

@Composable
fun InventoryNavHost(
    onBack: () -> Unit,
    onFullScreenChange: (Boolean) -> Unit = {},
    /** Opens the section on that session's detail instead of the history — a drill-down: Back then leaves. */
    openSessionId: Int? = null,
) {
    val navController = rememberTrackedNavController()

    NavHost(
        navController      = navController,
        startDestination   = if (openSessionId != null) Screen.InventaireDetail.route else Screen.InventaireHome.route,
        route              = Screen.InventaireGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.InventaireHome.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
            val viewModel: InventoryViewModel = hiltViewModel(parentEntry)
            InventoryHistoryScreen(
                viewModel      = viewModel,
                onBack         = onBack,
                onSessionClick = { historyEntry ->
                    if (historyEntry.session.status == "draft") {
                        navController.navigate(Screen.InventaireSessionGraph.route)
                    } else {
                        navController.navigate(Screen.InventaireDetail.createRoute(historyEntry.session.id))
                    }
                },
                onAddNew = { navController.navigate(Screen.InventaireSessionGraph.route) }
            )
        }

        composable(
            route     = Screen.InventaireDetail.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.IntType; openSessionId?.let { defaultValue = it } })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
            val viewModel: InventoryViewModel = hiltViewModel(parentEntry)
            val sessionId = entry.arguments!!.getInt("sessionId")
            InventorySessionDetailScreen(
                sessionId = sessionId,
                viewModel = viewModel,
                onBack    = { navController.popOr(onBack) }
            )
        }

        navigation(
            startDestination = Screen.InventaireSessionScan.route,
            route            = Screen.InventaireSessionGraph.route
        ) {
            // The count: the products still to count, a centred dialog to count one, and the selection.
            composable(Screen.InventaireSessionScan.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
                val viewModel: InventoryViewModel = hiltViewModel(parentEntry)
                val productViewModel: ProductViewModel = hiltViewModel()

                LaunchedEffect(Unit) { viewModel.startOrResumeSession() }

                val activeSession by viewModel.activeSession.collectAsState()
                val counts by viewModel.counts.collectAsState()
                val products = viewModel.productList.items.collectAsLazyPagingItems()
                val remaining by viewModel.productList.count.collectAsState()
                val categories by productViewModel.categories.collectAsState()
                val sousCategories by productViewModel.sousCategories.collectAsState()
                val marques by productViewModel.marques.collectAsState()
                val suppliers by productViewModel.suppliers.collectAsState()
                val scope = rememberCoroutineScope()

                var showScanner by remember { mutableStateOf(false) }
                var counting    by remember { mutableStateOf<Product?>(null) }
                var isSaving    by remember { mutableStateOf(false) }
                var saveError   by remember { mutableStateOf("") }
                var message     by remember { mutableStateOf("") }

                fun exitSession() {
                    viewModel.loadHistory()
                    navController.popBackStack(Screen.InventaireSessionGraph.route, inclusive = true)
                }

                // Straight to the count dialog — from a row, or from a scan, which skips the list altogether.
                fun count(product: Product) {
                    scope.launch {
                        if (viewModel.isProductAlreadyScanned(product.id)) {
                            message = "« ${product.name} » est déjà inventorié : corrigez-le dans Ma sélection."
                        } else {
                            message = ""; saveError = ""; counting = product
                        }
                    }
                }

                if (showScanner) {
                    BackHandler { showScanner = false }
                    BarcodeScannerScreen(
                        onBarcodeScanned = { code ->
                            showScanner = false
                            scope.launch {
                                val product = viewModel.productByBarcode(code)
                                if (product == null) message = "Aucun produit trouvé pour ce code-barres" else count(product)
                            }
                        },
                        onClose = { showScanner = false }
                    )
                } else {
                    BackHandler { exitSession() }
                    InventoryCountScreen(
                        numero          = activeSession?.let { inventoryNumero(it.id) } ?: "",
                        products        = products,
                        remaining       = remaining,
                        search          = viewModel.productSearch,
                        onSearchChange  = { viewModel.productSearch = it },
                        filters         = viewModel.productFilters,
                        onFiltersChange = { viewModel.productFilters = it },
                        categories      = categories,
                        sousCategories  = sousCategories,
                        marques         = marques,
                        suppliers       = suppliers,
                        countedCount    = counts.total_products,
                        ecartsValue     = counts.total_value_ecarts,
                        message         = message,
                        onBack          = { exitSession() },
                        onScan          = { showScanner = true },
                        onProductClick  = { count(it) },
                        onOpenSelection = { navController.navigate(Screen.InventaireSessionReview.route) },
                    )
                    counting?.let { product ->
                        InventoryCountDialog(
                            product   = product,
                            isSaving  = isSaving,
                            error     = saveError,
                            onSave    = { qte ->
                                isSaving = true
                                viewModel.recordScan(
                                    productId = product.id, qtePhysique = qte,
                                    onSuccess = { _, _, _ -> isSaving = false; saveError = ""; counting = null },
                                    onError   = { msg -> isSaving = false; saveError = msg }
                                )
                            },
                            onDismiss = { counting = null }
                        )
                    }
                }
            }

            // Ma sélection: the products counted, to correct or remove, then on to the summary.
            composable(Screen.InventaireSessionReview.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
                val viewModel: InventoryViewModel = hiltViewModel(parentEntry)
                // Paged and live while listed: collected here only, so the counts themselves never reload it.
                val items = remember { viewModel.sessionItemPages() }.collectAsLazyPagingItems()
                val counts by viewModel.counts.collectAsState()
                var error by remember { mutableStateOf("") }

                InventoryCartScreen(
                    items    = items,
                    count    = counts.total_products,
                    error    = error,
                    onEdit   = { item, qte -> viewModel.updateScan(item.id, qte, onSuccess = { error = "" }, onError = { error = it }) },
                    onDelete = { item -> viewModel.deleteScan(item.id, onSuccess = { error = "" }, onError = { error = it }) },
                    onBack   = { navController.popBackStack() },
                    onNext   = { navController.navigate(Screen.InventaireSessionSummary.route) }
                )
            }

            composable(Screen.InventaireSessionSummary.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
                val viewModel: InventoryViewModel = hiltViewModel(parentEntry)
                val summaryPreview by viewModel.counts.collectAsState()
                val uncounted by viewModel.uncountedCount.collectAsState()

                var date         by remember { mutableStateOf(java.time.LocalDate.now()) }
                var choice       by remember { mutableStateOf<UncountedChoice?>(null) }
                var isConfirmed  by remember { mutableStateOf(false) }
                var isConfirming by remember { mutableStateOf(false) }
                var zeroed       by remember { mutableStateOf(0) }
                var confirmError by remember { mutableStateOf("") }

                fun exitToHistory() {
                    viewModel.loadHistory()
                    navController.popBackStack(Screen.InventaireSessionGraph.route, inclusive = true)
                }

                BackHandler(enabled = isConfirmed) { exitToHistory() }

                Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                    InventorySummaryStep(
                        date            = date,
                        onDateChange    = { date = it },
                        summary         = summaryPreview,
                        uncounted       = uncounted,
                        choice          = choice,
                        onChoice        = { choice = it },
                        isConfirmed     = isConfirmed,
                        isConfirming    = isConfirming,
                        zeroed          = zeroed,
                        confirmError    = confirmError,
                        onBack          = { navController.popBackStack() },
                        onConfirm       = {
                            isConfirming = true
                            viewModel.finishSession(
                                zeroUncounted = choice == UncountedChoice.ZERO,
                                date          = date,
                                onSuccess = { n -> isConfirming = false; isConfirmed = true; zeroed = n; confirmError = "" },
                                onError   = { msg -> isConfirming = false; confirmError = msg }
                            )
                        },
                        onReturnHistory = { exitToHistory() }
                    )
                }
            }
        }
    }
}