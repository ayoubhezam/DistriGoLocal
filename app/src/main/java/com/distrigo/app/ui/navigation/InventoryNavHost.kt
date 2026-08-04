package com.distrigo.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.distrigo.app.data.model.InventorySessionSummary
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.inventory.*
import com.distrigo.app.ui.scanner.BarcodeScannerScreen

@Composable
fun InventoryNavHost(onBack: () -> Unit, onFullScreenChange: (Boolean) -> Unit = {}) {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = Screen.InventaireHome.route,
        route            = Screen.InventaireGraph.route
    ) {
        composable(Screen.InventaireHome.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
            val viewModel: InventoryViewModel = viewModel(parentEntry)
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
            val viewModel: InventoryViewModel = viewModel(parentEntry)
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
                val viewModel: InventoryViewModel = viewModel(parentEntry)

                LaunchedEffect(Unit) { viewModel.startOrResumeSession() }

                val activeSession by viewModel.activeSession.collectAsState()
                val sessionItems   by viewModel.sessionItems.collectAsState()
                val products        by viewModel.products.collectAsState()
                val userName by viewModel.userName.collectAsState()

                var showScanner       by remember { mutableStateOf(false) }
                var showSearchDialog  by remember { mutableStateOf(false) }
                var scanError         by remember { mutableStateOf("") }

                fun exitSession() {
                    viewModel.loadHistory()
                    navController.popBackStack(Screen.InventaireSessionGraph.route, inclusive = true)
                }

                fun openProduct(product: com.distrigo.app.data.model.Product) {
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
                            val product = products.find { it.barcode?.equals(code, ignoreCase = true) == true }
                            if (product == null) scanError = "Aucun produit trouvé pour ce code-barres"
                            else openProduct(product)
                        },
                        onClose = { showScanner = false }
                    )
                } else {
                    BackHandler { exitSession() }
                    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                        InventoryScanStep(
                            numero            = activeSession?.let { inventoryNumero(it.id) } ?: "",
                            sessionItemsCount = sessionItems.size,
                            ecartsCount       = sessionItems.count { it.ecart != 0.0 },
                            totalValueEcarts  = sessionItems.sumOf { kotlin.math.abs(it.valeur_ecart) },
                            scanError         = scanError,
                            canFinish         = sessionItems.isNotEmpty(),
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
                            products  = products,
                            onSelect  = { product -> showSearchDialog = false; openProduct(product) },
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
                val viewModel: InventoryViewModel = viewModel(parentEntry)
                val productId = entry.arguments!!.getInt("productId")
                val products by viewModel.products.collectAsState()
                val product = products.find { it.id == productId }
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
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }

            composable(Screen.InventaireSessionConfirmed.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
                val viewModel: InventoryViewModel = viewModel(parentEntry)
                val lastResult by viewModel.lastScanResult.collectAsState()
                val products by viewModel.products.collectAsState()
                val currentResult = lastResult
                val product = currentResult?.let { r -> products.find { it.id == r.productId } }

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
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }

            composable(Screen.InventaireSessionReview.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
                val viewModel: InventoryViewModel = viewModel(parentEntry)
                val sessionItems by viewModel.sessionItems.collectAsState()
                val userName by viewModel.userName.collectAsState()
                var scanError by remember { mutableStateOf("") }

                Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                    InventoryReviewStep(
                        items    = sessionItems,
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
                val viewModel: InventoryViewModel = viewModel(parentEntry)
                val sessionItems by viewModel.sessionItems.collectAsState()

                Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                    InventoryReadyToFinishStep(
                        itemsCount    = sessionItems.size,
                        onBack        = { navController.popBackStack() },
                        onShowSummary = { navController.navigate(Screen.InventaireSessionSummary.route) }
                    )
                }
            }

            composable(Screen.InventaireSessionSummary.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.InventaireGraph.route) }
                val viewModel: InventoryViewModel = viewModel(parentEntry)
                val sessionItems by viewModel.sessionItems.collectAsState()

                var isConfirmed      by remember { mutableStateOf(false) }
                var isConfirming     by remember { mutableStateOf(false) }
                var confirmError     by remember { mutableStateOf("") }
                var showDetailDialog by remember { mutableStateOf(false) }

                val summaryPreview = remember(sessionItems) {
                    InventorySessionSummary(
                        total_products     = sessionItems.size,
                        total_ecarts       = sessionItems.count { it.ecart != 0.0 },
                        total_value_ecarts = sessionItems.sumOf { kotlin.math.abs(it.valeur_ecart) }
                    )
                }

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
                    InventoryDetailDialog(items = sessionItems, onDismiss = { showDetailDialog = false })
                }
            }
        }
    }
}