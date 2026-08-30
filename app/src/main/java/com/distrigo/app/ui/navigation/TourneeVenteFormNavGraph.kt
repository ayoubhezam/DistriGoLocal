package com.distrigo.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.distrigo.app.ui.clients.ClientViewModel
import com.distrigo.app.ui.designsystem.DsStepBadge
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.products.ProductViewModel
import com.distrigo.app.ui.scanner.BarcodeScannerScreen
import com.distrigo.app.ui.tournees.Step1Client
import com.distrigo.app.ui.tournees.Step3Validation
import com.distrigo.app.ui.tournees.TourneeVenteCartItem
import com.distrigo.app.ui.tournees.TourneeVenteCartRow
import com.distrigo.app.ui.tournees.TourneeViewModel
import com.distrigo.app.ui.tournees.formatQty
import com.distrigo.app.ui.ventes.VenteViewModel

// Mirrors venteFormGraph (VenteFormNavGraph.kt): form state (client/cart/note/montantPaye) lives
// on the outer TourneesGraph-scoped TourneeViewModel — shared with TourneesHome/TourneesDetail —
// not on a ViewModel scoped to this nested graph, so it's the SAME instance across all 5 steps.
// See TourneeViewModel.formClient/formCartItems/formNote/formMontantPaye.
// `routePrefix` namespaces this graph instance's child routes so the SAME function can be
// registered twice per NavHost under two different graph routes (see
// Screen.TourneeVenteFormGraph / Screen.TourneeVenteFormGraphDirect) without route collisions:
//  - skipClientStep = false: client unknown at entry → starts at the client-picker step.
//  - skipClientStep = true : client already known at entry → starts directly at Products. The
//    client step composable is never registered in this mode, so it can never be navigated to,
//    composed, or animated — not even for a single frame.
fun NavGraphBuilder.tourneeVenteFormGraph(
    navController    : NavHostController,
    graphRoute       : String,
    routePrefix      : String,
    skipClientStep   : Boolean = false,
    viewModel        : @Composable () -> TourneeViewModel,
    venteViewModel   : @Composable () -> VenteViewModel,
    productViewModel : @Composable () -> ProductViewModel,
    clientViewModel  : @Composable () -> ClientViewModel,
    onBack  : () -> Unit,
    onSaved : () -> Unit
) {
    val clientRoute       = "${routePrefix}_client"
    val clientPickerRoute = "${routePrefix}_client_picker"
    val productsRoute     = "${routePrefix}_products"
    val cartRoute         = "${routePrefix}_cart"
    val validationRoute   = "${routePrefix}_validation"

    navigation(
        startDestination = if (skipClientStep) productsRoute else clientRoute,
        route = graphRoute,
        arguments = listOf(
            navArgument("tourneeId") { type = NavType.IntType },
            navArgument("clientId")  { type = NavType.IntType; defaultValue = -1 }
        )
    ) {
        if (!skipClientStep) {
        composable(clientRoute) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val clientViewModel = clientViewModel()
            val clientIdArg = parentEntry.arguments?.getInt("clientId")?.takeIf { it != -1 }
            val clients by clientViewModel.clients.collectAsState()
            val formClient by viewModel.formClient.collectAsState()

            var initialized by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!initialized) {
                    viewModel.resetTourneeVenteForm()
                    initialized = true
                }
            }
            LaunchedEffect(clientIdArg, clients) {
                if (clientIdArg != null && formClient == null) {
                    viewModel.setFormClient(clients.find { it.id == clientIdArg })
                }
            }

            BackHandler { onBack() }

            Step1Client(
                selectedClient = formClient,
                onChooseClient = { navController.navigate(clientPickerRoute) },
                onNext         = { navController.navigate(productsRoute) }
            )
        }

        composable(clientPickerRoute) { entry ->
            remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val clientViewModel = clientViewModel()
            val clients by clientViewModel.clients.collectAsState()
            var showAddClientScreen by remember { mutableStateOf(false) }

            if (showAddClientScreen) {
                BackHandler { showAddClientScreen = false }
                com.distrigo.app.ui.clients.ClientFormScreen(
                    onBack  = { showAddClientScreen = false },
                    onSaved = { newClientId ->
                        showAddClientScreen = false
                        clientViewModel.loadClientsAndUpdate(newClientId) { newClient ->
                            if (newClient != null) {
                                viewModel.setFormClient(newClient)
                                navController.popBackStack()
                            }
                        }
                    }
                )
                return@composable
            }

            BackHandler { navController.popBackStack() }

            val selectedClient = viewModel.formClient.collectAsState().value

            com.distrigo.app.ui.common.ClientSearchPicker(
                clients          = clients,
                selectedClientId = selectedClient?.id,
                onClientSelected = { client ->
                    viewModel.setFormClient(client)
                    navController.popBackStack()
                },
                onBack         = { navController.popBackStack() },
                onAddNewClient = { showAddClientScreen = true }
            )
        }
        }

        composable(productsRoute) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val clientViewModel = clientViewModel()
            val productViewModel = productViewModel()
            val tourneeId = parentEntry.arguments!!.getInt("tourneeId")
            val clientIdArg = parentEntry.arguments?.getInt("clientId")?.takeIf { it != -1 }
            val products by productViewModel.products.collectAsState()
            val formClient by viewModel.formClient.collectAsState()
            val cartItems by viewModel.formCartItems.collectAsState()
            var search by remember { mutableStateOf("") }
            var showScanner by remember { mutableStateOf(false) }

            if (skipClientStep) {
                // This step is the graph's entry point in this mode (client already known at
                // navigation time) — run the same one-time init + client-resolution that the
                // client step normally does, since that step is never entered here.
                val clients by clientViewModel.clients.collectAsState()

                var initialized by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (!initialized) {
                        viewModel.resetTourneeVenteForm()
                        initialized = true
                    }
                }
                LaunchedEffect(clientIdArg, clients) {
                    if (clientIdArg != null && formClient == null) {
                        viewModel.setFormClient(clients.find { it.id == clientIdArg })
                    }
                }
            }

            // Because this graph's ViewModel can stay alive across multiple wizard visits
            // (graph-scoped to TourneesGraph, not to this nested graph), a product's camion_stock
            // can change elsewhere (Chargement, Perte) while a cart is being built. Re-sync each
            // cart item's product snapshot against the live list so the stepper's ceiling /
            // "Disponible" line stay honest. UX refresh only — ProductRepository.createVente
            // re-checks camion_stock against the database at save time regardless.
            LaunchedEffect(products) {
                if (products.isEmpty()) return@LaunchedEffect
                var changed = false
                val resynced = cartItems.map { ci ->
                    val fresh = products.find { it.id == ci.product.id } ?: return@map ci
                    if (fresh.camion_stock == ci.product.camion_stock) {
                        ci
                    } else {
                        changed = true
                        ci.copy(product = fresh)
                    }
                }
                if (changed) viewModel.setFormCartItems(resynced)
            }

            if (showScanner) {
                BackHandler { showScanner = false }
                BarcodeScannerScreen(
                    onBarcodeScanned = { code -> search = code; showScanner = false },
                    onClose = { showScanner = false }
                )
                return@composable
            }

            BackHandler {
                if (skipClientStep) onBack() else navController.popBackStack()
            }

            val filteredProducts = products.filter { product ->
                val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                val matchesSearch = tokens.isEmpty() || tokens.all { token ->
                    product.name.contains(token, ignoreCase = true) ||
                            (product.barcode?.contains(token, ignoreCase = true) == true)
                }
                product.camion_stock > 0 && matchesSearch
            }
            val total = cartItems.sumOf { it.quantity * it.unitPrice }

            Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
                DsTopAppBar(
                    title         = "Vente — Tournée #$tourneeId",
                    subtitle      = formClient?.name ?: "Choisir un client",
                    // Blue once a client is chosen, grey while the step is still open.
                    subtitleColor = if (formClient != null) DsColors.Primary else DsColors.TextSecondary,
                    leading       = DsTopBarLeading.Back({ if (skipClientStep) onBack() else navController.popBackStack() })
                ) {
                    DsStepBadge("Produits", 2, 3)
                    Spacer(Modifier.width(DsSpacing.xs))
                }
                HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            OutlinedTextField(
                                value         = search,
                                onValueChange = { search = it },
                                placeholder   = { Text("Rechercher par nom ou code-barres…", fontSize = DsTextSize.body) },
                                leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon  = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (search.isNotEmpty()) {
                                            IconButton(onClick = { search = "" }) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Effacer",
                                                    tint = DsColors.TextSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        IconButton(onClick = { showScanner = true }) {
                                            Icon(
                                                Icons.Default.QrCodeScanner,
                                                contentDescription = "Scanner un code-barres",
                                                tint = DsColors.Primary
                                            )
                                        }
                                    }
                                },
                                modifier      = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = DsSpacing.lg)
                                    .padding(top = DsSpacing.md),
                                shape         = DsShapes.large,
                                singleLine    = true,
                                colors = dsTextFieldColors(
                                    unfocusedBorderColor = DsColors.Border,
                                    focusedBorderColor   = DsColors.Primary
                                )
                            )

                            Spacer(Modifier.height(DsSpacing.sm))

                            Text(
                                "${filteredProducts.size} produit(s)",
                                fontSize = DsTextSize.caption,
                                color    = DsColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = DsSpacing.lg)
                            )

                            Spacer(Modifier.height(DsSpacing.sm))

                            LazyColumn(
                                contentPadding      = PaddingValues(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs, bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                            ) {
                                items(filteredProducts, key = { it.id }) { product ->
                                    val isInCart = cartItems.any { it.product.id == product.id }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(DsShapes.large)
                                            .background(DsColors.Surface)
                                            .border(1.dp, if (isInCart) DsColors.Primary else DsColors.Border, DsShapes.large)
                                            .padding(DsSpacing.md),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(DsShapes.medium)
                                                .background(if (isInCart) DsColors.PrimaryLight else DsColors.SurfaceMuted),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val bitmap = remember(product.image_uri) {
                                                product.image_uri?.let { uri ->
                                                    val imageBytes = android.util.Base64.decode(uri.substringAfter("base64,"), android.util.Base64.NO_WRAP)
                                                    android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                                }
                                            }
                                            if (bitmap != null) {
                                                androidx.compose.foundation.Image(
                                                    bitmap = bitmap.asImageBitmap(), contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    if (isInCart) Icons.Default.Check else Icons.Default.ShoppingCart,
                                                    contentDescription = null,
                                                    tint     = if (isInCart) DsColors.Primary else DsColors.TextSecondary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(DsSpacing.sm))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                product.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize   = DsTextSize.body,
                                                color      = DsColors.TextPrimary,
                                                maxLines   = 1
                                            )
                                            Text(
                                                "${product.category_name ?: "—"} · ${"%.2f".format(product.selling_price)} DA",
                                                fontSize = DsTextSize.caption,
                                                color    = DsColors.TextSecondary
                                            )
                                            Text(
                                                "Camion: ${formatQty(product.camion_stock)}",
                                                fontSize = DsTextSize.caption,
                                                color    = DsColors.TextSecondary
                                            )
                                        }

                                        Spacer(Modifier.width(DsSpacing.sm))

                                        if (!isInCart) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.setFormCartItems(
                                                        cartItems + TourneeVenteCartItem(
                                                            product   = product,
                                                            quantity  = 1.0,
                                                            unitPrice = product.selling_price
                                                        )
                                                    )
                                                },
                                                modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight)
                                            ) {
                                                Icon(Icons.Default.AddShoppingCart, contentDescription = "Ajouter au panier", tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                                            }
                                        } else {
                                            IconButton(
                                                onClick = { viewModel.setFormCartItems(cartItems.filter { it.product.id != product.id }) },
                                                modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.SuccessLight)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = "Ajouté", tint = DsColors.Success, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Cart summary + Suivant ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DsSpacing.xl, vertical = DsSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.large)
                                .background(if (cartItems.isNotEmpty()) DsColors.PrimaryLight else DsColors.SurfaceSunken)
                                .clickable(enabled = cartItems.isNotEmpty()) { navController.navigate(cartRoute) }
                                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                        ) {
                            Box(
                                modifier = Modifier.size(20.dp).clip(DsShapes.pill)
                                    .background(if (cartItems.isNotEmpty()) DsColors.Primary else DsColors.TextTertiary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${cartItems.size}", color = Color.White, fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "Ma sélection", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold,
                                color = if (cartItems.isNotEmpty()) DsColors.Primary else DsColors.TextTertiary
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${"%.2f".format(total)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold,
                                color = if (cartItems.isNotEmpty()) DsColors.Primary else DsColors.TextTertiary
                            )
                        }
                    }
                }
            }
        }

        composable(cartRoute) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val productViewModel = productViewModel()
            val products by productViewModel.products.collectAsState()
            val cartItems by viewModel.formCartItems.collectAsState()
            val note by viewModel.formNote.collectAsState()
            var expandedCartItemId by remember { mutableStateOf<Int?>(null) }

            LaunchedEffect(products) {
                if (products.isEmpty()) return@LaunchedEffect
                var changed = false
                val resynced = cartItems.map { ci ->
                    val fresh = products.find { it.id == ci.product.id } ?: return@map ci
                    if (fresh.camion_stock == ci.product.camion_stock) {
                        ci
                    } else {
                        changed = true
                        ci.copy(product = fresh)
                    }
                }
                if (changed) viewModel.setFormCartItems(resynced)
            }

            val total = cartItems.sumOf { it.quantity * it.unitPrice }

            BackHandler { navController.popBackStack() }

            Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
                DsTopAppBar(
                    title    = "Ma sélection",
                    subtitle = "${cartItems.size} article(s)",
                    leading  = DsTopBarLeading.Back({ navController.popBackStack() })
                ) {
                    if (cartItems.isNotEmpty()) {
                        TextButton(onClick = { viewModel.setFormCartItems(emptyList()) }) {
                            Text("Vider", color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                        }
                    }
                    // TextButton brings its own inset; this makes up the standard end margin.
                    Spacer(Modifier.width(DsSpacing.xs))
                }

                Spacer(Modifier.height(DsSpacing.sm))

                if (cartItems.isEmpty()) {
                    Box(
                        modifier         = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint     = DsColors.TextTertiary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(DsSpacing.md))
                            Text("Sélection vide", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(DsSpacing.sm))
                            Button(
                                onClick = { navController.popBackStack() },
                                shape   = DsShapes.medium,
                                colors  = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                            ) { Text("Parcourir les produits") }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
                        modifier            = Modifier.weight(1f)
                    ) {
                        items(cartItems, key = { "cart_${it.product.id}" }) { item ->
                            TourneeVenteCartRow(
                                item             = item,
                                isExpanded       = expandedCartItemId == item.product.id,
                                onToggleExpand   = {
                                    expandedCartItemId = if (expandedCartItemId == item.product.id) null else item.product.id
                                },
                                onQuantityChange = { newQty ->
                                    viewModel.setFormCartItems(cartItems.map {
                                        if (it.product.id == item.product.id) it.copy(quantity = maxOf(1.0, newQty)) else it
                                    })
                                },
                                onPriceChange = { newPrice ->
                                    viewModel.setFormCartItems(cartItems.map {
                                        if (it.product.id == item.product.id) it.copy(unitPrice = newPrice) else it
                                    })
                                },
                                onRemove = {
                                    viewModel.setFormCartItems(cartItems.filter { it.product.id != item.product.id })
                                }
                            )
                        }

                        item {
                            OutlinedTextField(
                                value         = note,
                                onValueChange = { viewModel.setFormNote(it) },
                                placeholder   = { Text("Note (optionnel)", fontSize = DsTextSize.body) },
                                modifier      = Modifier.fillMaxWidth(),
                                shape         = DsShapes.medium,
                                minLines      = 2,
                                colors = dsTextFieldColors(
                                    unfocusedBorderColor = DsColors.Border,
                                    focusedBorderColor   = DsColors.Primary
                                )
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(DsShapes.medium)
                                    .background(DsColors.PrimaryLight)
                                    .padding(DsSpacing.lg),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text("Total", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.Primary)
                                Text("${"%.2f".format(total)} DA", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.Primary)
                            }
                        }
                    }

                    // ── Bottom action bar: Retour + Suivant ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                    ) {
                        OutlinedButton(
                            onClick  = { navController.popBackStack() },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape    = DsShapes.medium
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(DsSpacing.sm))
                            Text("Retour", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick  = { navController.navigate(validationRoute) },
                            enabled  = cartItems.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape    = DsShapes.medium,
                            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                        ) {
                            Text("Suivant →", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        composable(validationRoute) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val venteViewModel = venteViewModel()
            val tourneeId = parentEntry.arguments!!.getInt("tourneeId")
            val clientIdArg = parentEntry.arguments?.getInt("clientId")?.takeIf { it != -1 }
            val formClient by viewModel.formClient.collectAsState()
            val cartItems by viewModel.formCartItems.collectAsState()
            val note by viewModel.formNote.collectAsState()
            val montantPaye by viewModel.formMontantPaye.collectAsState()
            var isSaving by remember { mutableStateOf(false) }
            val total = cartItems.sumOf { it.quantity * it.unitPrice }

            fun doSave() {
                if (formClient == null) return
                isSaving = true
                val items = cartItems.map { ci ->
                    mapOf(
                        "product_id" to ci.product.id,
                        "quantity"   to ci.quantity,
                        "unit_price" to ci.unitPrice
                    )
                }
                venteViewModel.createVente(
                    clientId    = formClient!!.id,
                    tourneeId   = tourneeId,
                    source      = "camion",
                    items       = items,
                    note        = note.trim().ifEmpty { null },
                    montantPaye = montantPaye.toDoubleOrNull() ?: 0.0,
                    onSuccess   = {
                        viewModel.refreshAfterVenteChange(tourneeId)
                        if (clientIdArg != null) {
                            viewModel.markTourneeClientVisited(
                                tourneeId, clientIdArg,
                                onSuccess = { viewModel.refreshAfterVenteChange(tourneeId) },
                                onError   = {}
                            )
                        } else {
                            viewModel.loadTourneeClients(tourneeId)
                        }
                        onSaved()
                    },
                    onError = { isSaving = false }
                )
            }

            BackHandler { navController.popBackStack() }

            Step3Validation(
                selectedClient      = formClient,
                cartItems           = cartItems,
                total               = total,
                montantPaye         = montantPaye,
                onMontantPayeChange = { viewModel.setFormMontantPaye(it) },
                note                = note,
                onNoteChange        = { viewModel.setFormNote(it) },
                isSaving            = isSaving,
                onBack              = { navController.popBackStack() },
                onConfirm           = { doSave() }
            )
        }
    }
}
