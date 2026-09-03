package com.distrigo.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.distrigo.app.ui.tournees.TourneeVenteCartItem
import com.distrigo.app.ui.tournees.TourneeVenteCartRow
import com.distrigo.app.ui.ventes.*
import com.distrigo.app.ui.ventes.VenteFormSessionViewModel

/**
 * Resolves the session that owns this pass through the form, and enters it.
 *
 * Scoped to the graph's back stack entry, so it lives exactly as long as one visit to the form:
 * restored with the entry after process death, destroyed with it when the user leaves. A restored
 * session means "carry on silently"; a fresh entry means the caller was free to ask
 * "Reprendre ou recommencer ?".
 */
@Composable
private fun venteFormSession(
    navController: NavHostController,
    graphRoute   : String,
    route        : String
): VenteFormSessionViewModel {
    val graphEntry = remember(navController, graphRoute) { navController.getBackStackEntry(graphRoute) }
    val session: VenteFormSessionViewModel = hiltViewModel(graphEntry)

    // Entering the session happens here, on every destination, rather than in the first one only.
    // Process death restores the back stack to whichever step the user was on, so the first
    // destination may never compose — a session entry that lived there alone would leave a restored
    // Cart or Validation step staring at an empty form. The call is idempotent.
    val venteId  = graphEntry.arguments?.getInt("venteId")?.takeIf { it != -1 }
    val clientId = graphEntry.arguments?.getInt("clientId")?.takeIf { it != -1 }
    val draftId  = graphEntry.arguments?.getInt("draftId")?.takeIf { it != -1 }
    LaunchedEffect(session) { session.beginOrResumeSession(venteId, clientId, draftId) }

    // Record where the user is, so a resume can land on the step they left, and keep the ON_STOP
    // flush attached to whichever step is actually on screen.
    LaunchedEffect(route) { session.setLastStep(route) }
    FlushDraftOnStop(session)

    return session
}

/**
 * Writes the draft on ON_STOP rather than waiting out the autosave debounce, which would otherwise
 * lose its tail if the process died inside the window.
 */
