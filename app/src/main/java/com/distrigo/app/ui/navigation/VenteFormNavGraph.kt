package com.distrigo.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.products.ProductViewModel
import com.distrigo.app.ui.scanner.BarcodeScannerScreen
import com.distrigo.app.ui.tournees.TourneeVenteCartItem
import com.distrigo.app.ui.tournees.TourneeVenteCartRow
import com.distrigo.app.ui.ventes.*

// `viewModel`/`productViewModel`/`clientViewModel` are `@Composable` providers rather than
// pre-resolved instances: NavHost's `builder: NavGraphBuilder.() -> Unit` runs inside a plain
// `remember { navController.createGraph(...) }` call, NOT a @Composable context, so `hiltViewModel()`
// cannot be invoked directly as an argument at the call site (in the raw NavHost{} block). Each
// provider is instead invoked lazily inside this graph's own `composable(...) { }` content
// lambdas, which ARE @Composable — letting the caller decide the scoping (e.g. parent-graph vs.
// per-destination) while keeping this file compilable.
// `routePrefix` namespaces this graph instance's child routes so the SAME function can be
// registered twice per NavHost under two different graph routes (see Screen.VenteFormGraph /
// Screen.VenteFormGraphDirect) without route collisions:
//  - skipClientStep = false: client/vente unknown at entry → starts at the client-picker step.
//  - skipClientStep = true : client or vente already known at entry → starts directly at Products.
//    The client step composable is never registered in this mode, so it can never be navigated
//    to, composed, or animated — not even for a single frame.
fun NavGraphBuilder.venteFormGraph(
    navController    : NavHostController,
    graphRoute       : String,
    routePrefix      : String,
    skipClientStep   : Boolean = false,
    viewModel        : @Composable () -> VenteViewModel,
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
            navArgument("venteId")  { type = NavType.IntType; defaultValue = -1 },
            navArgument("clientId") { type = NavType.IntType; defaultValue = -1 }
        )
    ) {
        if (!skipClientStep) {
        composable(clientRoute) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val clientViewModel = clientViewModel()
            val venteId = parentEntry.arguments?.getInt("venteId")?.takeIf { it != -1 }
            val clientIdArg = parentEntry.arguments?.getInt("clientId")?.takeIf { it != -1 }
            val isEdit = venteId != null
            val ventes by viewModel.ventes.collectAsState()
            val editingVente = venteId?.let { id -> ventes.find { it.id == id } }
            val clients by clientViewModel.clients.collectAsState()
            val formClient by viewModel.formClient.collectAsState()

            var initialized by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!initialized) {
                    viewModel.resetVenteForm()
                    if (venteId != null) viewModel.loadVenteDetail(venteId)
                    initialized = true
                }
            }
            LaunchedEffect(clients, editingVente) {
                if (isEdit && formClient == null && editingVente != null && clients.isNotEmpty()) {
                    viewModel.setFormClient(clients.find { it.id == editingVente.client_id })
                }
            }
            LaunchedEffect(clientIdArg, clients) {
                if (clientIdArg != null && formClient == null && !isEdit) {
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
            var clientSearch by remember { mutableStateOf("") }
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

            Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
                    }
                    Spacer(Modifier.width(DsSpacing.xs))
                    Text("Choisir un client", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                }

                OutlinedTextField(
                    value         = clientSearch,
                    onValueChange = { clientSearch = it },
                    placeholder   = { Text("Rechercher par nom ou téléphone…", fontSize = DsTextSize.body) },
                    leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
                    shape         = DsShapes.large,
                    singleLine    = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
                )

                Spacer(Modifier.height(DsSpacing.sm))

                val filteredClients = clients.filter { client ->
                    val tokens = clientSearch.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                    tokens.isEmpty() || tokens.all { token ->
                        client.name.contains(token, ignoreCase = true) ||
                                (client.phone?.contains(token, ignoreCase = true) == true)
                    }
                }

                LazyColumn(
                    modifier            = Modifier.weight(1f),
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(filteredClients, key = { it.id }) { client ->
                        val typeColors = when (client.customer_type) {
                            "wholesale" -> DsColors.TagWholesale
                            "business"  -> DsColors.TagBusiness
                            else        -> DsColors.TagRetail
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.large)
                                .background(DsColors.Surface)
                                .border(1.dp, if (selectedClient?.id == client.id) DsColors.Primary else DsColors.Border, DsShapes.large)
                                .clickable {
                                    viewModel.setFormClient(client)
                                    navController.popBackStack()
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val initials = client.name.split(" ").take(2)
                                .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                            Box(
                                modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(typeColors.second),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(initials, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = typeColors.first)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(client.name, fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.bodyLarge, color = DsColors.TextPrimary, maxLines = 1)
                                Text(client.phone ?: "—", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                if (client.balance > 0) {
                                    Text("${"%.2f".format(client.balance)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.Danger)
                                } else {
                                    Text("✓ Soldé", fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Success)
                                }
                            }
                            if (selectedClient?.id == client.id) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Button(
                    onClick  = { showAddClientScreen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                        .height(52.dp),
                    shape  = DsShapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text("Nouveau client", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        }

        composable(productsRoute) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val clientViewModel = clientViewModel()
            val productViewModel = productViewModel()
            val venteId = parentEntry.arguments?.getInt("venteId")?.takeIf { it != -1 }
            val clientIdArg = parentEntry.arguments?.getInt("clientId")?.takeIf { it != -1 }
            val isEdit = venteId != null
            val ventes by viewModel.ventes.collectAsState()
            val editingVente = venteId?.let { id -> ventes.find { it.id == id } }
            val products by productViewModel.products.collectAsState()
            val formClient by viewModel.formClient.collectAsState()
            val cartItems by viewModel.formCartItems.collectAsState()
            var search by remember { mutableStateOf("") }
            var showScanner by remember { mutableStateOf(false) }

            if (skipClientStep) {
                // This step is the graph's entry point in this mode (client/vente already known
                // at navigation time) — run the same one-time init + client-resolution that the
                // client step normally does, since that step is never entered here.
                val clients by clientViewModel.clients.collectAsState()

                var initialized by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (!initialized) {
                        viewModel.resetVenteForm()
                        if (venteId != null) viewModel.loadVenteDetail(venteId)
                        initialized = true
                    }
                }
                LaunchedEffect(clients, editingVente) {
                    if (isEdit && formClient == null && editingVente != null && clients.isNotEmpty()) {
                        viewModel.setFormClient(clients.find { it.id == editingVente.client_id })
                    }
                }
                LaunchedEffect(clientIdArg, clients) {
                    if (clientIdArg != null && formClient == null && !isEdit) {
                        viewModel.setFormClient(clients.find { it.id == clientIdArg })
                    }
                }
            }

            LaunchedEffect(products, editingVente) {
                if (isEdit && cartItems.isEmpty() && editingVente?.items != null && products.isNotEmpty()) {
                    val newCartItems = editingVente.items!!.map { item ->
                        val liveProduct = products.find { it.id == item.product_id }
                            ?: com.distrigo.app.data.model.Product(
                                id             = item.product_id,
                                name           = item.product_name,
                                barcode        = null,
                                selling_price  = item.unit_price,
                                purchase_price = 0.0,
                                stock          = 0.0,
                                min_stock      = 0,
                                unit_type      = item.unit_type,
                                packages       = 0,
                                pack_size      = 0,
                                has_expiry     = 0,
                                expiry_date    = null,
                                image_uri      = null,
                                category_name  = null,
                                category_id    = null,
                                supplier_name  = null,
                                supplier_id    = null,
                                camion_stock   = 0.0
                            )
                        // `liveProduct.stock`/`camion_stock` already reflect this sale's own deduction
                        // (applyStockDelta was applied at save time), so the raw live snapshot understates
                        // what was actually available when this sale was made — restore this item's own
                        // reservation onto the snapshot before it's used for display, mirroring
                        // ProductRepository.updateVente's own reversal math, but only in local UI state —
                        // never persisted.
                        val editBaselineProduct = if (editingVente.source == "camion") {
                            liveProduct.copy(
                                stock        = liveProduct.stock + item.quantity,
                                camion_stock = liveProduct.camion_stock + item.quantity
                            )
                        } else {
                            liveProduct.copy(stock = liveProduct.stock + item.quantity)
                        }
                        VenteCartItem(
                            product             = editBaselineProduct,
                            quantity            = item.quantity,
                            unitPrice           = item.unit_price,
                            originalReservedQty = item.quantity
                        )
                    }
                    viewModel.setFormCartItems(newCartItems)
                }
            }

            // Keep each cart line's product snapshot synced with the live products flow. For
            // edit-mode lines, re-apply the original saved reservation on every sync — see
            // VenteCartItem.originalReservedQty.
            LaunchedEffect(products) {
                if (products.isEmpty()) return@LaunchedEffect
                var changed = false
                val resynced = cartItems.map { ci ->
                    val fresh = products.find { it.id == ci.product.id } ?: return@map ci
                    val adjusted = ci.originalReservedQty?.let { reserved ->
                        if (editingVente?.source == "camion")
                            fresh.copy(stock = fresh.stock + reserved, camion_stock = fresh.camion_stock + reserved)
                        else
                            fresh.copy(stock = fresh.stock + reserved)
                    } ?: fresh
                    if (adjusted.stock == ci.product.stock && adjusted.camion_stock == ci.product.camion_stock) {
                        ci
                    } else {
                        changed = true
                        ci.copy(product = adjusted)
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
                tokens.isEmpty() || tokens.all { token ->
                    product.name.contains(token, ignoreCase = true) ||
                            (product.barcode?.contains(token, ignoreCase = true) == true)
                }
            }
            val total = cartItems.sumOf { it.quantity * it.unitPrice }

            Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (skipClientStep) onBack() else navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (isEdit) "Modifier la vente #$venteId" else "Vente dépôt", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                        Text(
                            formClient?.name ?: "Choisir un client",
                            fontSize = DsTextSize.caption,
                            color    = if (formClient != null) DsColors.Primary else DsColors.TextSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(DsShapes.pill)
                            .background(DsColors.PrimaryLight)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "Produits · 2/3",
                            fontSize   = DsTextSize.caption,
                            fontWeight = FontWeight.Bold,
                            color      = DsColors.Primary
                        )
                    }
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
                                colors = OutlinedTextFieldDefaults.colors(
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
                                            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                                                Text("Dépôt: ${formatQty(product.stock - product.camion_stock)}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                                Text("Camion: ${formatQty(product.camion_stock)}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                            }
                                        }

                                        Spacer(Modifier.width(DsSpacing.sm))

                                        if (!isInCart) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.setFormCartItems(
                                                        cartItems + VenteCartItem(
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
            val venteId = parentEntry.arguments?.getInt("venteId")?.takeIf { it != -1 }
            val ventes by viewModel.ventes.collectAsState()
            val editingVente = venteId?.let { id -> ventes.find { it.id == id } }
            val products by productViewModel.products.collectAsState()
            val cartItems by viewModel.formCartItems.collectAsState()
            val note by viewModel.formNote.collectAsState()
            var expandedCartItemId by remember { mutableStateOf<Int?>(null) }

            LaunchedEffect(products) {
                if (products.isEmpty()) return@LaunchedEffect
                var changed = false
                val resynced = cartItems.map { ci ->
                    val fresh = products.find { it.id == ci.product.id } ?: return@map ci
                    val adjusted = ci.originalReservedQty?.let { reserved ->
                        if (editingVente?.source == "camion")
                            fresh.copy(stock = fresh.stock + reserved, camion_stock = fresh.camion_stock + reserved)
                        else
                            fresh.copy(stock = fresh.stock + reserved)
                    } ?: fresh
                    if (adjusted.stock == ci.product.stock && adjusted.camion_stock == ci.product.camion_stock) {
                        ci
                    } else {
                        changed = true
                        ci.copy(product = adjusted)
                    }
                }
                if (changed) viewModel.setFormCartItems(resynced)
            }

            val total = cartItems.sumOf { it.quantity * it.unitPrice }

            BackHandler { navController.popBackStack() }

            Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
                        }
                        Spacer(Modifier.width(DsSpacing.xs))
                        Column {
                            Text("Ma sélection", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                            Text("${cartItems.size} article(s)", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                        }
                    }
                    if (cartItems.isNotEmpty()) {
                        TextButton(onClick = { viewModel.setFormCartItems(emptyList()) }) {
                            Text("Vider", color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                        }
                    }
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
                            val isRowExpanded = expandedCartItemId == item.product.id
                            val toggleExpand: () -> Unit = {
                                expandedCartItemId = if (isRowExpanded) null else item.product.id
                            }
                            val changeQuantity: (Double) -> Unit = { newQty ->
                                viewModel.setFormCartItems(cartItems.map {
                                    if (it.product.id == item.product.id) it.copy(quantity = maxOf(1.0, newQty)) else it
                                })
                            }
                            val changePrice: (Double) -> Unit = { newPrice ->
                                viewModel.setFormCartItems(cartItems.map {
                                    if (it.product.id == item.product.id) it.copy(unitPrice = newPrice) else it
                                })
                            }
                            val removeItem: () -> Unit = {
                                viewModel.setFormCartItems(cartItems.filter { it.product.id != item.product.id })
                            }

                            if (editingVente?.source == "camion") {
                                TourneeVenteCartRow(
                                    item = TourneeVenteCartItem(
                                        product   = item.product,
                                        quantity  = item.quantity,
                                        unitPrice = item.unitPrice
                                    ),
                                    isExpanded       = isRowExpanded,
                                    onToggleExpand   = toggleExpand,
                                    onQuantityChange = changeQuantity,
                                    onPriceChange    = changePrice,
                                    onRemove         = removeItem
                                )
                            } else {
                                VenteCartRow(
                                    item             = item,
                                    isExpanded       = isRowExpanded,
                                    onToggleExpand   = toggleExpand,
                                    onQuantityChange = changeQuantity,
                                    onPriceChange    = changePrice,
                                    onRemove         = removeItem
                                )
                            }
                        }

                        item {
                            OutlinedTextField(
                                value         = note,
                                onValueChange = { viewModel.setFormNote(it) },
                                placeholder   = { Text("Note (optionnel)", fontSize = DsTextSize.body) },
                                modifier      = Modifier.fillMaxWidth(),
                                shape         = DsShapes.medium,
                                minLines      = 2,
                                colors = OutlinedTextFieldDefaults.colors(
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
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
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
            val clientViewModel = clientViewModel()
            val venteId = parentEntry.arguments?.getInt("venteId")?.takeIf { it != -1 }
            val isEdit = venteId != null
            val formClient by viewModel.formClient.collectAsState()
            val cartItems by viewModel.formCartItems.collectAsState()
            val note by viewModel.formNote.collectAsState()
            val userName by viewModel.formUserName.collectAsState()
            val montantPaye by viewModel.formMontantPaye.collectAsState()
            var isSaving by remember { mutableStateOf(false) }
            var saveError by remember { mutableStateOf("") }
            val total = cartItems.sumOf { it.quantity * it.unitPrice }

            fun doSave() {
                if (formClient == null) return
                isSaving = true; saveError = ""
                val items = cartItems.map { ci ->
                    mapOf("product_id" to ci.product.id, "quantity" to ci.quantity, "unit_price" to ci.unitPrice)
                }
                if (isEdit) {
                    viewModel.updateVente(
                        id = venteId!!, clientId = formClient!!.id, items = items,
                        note = note.trim().ifEmpty { null }, montantPaye = montantPaye.toDoubleOrNull() ?: 0.0,
                        userName = userName.trim().ifEmpty { null },
                        onSuccess = { clientViewModel.loadTransactions(formClient!!.id); onSaved() },
                        onError = { error -> isSaving = false; saveError = error }
                    )
                } else {
                    viewModel.createVente(
                        clientId = formClient!!.id, tourneeId = null, source = "depot", items = items,
                        note = note.trim().ifEmpty { null }, montantPaye = montantPaye.toDoubleOrNull() ?: 0.0,
                        userName = userName.trim().ifEmpty { null },
                        onSuccess = { clientViewModel.loadTransactions(formClient!!.id); onSaved() },
                        onError = { error -> isSaving = false; saveError = error }
                    )
                }
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
                userName            = userName,
                onUserNameChange    = { viewModel.setFormUserName(it) },
                isSaving            = isSaving,
                saveError           = saveError,
                onBack              = { navController.popBackStack() },
                onConfirm           = { doSave() }
            )
        }
    }
}
