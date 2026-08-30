package com.distrigo.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.paging.compose.collectAsLazyPagingItems
import com.distrigo.app.data.model.ClientTransaction
import com.distrigo.app.data.model.FactureFilter
import com.distrigo.app.ui.clients.*
import com.distrigo.app.ui.components.paging.PagedHistoryScreen
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.retours.RetourClientListScreen
import com.distrigo.app.ui.retours.RetourClientViewModel

@Composable
fun ClientsNavHost(
    preSelectedClientId : Int? = null,
    onFullScreenChange  : (Boolean) -> Unit = {},
    onBack              : () -> Unit = {}
) {
    val navController = rememberNavController()

    LaunchedEffect(preSelectedClientId) {
        if (preSelectedClientId != null) {
            navController.navigate(Screen.ClientsDetail.createRoute(preSelectedClientId))
        }
    }

    NavHost(
        navController      = navController,
        startDestination   = Screen.ClientsHome.route,
        route              = Screen.ClientsGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.ClientsHome.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ClientsGraph.route) }
            val viewModel: ClientViewModel = hiltViewModel(parentEntry)
            ClientsScreen(
                viewModel     = viewModel,
                onBack        = onBack,
                onAddClient   = { navController.navigate(Screen.ClientsForm.createRoute()) },
                onEditClient  = { clientId -> navController.navigate(Screen.ClientsForm.createRoute(clientId)) },
                onClientClick = { clientId -> navController.navigate(Screen.ClientsDetail.createRoute(clientId)) }
            )
        }

        composable(
            route     = Screen.ClientsForm.route,
            arguments = listOf(navArgument("clientId") { type = NavType.IntType; defaultValue = -1 })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ClientsGraph.route) }
            val viewModel: ClientViewModel = hiltViewModel(parentEntry)
            val clientId = entry.arguments!!.getInt("clientId").takeIf { it != -1 }
            val clients by viewModel.clients.collectAsState()
            val client = clientId?.let { id -> clients.find { it.id == id } }

            ClientFormScreen(
                client    = client,
                viewModel = viewModel,
                onBack    = { navController.popBackStack() },
                onSaved   = { navController.popBackStack() }
            )
        }

        composable(
            route     = Screen.ClientsDetail.route,
            arguments = listOf(navArgument("clientId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ClientsGraph.route) }
            val viewModel: ClientViewModel = hiltViewModel(parentEntry)
            val retourParentEntry = remember(entry) { navController.getBackStackEntry(Screen.ClientsGraph.route) }
            val retourViewModel: RetourClientViewModel = hiltViewModel(retourParentEntry)
            val clientId = entry.arguments!!.getInt("clientId")
            val clients by viewModel.clients.collectAsState()
            val isLoading by viewModel.isLoading.collectAsState()
            val client = clients.find { it.id == clientId }
            var showDeleteConfirm by remember { mutableStateOf(false) }

            when {
                client != null -> {
                    ClientDetailScreen(
                        client           = client,
                        viewModel        = viewModel,
                        retourViewModel  = retourViewModel,
                        onBack           = { navController.popBackStack() },
                        onEdit           = { navController.navigate(Screen.ClientsForm.createRoute(clientId)) },
                        onDelete         = { showDeleteConfirm = true },
                        onNewVente       = { navController.navigate(Screen.VenteFormGraphDirect.createRoute(clientId = clientId)) },
                        onRetourForm     = { navController.navigate(Screen.ClientsRetourFormGraph.createRoute(clientId)) },
                        onRetourHistory  = { navController.navigate(Screen.ClientsRetourHistory.createRoute(clientId)) },
                        onFactureHistory = { navController.navigate(Screen.ClientsFactureHistory.createRoute(clientId)) }
                    )
                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("Supprimer le client") },
                            text  = { Text("Voulez-vous supprimer \"${client.name}\" ?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.deleteClient(
                                        id        = clientId,
                                        onSuccess = { showDeleteConfirm = false; navController.popBackStack() },
                                        onError   = { showDeleteConfirm = false }
                                    )
                                }) { Text("Supprimer", color = DsColors.Danger) }
                            },
                            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") } },
                            containerColor    = DsColors.Surface,
                            titleContentColor = DsColors.TextPrimary,
                            textContentColor  = DsColors.TextSecondary
                        )
                    }
                }
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        CircularProgressIndicator(color = DsColors.Primary)
                    }
                }
                else -> {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
        }

        // "Nouvelle facture" always starts from a specific client (see onNewVente above), so this
        // only needs the direct-entry graph — the client-picker step never applies here.
        venteFormGraph(
            navController    = navController,
            graphRoute        = Screen.VenteFormGraphDirect.route,
            routePrefix       = "clients_vente_form",
            skipClientStep    = true,
            // Scoped to the Clients graph (like clientViewModel below), not a bare hiltViewModel():
            // each of Client/Products/Cart/Validation is a separate NavBackStackEntry with its own
            // ViewModelStore, so an unscoped hiltViewModel() would hand each step a fresh VenteViewModel
            // and silently drop the selected client / cart between steps.
            viewModel         = { hiltViewModel(remember(navController) { navController.getBackStackEntry(Screen.ClientsGraph.route) }) },
            productViewModel  = { hiltViewModel() },
            clientViewModel   = { hiltViewModel(remember(navController) { navController.getBackStackEntry(Screen.ClientsGraph.route) }) },
            onBack  = { navController.popBackStack(Screen.VenteFormGraphDirect.route, inclusive = true) },
            onSaved = { navController.popBackStack(Screen.VenteFormGraphDirect.route, inclusive = true) }
        )

        retourClientFormGraph(
            navController = navController,
            graphRoute    = Screen.ClientsRetourFormGraph.route,
            viewModel     = { hiltViewModel(remember(navController) { navController.getBackStackEntry(Screen.ClientsGraph.route) }) },
            onBack  = { navController.popBackStack(Screen.ClientsRetourFormGraph.route, inclusive = true) },
            onSaved = { navController.popBackStack(Screen.ClientsRetourFormGraph.route, inclusive = true) }
        )

        composable(
            route     = Screen.ClientsRetourHistory.route,
            arguments = listOf(navArgument("clientId") { type = NavType.IntType })
        ) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ClientsGraph.route) }
            val viewModel: ClientViewModel = hiltViewModel(parentEntry)
            val retourParentEntry = remember(entry) { navController.getBackStackEntry(Screen.ClientsGraph.route) }
            val retourViewModel: RetourClientViewModel = hiltViewModel(retourParentEntry)
            val clientId = entry.arguments!!.getInt("clientId")
            val clients by viewModel.clients.collectAsState()
            val client = clients.find { it.id == clientId }

            if (client != null) {
                RetourClientListScreen(
                    client      = client,
                    viewModel   = retourViewModel,
                    onBack      = { navController.popBackStack() },
                    onAddRetour = { navController.navigate(Screen.ClientsRetourFormGraph.createRoute(clientId)) }
                )
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }

        composable(
            route     = Screen.ClientsFactureHistory.route,
            arguments = listOf(navArgument("clientId") { type = NavType.IntType })
        ) { entry ->
            // ── FactureHistoryViewModel: محصورة بهذه الوجهة فقط، غير مشتركة مع الرسم البياني ──
            val historyViewModel: FactureHistoryViewModel = hiltViewModel()
            val clientId = entry.arguments!!.getInt("clientId")
            LaunchedEffect(Unit) { historyViewModel.bind(clientId) }
            val controller = historyViewModel.bind(clientId)
            val historyQuery by controller.query.collectAsState()
            val historyFilter by controller.filter.collectAsState()
            val historyTotalCount by historyViewModel.totalCount.collectAsState()
            val pagingItems = controller.items.collectAsLazyPagingItems()

            var longPressPayment by remember { mutableStateOf<ClientTransaction?>(null) }
            var showDeletePayment by remember { mutableStateOf(false) }
            var showEditPayment by remember { mutableStateOf(false) }
            var editPaymentAmount by remember { mutableStateOf("") }
            var editError by remember { mutableStateOf("") }

            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ClientsGraph.route) }
            val clientViewModel: ClientViewModel = hiltViewModel(parentEntry)

            PagedHistoryScreen(
                title             = "Factures & Paiements",
                countLabel        = if (historyTotalCount > 1) "$historyTotalCount résultats" else "$historyTotalCount résultat",
                query             = historyQuery,
                onQueryChange     = controller::onQueryChange,
                searchPlaceholder = "Rechercher une facture ou un paiement",
                filters           = FactureFilter.entries,
                selectedFilter    = historyFilter,
                onFilterSelected  = controller::onFilterChange,
                filterLabel       = { it.label },
                pagingItems       = pagingItems,
                itemKey           = { "${it.type}_${it.id}" },
                onBack            = { navController.popBackStack() }
            ) { transaction ->
                com.distrigo.app.ui.clients.FactureRow(transaction, onLongPressPaiement = { longPressPayment = it })
            }

            // ── حوارات إدارة الدفعة، منقولة حرفياً من ClientDetailScreen — نفس النص، نفس السلوك ──
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
                    },
                    containerColor    = DsColors.Surface,
                    titleContentColor = DsColors.TextPrimary,
                    textContentColor  = DsColors.TextSecondary
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
                                clientViewModel.deletePayment(
                                    clientId  = clientId,
                                    paymentId = payment.id,
                                    onSuccess = { showDeletePayment = false; longPressPayment = null },
                                    onError   = {}
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DsColors.Danger)
                        ) { Text("Supprimer", color = androidx.compose.ui.graphics.Color.White) }
                    },
                    dismissButton = { TextButton(onClick = { showDeletePayment = false; longPressPayment = null }) { Text("Annuler") } },
                    containerColor    = DsColors.Surface,
                    titleContentColor = DsColors.TextPrimary,
                    textContentColor  = DsColors.TextSecondary
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
                                colors = dsTextFieldColors(
                                    unfocusedBorderColor = DsColors.Border,
                                    focusedBorderColor = DsColors.Primary
                                )
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
                                clientViewModel.updatePayment(
                                    clientId  = clientId,
                                    paymentId = payment.id,
                                    amount    = amount,
                                    onSuccess = { showEditPayment = false; longPressPayment = null; editError = "" },
                                    onError   = { editError = it }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                        ) { Text("Confirmer", color = androidx.compose.ui.graphics.Color.White) }
                    },
                    dismissButton = { TextButton(onClick = { showEditPayment = false; longPressPayment = null; editError = "" }) { Text("Annuler") } },
                    containerColor    = DsColors.Surface,
                    titleContentColor = DsColors.TextPrimary,
                    textContentColor  = DsColors.TextSecondary
                )
            }
        }
    }
}