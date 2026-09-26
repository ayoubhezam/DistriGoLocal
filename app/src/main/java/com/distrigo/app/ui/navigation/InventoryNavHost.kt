package com.distrigo.app.ui.navigation

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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.inventory.*
import com.distrigo.app.ui.scanner.BarcodeScannerScreen

@Composable
fun InventoryNavHost(onBack: () -> Unit, onFullScreenChange: (Boolean) -> Unit = {}) {
    val navController = rememberNavController()

    NavHost(
        navController      = navController,
        startDestination   = Screen.InventaireHome.route,
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
            arguments = listOf(navArgument("sessionId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
            val viewModel: InventoryViewModel = hiltViewModel(parentEntry)
            val sessionId = entry.arguments!!.getInt("sessionId")
            InventorySessionDetailScreen(
                sessionId = sessionId,
                viewModel = viewModel,
                onBack    = { navController.popBackStack() }
            )
        }

        navigation(
            startDestination = Screen.InventaireSessionScan.route,
            route            = Screen.InventaireSessionGraph.route
        ) {
            composable(Screen.InventaireSessionScan.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
                val viewModel: InventoryViewModel = hiltViewModel(parentEntry)

                LaunchedEffect(Unit) { viewModel.startOrResumeSession() }

                val activeSession by viewModel.activeSession.collectAsState()
                val counts   by viewModel.counts.collectAsState()
                val userName by viewModel.userName.collectAsState()
                val scanScope = rememberCoroutineScope()

                var showScanner       by remember { mutableStateOf(false) }
                var showSearchDialog  by remember { mutableStateOf(false) }
                var scanError         by remember { mutableStateOf("") }

                fun exitSession() {
                    viewModel.loadHistory()
                    navController.popBackStack(Screen.InventaireSessionGraph.route, inclusive = true)
                }

                suspend fun openProduct(product: com.distrigo.app.data.model.Product) {
                    if (viewModel.isProductAlreadyScanned(product.id)) {
                        scanError = "\"${product.name}\" a déjà été scanné dans cette session"
                        return
                    }
                    scanError = ""
                    navController.navigate(Screen.InventaireSessionQuantity.createRoute(product.id))
                }

                if (showScanner) {
                    BackHandler { showScanner = false }
                    BarcodeScannerScreen(
                        onBarcodeScanned = { code ->
                            showScanner = false
                            scanScope.launch {
                                val product = viewModel.productByBarcode(code)
                                if (product == null) scanError = "Aucun produit trouvé pour ce code-barres"
                                else openProduct(product)
                            }
                        },
                        onClose = { showScanner = false }
                    )
                } else {
                    BackHandler { exitSession() }
                    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                        InventoryScanStep(
                            numero            = activeSession?.let { inventoryNumero(it.id) } ?: "",
                            sessionItemsCount = counts.total_products,
                            ecartsCount       = counts.total_ecarts,
                            totalValueEcarts  = counts.total_value_ecarts,
                            scanError         = scanError,
                            canFinish         = counts.total_products > 0,
                            isSaving          = false,
                            userName          = userName,
                            onUserNameChange  = viewModel::setUserName,
                            onBack            = { exitSession() },
                            onScan            = { showScanner = true },
                            onSearch          = { showSearchDialog = true },
                            onReview          = { navController.navigate(Screen.InventaireSessionReview.route) },
                            onFinish          = { navController.navigate(Screen.InventaireSessionReadyToFinish.route) }
                        )
                    }
                    if (showSearchDialog) {
                        InventoryProductSearchDialog(
                            products       = viewModel.productList.items.collectAsLazyPagingItems(),
                            search         = viewModel.productSearch,
                            onSearchChange = { viewModel.productSearch = it },
                            onSelect       = { product -> showSearchDialog = false; scanScope.launch { openProduct(product) } },
                            onDismiss = { showSearchDialog = false }
                        )
                    }
                }
            }

            composable(
                route     = Screen.InventaireSessionQuantity.route,
                arguments = listOf(navArgument("productId") { type = NavType.IntType })
            ) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
                val viewModel: InventoryViewModel = hiltViewModel(parentEntry)
                val productId = entry.arguments!!.getInt("productId")
                val lookup by remember(productId) { viewModel.observeProduct(productId) }
                    .collectAsState(initial = ProductLookup.Loading)
                val product = (lookup as? ProductLookup.Found)?.product
                val userName by viewModel.userName.collectAsState()

                var qtePhysiqueText by remember { mutableStateOf("") }
                var saveError       by remember { mutableStateOf("") }
                var isSaving        by remember { mutableStateOf(false) }

                if (product != null) {
                    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                        InventoryQuantityStep(
                            product          = product,
                            qtePhysiqueText  = qtePhysiqueText,
                            onQuantityChange = { raw ->
                                val filtered = raw.filter { c -> c.isDigit() || c == '.' }
                                qtePhysiqueText = if (filtered.count { it == '.' } > 1) qtePhysiqueText else filtered
                            },
                            saveError = saveError,
                            isSaving  = isSaving,
                            onCancel  = { navController.popBackStack() },
                            onSave    = {
                                // A second tap before the button greys out would save the line twice;
                                // the database would refuse it, but the worker would see an error.
                                if (isSaving) return@InventoryQuantityStep
                                val qte = qtePhysiqueText.toDoubleOrNull()
                                if (qte == null || qte < 0) { saveError = "Quantité invalide"; return@InventoryQuantityStep }
                                isSaving = true
                                viewModel.recordScan(
                                    productId = product.id, qtePhysique = qte,
                                    userName  = userName.trim().ifEmpty { null },
                                    onSuccess = { _, _, _ ->
                                        isSaving = false; saveError = ""
                                        navController.navigate(Screen.InventaireSessionConfirmed.route) {
                                            popUpTo(Screen.InventaireSessionQuantity.route) { inclusive = true }
                                        }
                                    },
                                    onError = { msg -> isSaving = false; saveError = msg }
                                )
                            }
                        )
                    }
                } else if (lookup == ProductLookup.Gone) {
                    LeaveWhenGone(navController, entry)
                }
            }

            composable(Screen.InventaireSessionConfirmed.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
                val viewModel: InventoryViewModel = hiltViewModel(parentEntry)
                val lastResult by viewModel.lastScanResult.collectAsState()
                val currentResult = lastResult
                val lookup by remember(currentResult?.productId) {
                    currentResult?.let { viewModel.observeProduct(it.productId) } ?: flowOf(ProductLookup.Gone)
                }.collectAsState(initial = ProductLookup.Loading)
                val product = (lookup as? ProductLookup.Found)?.product

                if (currentResult != null && product != null) {
                    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                        InventoryConfirmedStep(
                            product     = product,
                            qteSysteme  = currentResult.qteSysteme,
                            qtePhysique = currentResult.qtePhysique,
                            ecart       = currentResult.ecart,
                            onScanNext  = {
                                navController.popBackStack(Screen.InventaireSessionScan.route, inclusive = false)
                            }
                        )
                    }
                } else if (currentResult == null || lookup == ProductLookup.Gone) {
                    LeaveWhenGone(navController, entry)
                }
            }

            composable(Screen.InventaireSessionReview.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
                val viewModel: InventoryViewModel = hiltViewModel(parentEntry)
                // Paged and live while listed: collected here only, so the scans themselves never reload it.
                val items = remember { viewModel.sessionItemPages() }.collectAsLazyPagingItems()
                val counts by viewModel.counts.collectAsState()
                val userName by viewModel.userName.collectAsState()
                var scanError by remember { mutableStateOf("") }

                Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                    InventoryReviewStep(
                        items    = items,
                        count    = counts.total_products,
                        isSaving = false,
                        onBack   = { navController.popBackStack() },
                        onEdit   = { item, newQte ->
                            viewModel.updateScan(item.id, newQte, userName = userName.trim().ifEmpty { null }, onSuccess = {}, onError = { scanError = it })
                        },
                        onDelete = { item -> viewModel.deleteScan(item.id, onSuccess = {}, onError = { scanError = it }) },
                        onFinish = { navController.navigate(Screen.InventaireSessionReadyToFinish.route) }
                    )
                }
            }

            composable(Screen.InventaireSessionReadyToFinish.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
                val viewModel: InventoryViewModel = hiltViewModel(parentEntry)
                val counts by viewModel.counts.collectAsState()

                Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                    InventoryReadyToFinishStep(
                        itemsCount    = counts.total_products,
                        onBack        = { navController.popBackStack() },
                        onShowSummary = { navController.navigate(Screen.InventaireSessionSummary.route) }
                    )
                }
            }

            composable(Screen.InventaireSessionSummary.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
                val viewModel: InventoryViewModel = hiltViewModel(parentEntry)
                val summaryPreview by viewModel.counts.collectAsState()

                var isConfirmed      by remember { mutableStateOf(false) }
                var isConfirming     by remember { mutableStateOf(false) }
                var confirmError     by remember { mutableStateOf("") }
                var showDetailDialog by remember { mutableStateOf(false) }


                fun exitToHistory() {
                    viewModel.loadHistory()
                    navController.popBackStack(Screen.InventaireSessionGraph.route, inclusive = true)
                }

                BackHandler(enabled = isConfirmed) { exitToHistory() }

                Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                    InventorySummaryStep(
                        summary         = summaryPreview,
                        isConfirmed     = isConfirmed,
                        isConfirming    = isConfirming,
                        confirmError    = confirmError,
                        onBack          = { navController.popBackStack() },
                        onConfirm       = {
                            isConfirming = true
                            viewModel.finishSession(
                                onSuccess = { isConfirming = false; isConfirmed = true; confirmError = "" },
                                onError   = { msg -> isConfirming = false; confirmError = msg }
                            )
                        },
                        onViewDetail    = { showDetailDialog = true },
                        onReturnHistory = { exitToHistory() }
                    )
                }
                if (showDetailDialog) {
                    InventoryDetailDialog(
                        items     = remember { viewModel.sessionItemPages() }.collectAsLazyPagingItems(),
                        onDismiss = { showDetailDialog = false }
                    )
                }
            }
        }
    }
}