@Composable
private fun FlushDraftOnStop(session: VenteFormSessionViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) session.flushDraft()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

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
//
// The form's own state (client / cart / note / userName / montantPaye) is NOT on the
// `viewModel` provider any more: it lives on VenteFormSessionViewModel, scoped to this graph's
// back stack entry, which is also what enters the session and keeps its Brouillon. The provider
// still supplies the ventes list and the create/update commands. See venteFormSession above.
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
            navArgument("clientId") { type = NavType.IntType; defaultValue = -1 },
            navArgument("draftId")  { type = NavType.IntType; defaultValue = -1 }
        )
    ) {
        if (!skipClientStep) {
        composable(clientRoute) { entry ->
            remember(entry) { navController.getBackStackEntry(graphRoute) }
            val session = venteFormSession(navController, graphRoute, clientRoute)
            val formClient by session.formClient.collectAsState()

            // The one-time init that used to live here -- resetVenteForm() behind a
            // rememberSaveable("initialized") flag, plus two effects resolving the client from the
            // vente or from the clientId argument -- is now the session's beginOrResumeSession.
            // It had to be duplicated on the products step for the skipClientStep entry, and the
            // flag outlived the ViewModel it described across process death. The session owns both
            // halves and cannot get out of step with itself.

            BackHandler { onBack() }

            Step1Client(
                selectedClient = formClient,
                onChooseClient = { navController.navigate(clientPickerRoute) },
                onNext         = { navController.navigate(productsRoute) }
            )
        }

        composable(clientPickerRoute) { entry ->
            remember(entry) { navController.getBackStackEntry(graphRoute) }
            val session = venteFormSession(navController, graphRoute, clientPickerRoute)
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
                                session.setFormClient(newClient)
                                navController.popBackStack()
                            }
                        }
                    }
                )
                return@composable
            }

            BackHandler { navController.popBackStack() }

            val selectedClient = session.formClient.collectAsState().value

            com.distrigo.app.ui.common.ClientSearchPicker(
                clients          = clients,
                selectedClientId = selectedClient?.id,
                onClientSelected = { client ->
                    session.setFormClient(client)
                    navController.popBackStack()
                },
                onBack         = { navController.popBackStack() },
                onAddNewClient = { showAddClientScreen = true }
            )
        }
        }

        composable(productsRoute) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val session = venteFormSession(navController, graphRoute, productsRoute)
            val productViewModel = productViewModel()
            val venteId = parentEntry.arguments?.getInt("venteId")?.takeIf { it != -1 }
            val editSource by session.editSource.collectAsState()
            val products by productViewModel.products.collectAsState()
            val formClient by session.formClient.collectAsState()
            val cartItems by session.formCartItems.collectAsState()
            var search by remember { mutableStateOf("") }
            var showScanner by remember { mutableStateOf(false) }

            // Two blocks used to live here and are now the session's:
            //  - a copy of the client step's one-time init, needed because in skipClientStep mode
            //    that step is never entered. The session enters on every destination, so there is
            //    nothing left to duplicate.
            //  - the edit prefill that rebuilt the cart from the vente. It ran off whichever
            //    destination happened to compose first, which a process-death restore does not
            //    guarantee; prefillEditFromVente runs on every path into the graph instead, and
            //    it is also the half that has to stay in step with the fingerprint's mirror of it.

            // Keep each cart line's product snapshot synced with the live products flow. For
            // edit-mode lines, re-apply the original saved reservation on every sync — see
            // VenteCartItem.originalReservedQty.
            LaunchedEffect(products) {
                if (products.isEmpty()) return@LaunchedEffect
                var changed = false
                val resynced = cartItems.map { ci ->
                    val fresh = products.find { it.id == ci.product.id } ?: return@map ci
                    val adjusted = ci.originalReservedQty?.let { reserved ->
                        if (editSource == "camion")
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
                if (changed) session.setFormCartItems(resynced)
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
                DsTopAppBar(
                    title         = if (venteId != null) "Modifier la vente #$venteId" else "Vente dépôt",
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
                                            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                                                Text("Dépôt: ${formatQty(product.stock - product.camion_stock)}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                                Text("Camion: ${formatQty(product.camion_stock)}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                            }
                                        }

                                        Spacer(Modifier.width(DsSpacing.sm))

                                        if (!isInCart) {
                                            IconButton(
                                                onClick = {
                                                    session.setFormCartItems(
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
                                                onClick = { session.setFormCartItems(cartItems.filter { it.product.id != product.id }) },
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
            val session = venteFormSession(navController, graphRoute, cartRoute)
            val productViewModel = productViewModel()
            val venteId = parentEntry.arguments?.getInt("venteId")?.takeIf { it != -1 }
            val editSource by session.editSource.collectAsState()
            val products by productViewModel.products.collectAsState()
            val cartItems by session.formCartItems.collectAsState()
            val note by session.formNote.collectAsState()
            var expandedCartItemId by remember { mutableStateOf<Int?>(null) }

            LaunchedEffect(products) {
                if (products.isEmpty()) return@LaunchedEffect
                var changed = false
                val resynced = cartItems.map { ci ->
                    val fresh = products.find { it.id == ci.product.id } ?: return@map ci
                    val adjusted = ci.originalReservedQty?.let { reserved ->
                        if (editSource == "camion")
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
                if (changed) session.setFormCartItems(resynced)
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
                        TextButton(onClick = { session.setFormCartItems(emptyList()) }) {
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
                            val isRowExpanded = expandedCartItemId == item.product.id
                            val toggleExpand: () -> Unit = {
                                expandedCartItemId = if (isRowExpanded) null else item.product.id
                            }
                            val changeQuantity: (Double) -> Unit = { newQty ->
                                session.setFormCartItems(cartItems.map {
                                    if (it.product.id == item.product.id) it.copy(quantity = maxOf(1.0, newQty)) else it
                                })
                            }
                            val changePrice: (Double) -> Unit = { newPrice ->
                                session.setFormCartItems(cartItems.map {
                                    if (it.product.id == item.product.id) it.copy(unitPrice = newPrice) else it
                                })
                            }
                            val removeItem: () -> Unit = {
                                session.setFormCartItems(cartItems.filter { it.product.id != item.product.id })
                            }

                            if (editSource == "camion") {
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
                                onValueChange = { session.setFormNote(it) },
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
            val session = venteFormSession(navController, graphRoute, validationRoute)
            val clientViewModel = clientViewModel()
            val venteId = parentEntry.arguments?.getInt("venteId")?.takeIf { it != -1 }
            val isEdit = venteId != null
            val formClient by session.formClient.collectAsState()
            val cartItems by session.formCartItems.collectAsState()
            val note by session.formNote.collectAsState()
            val userName by session.formUserName.collectAsState()
            val montantPaye by session.formMontantPaye.collectAsState()
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
                        draftId = session.draftId,
                        onSuccess = {
                            session.onCommitted()
                            clientViewModel.loadTransactions(formClient!!.id)
                            onSaved()
                        },
                        onError = { error -> isSaving = false; saveError = error }
                    )
                } else {
                    viewModel.createVente(
                        clientId = formClient!!.id, tourneeId = null, source = "depot", items = items,
                        note = note.trim().ifEmpty { null }, montantPaye = montantPaye.toDoubleOrNull() ?: 0.0,
                        userName = userName.trim().ifEmpty { null },
                        draftId = session.draftId,
                        onSuccess = {
                            session.onCommitted()
                            clientViewModel.loadTransactions(formClient!!.id)
                            onSaved()
                        },
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
                onMontantPayeChange = { session.setFormMontantPaye(it) },
                note                = note,
                onNoteChange        = { session.setFormNote(it) },
                userName            = userName,
                onUserNameChange    = { session.setFormUserName(it) },
                isSaving            = isSaving,
                saveError           = saveError,
                onBack              = { navController.popBackStack() },
                onConfirm           = { doSave() }
            )
        }
    }
}
