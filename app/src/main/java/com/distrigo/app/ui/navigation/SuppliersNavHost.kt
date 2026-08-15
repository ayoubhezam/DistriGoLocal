package com.distrigo.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.paging.compose.collectAsLazyPagingItems
import com.distrigo.app.data.model.AchatFilter
import com.distrigo.app.data.model.SupplierTransaction
import com.distrigo.app.ui.components.paging.PagedHistoryScreen
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.retours.RetourFournisseurListScreen
import com.distrigo.app.ui.retours.RetourFournisseurViewModel
import com.distrigo.app.ui.suppliers.*

@Composable
fun SuppliersNavHost(
    onFullScreenChange : (Boolean) -> Unit = {},
    onNavigateToOrder  : (Int) -> Unit = {}
) {
    val navController = rememberNavController()

    NavHost(
        navController      = navController,
        startDestination   = Screen.SuppliersHome.route,
        route              = Screen.SuppliersGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.SuppliersHome.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.SuppliersGraph.route) }
            val viewModel: SupplierViewModel = viewModel(parentEntry)
            SuppliersScreen(
                viewModel       = viewModel,
                onAddSupplier   = { navController.navigate(Screen.SuppliersForm.createRoute()) },
                onSupplierClick = { supplierId -> navController.navigate(Screen.SuppliersDetail.createRoute(supplierId)) }
            )
        }

        composable(
            route     = Screen.SuppliersForm.route,
            arguments = listOf(navArgument("supplierId") { type = NavType.IntType; defaultValue = -1 })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.SuppliersGraph.route) }
            val viewModel: SupplierViewModel = viewModel(parentEntry)
            val supplierId = entry.arguments!!.getInt("supplierId").takeIf { it != -1 }
            val suppliers by viewModel.suppliers.collectAsState()
            val supplier = supplierId?.let { id -> suppliers.find { it.id == id } }

            SupplierFormScreen(
                supplier  = supplier,
                viewModel = viewModel,
                onBack    = { navController.popBackStack() },
                onSaved   = { navController.popBackStack() }
            )
        }

        composable(
            route     = Screen.SuppliersDetail.route,
            arguments = listOf(navArgument("supplierId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.SuppliersGraph.route) }
            val viewModel: SupplierViewModel = viewModel(parentEntry)
            val retourParentEntry = remember(entry) { navController.getBackStackEntry(Screen.SuppliersGraph.route) }
            val retourViewModel: RetourFournisseurViewModel = viewModel(retourParentEntry)
            val supplierId = entry.arguments!!.getInt("supplierId")
            val suppliers by viewModel.suppliers.collectAsState()
            val isLoading by viewModel.isLoading.collectAsState()
            val supplier = suppliers.find { it.id == supplierId }
            var showDeleteConfirm by remember { mutableStateOf(false) }

            when {
                supplier != null -> {
                    SupplierDetailScreen(
                        supplier          = supplier,
                        viewModel         = viewModel,
                        retourViewModel   = retourViewModel,
                        onBack            = { navController.popBackStack() },
                        onEdit            = { navController.navigate(Screen.SuppliersForm.createRoute(supplierId)) },
                        onDelete          = { showDeleteConfirm = true },
                        onNewAchat        = { navController.navigate(Screen.PurchaseFormGraph.createRoute(supplierId = supplierId)) },
                        onRetourForm      = { navController.navigate(Screen.SuppliersRetourFormGraph.createRoute(supplierId)) },
                        onRetourHistory   = { navController.navigate(Screen.SuppliersRetourHistory.createRoute(supplierId)) },
                        onAchatHistory    = { navController.navigate(Screen.SuppliersAchatHistory.createRoute(supplierId)) },
                        onNavigateToOrder = onNavigateToOrder
                    )
                    if (showDeleteConfirm) {
                        var deleteError by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false; deleteError = "" },
                            title = { Text("Supprimer le fournisseur") },
                            text = {
                                Column {
                                    Text("Voulez-vous supprimer \"${supplier.name}\" ?")
                                    if (deleteError.isNotEmpty()) {
                                        Spacer(Modifier.height(DsSpacing.sm))
                                        Text(deleteError, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.deleteSupplier(
                                        id        = supplierId,
                                        onSuccess = { showDeleteConfirm = false; navController.popBackStack() },
                                        onError   = { deleteError = "Impossible de supprimer ce fournisseur car il est associé à des produits." }
                                    )
                                }) { Text("Supprimer", color = DsColors.Danger) }
                            },
                            dismissButton = { TextButton(onClick = { showDeleteConfirm = false; deleteError = "" }) { Text("Annuler") } }
                        )
                    }
                }
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DsColors.Primary)
                    }
                }
                else -> {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
        }

        purchaseFormGraph(
            navController     = navController,
            graphRoute        = Screen.PurchaseFormGraph.route,
            viewModel         = { viewModel(remember(navController) { navController.getBackStackEntry(Screen.PurchaseFormGraph.route) }) },
            productViewModel  = { viewModel() },
            supplierViewModel = { viewModel() },
            onBack  = { navController.popBackStack(Screen.PurchaseFormGraph.route, inclusive = true) },
            onSaved = {
                val supplierId = navController.getBackStackEntry(Screen.PurchaseFormGraph.route)
                    .arguments?.getInt("supplierId")?.takeIf { it != -1 }
                if (supplierId != null) {
                    ViewModelProvider(navController.getBackStackEntry(Screen.SuppliersGraph.route))[SupplierViewModel::class.java]
                        .loadTransactions(supplierId)
                }
                navController.popBackStack(Screen.PurchaseFormGraph.route, inclusive = true)
            }
        )

        retourFournisseurFormGraph(
            navController     = navController,
            graphRoute        = Screen.SuppliersRetourFormGraph.route,
            viewModel         = { viewModel(remember(navController) { navController.getBackStackEntry(Screen.SuppliersGraph.route) }) },
            supplierViewModel = { viewModel(remember(navController) { navController.getBackStackEntry(Screen.SuppliersGraph.route) }) },
            onBack  = { navController.popBackStack(Screen.SuppliersRetourFormGraph.route, inclusive = true) },
            onSaved = { navController.popBackStack(Screen.SuppliersRetourFormGraph.route, inclusive = true) }
        )

        composable(
            route     = Screen.SuppliersRetourHistory.route,
            arguments = listOf(navArgument("supplierId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.SuppliersGraph.route) }
            val viewModel: SupplierViewModel = viewModel(parentEntry)
            val retourParentEntry = remember(entry) { navController.getBackStackEntry(Screen.SuppliersGraph.route) }
            val retourViewModel: RetourFournisseurViewModel = viewModel(retourParentEntry)
            val supplierId = entry.arguments!!.getInt("supplierId")
            val suppliers by viewModel.suppliers.collectAsState()
            val supplier = suppliers.find { it.id == supplierId }

            if (supplier != null) {
                RetourFournisseurListScreen(
                    supplierId   = supplier.id,
                    supplierName = supplier.name,
                    viewModel    = retourViewModel,
                    onBack       = { navController.popBackStack() },
                    onAddRetour  = { navController.navigate(Screen.SuppliersRetourFormGraph.createRoute(supplierId)) }
                )
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }

        composable(
            route     = Screen.SuppliersAchatHistory.route,
            arguments = listOf(navArgument("supplierId") { type = NavType.IntType })
        ) { entry ->
            val historyViewModel: AchatHistoryViewModel = viewModel()
            val supplierId = entry.arguments!!.getInt("supplierId")
            LaunchedEffect(Unit) { historyViewModel.bind(supplierId) }
            val controller = historyViewModel.bind(supplierId)
            val historyQuery by controller.query.collectAsState()
            val historyFilter by controller.filter.collectAsState()
            val historyTotalCount by historyViewModel.totalCount.collectAsState()
            val pagingItems = controller.items.collectAsLazyPagingItems()

            var longPressPayment by remember { mutableStateOf<SupplierTransaction?>(null) }
            var showDeletePayment by remember { mutableStateOf(false) }
            var showEditPayment by remember { mutableStateOf(false) }
            var editPaymentAmount by remember { mutableStateOf("") }
            var editError by remember { mutableStateOf("") }

            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.SuppliersGraph.route) }
            val supplierViewModel: SupplierViewModel = viewModel(parentEntry)

            PagedHistoryScreen(
                title             = "Achats & Paiements",
                countLabel        = if (historyTotalCount > 1) "$historyTotalCount résultats" else "$historyTotalCount résultat",
                query             = historyQuery,
                onQueryChange     = controller::onQueryChange,
                searchPlaceholder = "Rechercher un achat ou un paiement",
                filters           = AchatFilter.entries,
                selectedFilter    = historyFilter,
                onFilterSelected  = controller::onFilterChange,
                filterLabel       = { it.label },
                pagingItems       = pagingItems,
                itemKey           = { "${it.type}_${it.id}" },
                onBack            = { navController.popBackStack() }
            ) { transaction ->
                AchatRow(
                    transaction,
                    onLongPressPaiement = { longPressPayment = it },
                    onClickFacture = { onNavigateToOrder(it.id) }
                )
            }

            if (longPressPayment != null && !showDeletePayment && !showEditPayment) {
                val payment = longPressPayment!!
                AlertDialog(
                    onDismissRequest = { longPressPayment = null },
                    title = { Text("Options du paiement", fontWeight = FontWeight.Bold) },
                    confirmButton = {
                        TextButton(onClick = {
                            editPaymentAmount = (payment.amount ?: 0.0).toString()
                            showEditPayment = true
                        }) { Text("Modifier le montant", color = DsColors.Primary) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeletePayment = true }) { Text("Supprimer", color = DsColors.Danger) }
                    }
                )
            }
            if (longPressPayment != null && showDeletePayment) {
                val payment = longPressPayment!!
                AlertDialog(
                    onDismissRequest = { showDeletePayment = false; longPressPayment = null },
                    title = { Text("Supprimer le paiement", fontWeight = FontWeight.Bold) },
                    text  = { Text("Êtes-vous sûr de vouloir supprimer ce paiement ?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                supplierViewModel.deletePayment(
                                    supplierId = supplierId,
                                    paymentId  = payment.id,
                                    onSuccess  = { showDeletePayment = false; longPressPayment = null },
                                    onError    = {}
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DsColors.Danger)
                        ) { Text("Supprimer", color = Color.White) }
                    },
                    dismissButton = { TextButton(onClick = { showDeletePayment = false; longPressPayment = null }) { Text("Annuler") } }
                )
            }
            if (longPressPayment != null && showEditPayment) {
                val payment = longPressPayment!!
                AlertDialog(
                    onDismissRequest = { showEditPayment = false; longPressPayment = null; editError = "" },
                    title = { Text("Modifier le paiement", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                            OutlinedTextField(
                                value = editPaymentAmount,
                                onValueChange = { editPaymentAmount = it; editError = "" },
                                label = { Text("Montant (DA)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = editError.isNotEmpty(),
                                shape = DsShapes.medium,
                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary)
                            )
                            if (editError.isNotEmpty()) {
                                Text(editError, color = DsColors.Danger, fontSize = DsTextSize.caption)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val amount = editPaymentAmount.toDoubleOrNull()
                                if (amount == null || amount <= 0) { editError = "Montant invalide"; return@Button }
                                supplierViewModel.updatePayment(
                                    supplierId = supplierId,
                                    paymentId  = payment.id,
                                    amount     = amount,
                                    onSuccess  = { showEditPayment = false; longPressPayment = null; editError = "" },
                                    onError    = { editError = it }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                        ) { Text("Confirmer", color = Color.White) }
                    },
                    dismissButton = { TextButton(onClick = { showEditPayment = false; longPressPayment = null; editError = "" }) { Text("Annuler") } }
                )
            }
        }
    }
}