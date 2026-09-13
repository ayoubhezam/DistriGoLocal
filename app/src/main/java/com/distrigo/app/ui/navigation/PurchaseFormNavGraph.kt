package com.distrigo.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.distrigo.app.ui.common.CartStatusLine
import com.distrigo.app.ui.common.CartStatusTone
import com.distrigo.app.ui.common.ExpiryToggleField
import com.distrigo.app.ui.common.PriceFieldWithHistory
import com.distrigo.app.ui.common.QuantityStepper
import com.distrigo.app.ui.common.SelectionCartCard
import com.distrigo.app.ui.common.DsCompactSearchAction
import com.distrigo.app.ui.common.DsCompactSearchField
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
import com.distrigo.app.ui.suppliers.SupplierViewModel
import com.distrigo.app.ui.common.SessionPhase
import com.distrigo.app.ui.purchases.CartItem
import com.distrigo.app.ui.purchases.PurchaseFormSessionViewModel
import com.distrigo.app.ui.purchases.PurchaseViewModel
import com.distrigo.app.ui.purchases.Step1Fournisseur
import com.distrigo.app.ui.purchases.Step3Validation
import com.distrigo.app.ui.purchases.formatQty
import com.distrigo.app.ui.purchases.newPurchaseCartItem
import com.distrigo.app.ui.purchases.withNbColis
import com.distrigo.app.ui.purchases.withUniteParColis
import com.distrigo.app.ui.purchases.ProductListFilters
import com.distrigo.app.ui.purchases.PurchaseProductFilterSheet
import com.distrigo.app.ui.purchases.matches
import com.distrigo.app.ui.purchases.matchesSearch
import java.time.LocalDate
import com.distrigo.app.ui.common.EntityImage

/** Height shared by the three chips of Step 02's count / Filtres / Nouveau produit row. */
private val Step2ChipHeight = 32.dp

/**
 * One chip of Step 02's count / Filtres / Nouveau produit row.
 *
 * All three are drawn by this one function so they cannot drift apart: one height, one corner, one
 * padding, one icon size, one type size. What tells them apart is colour alone — the count is
 * information, Filtres is a control, Nouveau produit is the action.
 */
@Composable
private fun Step2Chip(
    icon      : ImageVector,
    label     : String,
    container : Color,
    content   : Color,
    modifier  : Modifier = Modifier,
    dot       : Boolean = false,
    onClick   : (() -> Unit)? = null
) {
    Box(modifier = modifier) {
    Row(
        modifier = Modifier
            .height(Step2ChipHeight)
            .clip(DsShapes.medium)
            .background(container)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = DsSpacing.sm),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(
            label,
            fontSize   = DsTextSize.caption,
            fontWeight = FontWeight.SemiBold,
            color      = content,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
    }
    // The same 6dp dot the Produits "Filtres" button shows while a filter is on. Overlaid in the
    // corner rather than placed in the row, so switching it on does not widen the chip.
    if (dot) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 5.dp, end = 5.dp)
                .size(6.dp)
                .clip(DsShapes.pill)
                .background(DsColors.Primary)
        )
    }
    }
}

// The compact header shared by steps 1/2/3 in the original monolithic PurchaseFormScreen
// (back + dynamic title + step badge). Kept identical across the 3 destinations that had it —
// Cart/SupplierPicker already had their own distinct headers and are unaffected.
@Composable
private fun PurchaseFormHeader(
    isEdit       : Boolean,
    orderId      : Int?,
    supplierName : String?,
    stepLabel    : String,
    currentStep  : Int,
    onBackClick  : () -> Unit
) {
    DsTopAppBar(
        title         = if (isEdit) "Modifier le bon #$orderId" else "Nouveau bon",
        subtitle      = supplierName ?: "Choisir un fournisseur",
        // Blue once a supplier is chosen, grey while the step is still open.
        subtitleColor = if (supplierName != null) DsColors.Primary else DsColors.TextSecondary,
        leading       = DsTopBarLeading.Back(onBackClick)
    ) {
        DsStepBadge(stepLabel, currentStep, 3)
        Spacer(Modifier.width(DsSpacing.xs))
    }
    HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
}

/**
 * The session ViewModel for this pass through the form, plus everything that has to happen on
 * every destination.
 *
 * It is scoped to the form graph's own back stack entry — resolved here rather than passed in by
 * the host, so AchatsNavHost and SuppliersNavHost get the identical lifetime no matter how they
 * scope PurchaseViewModel. That lifetime is the design: the entry (and its SavedStateHandle) is
 * saved and restored across process death, and destroyed when the user pops the graph. Restoring
 * means "carry on silently"; a fresh entry means the caller was free to ask
 * "Reprendre ou recommencer ?".
 */
@Composable
private fun purchaseFormSession(
    navController: NavHostController,
    graphRoute   : String,
    route        : String?
): PurchaseFormSessionViewModel {
    val graphEntry = remember(navController, graphRoute) { navController.getBackStackEntry(graphRoute) }
    val session: PurchaseFormSessionViewModel = hiltViewModel(graphEntry)

    // Entering the session happens here, on every destination, rather than in the first one only.
    // Process death restores the back stack to whichever step the user was on, so the first
    // destination may never compose — a session entry that lived there alone would leave a restored
    // Cart or Validation step staring at an empty form. The call is idempotent.
    val orderId    = graphEntry.arguments?.getInt("orderId")?.takeIf { it != -1 }
    val supplierId = graphEntry.arguments?.getInt("supplierId")?.takeIf { it != -1 }
    val draftId    = graphEntry.arguments?.getInt("draftId")?.takeIf { it != -1 }
    LaunchedEffect(session) { session.beginOrResumeSession(orderId, supplierId, draftId) }

    // Record where the user is — for a future resume that lands on the step they left; nothing
    // reads it yet — and keep the ON_STOP flush attached to whichever step is actually on screen.
    LaunchedEffect(route) { route?.let { session.setLastStep(it) } }
    FlushDraftOnStop(session)

    return session
}

/**
 * Writes the draft on ON_STOP rather than waiting out the autosave debounce, which would otherwise
 * lose its tail if the process died inside the window.
 */
@Composable
private fun FlushDraftOnStop(session: PurchaseFormSessionViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) session.flushDraft()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

// The form's own state (supplier/cart/note/montantPaye) lives on PurchaseFormSessionViewModel,
// scoped to this graph's back stack entry — see purchaseFormSession above. It used to live on the
// PurchaseViewModel instance the caller provides, which outlived the form and could not survive
// process death; that provider now supplies only the orders list, selectedOrder and the
// create/update/receive commands. It stays graph-scoped to AchatsGraph when invoked from
// AchatsNavHost (reusing the same instance AchatsHome/AchatsDetail already share, so edit-mode's
// selectedOrder lookup works), or scoped to this graph's own entry when invoked from
// SuppliersNavHost (no outer PurchaseViewModel to reuse there).
fun NavGraphBuilder.purchaseFormGraph(
    navController     : NavHostController,
    graphRoute        : String,
    viewModel         : @Composable () -> PurchaseViewModel,
    productViewModel  : @Composable () -> ProductViewModel,
    supplierViewModel : @Composable () -> SupplierViewModel,
    onBack  : () -> Unit,
    onSaved : () -> Unit
) {
    navigation(
        startDestination = Screen.PurchaseFormSupplier.route,
        route = graphRoute,
        arguments = listOf(
            navArgument("orderId")    { type = NavType.IntType; defaultValue = -1 },
            navArgument("supplierId") { type = NavType.IntType; defaultValue = -1 },
            navArgument("draftId")    { type = NavType.IntType; defaultValue = -1 }
        )
    ) {
        composable(Screen.PurchaseFormSupplier.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val session = purchaseFormSession(navController, graphRoute, entry.destination.route)
            val supplierViewModel = supplierViewModel()
            val orderIdArg = parentEntry.arguments?.getInt("orderId")?.takeIf { it != -1 }
            val isEdit = orderIdArg != null
            val selectedOrder by viewModel.selectedOrder.collectAsState()
            val suppliers by supplierViewModel.suppliers.collectAsState()
            val formSupplier by session.formSupplier.collectAsState()
            val phase by session.phase.collectAsState()

            // `var initialized by rememberSaveable` used to live here. It survived process death
            // while the ViewModel it described did not, so after a kill it reported "already set
            // up" against an empty ViewModel — skipping loadOrderDetail and leaving the edit-mode
            // gate below spinning forever. The session answers that question from a
            // SavedStateHandle with exactly the ViewModel's lifetime, so it cannot disagree with
            // it, and it is entered in purchaseFormSession() rather than in this one destination.

            // Unconditional, and guarded on the data rather than on a boolean: after process death
            // this re-issues the load the old flag suppressed.
            LaunchedEffect(orderIdArg, selectedOrder?.id) {
                if (orderIdArg != null && selectedOrder?.id != orderIdArg) {
                    viewModel.loadOrderDetail(orderIdArg)
                }
            }

            // Preserve the original "wait for order detail before rendering the wizard" gate.
            if (isEdit && selectedOrder?.id != orderIdArg) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
                return@composable
            }
            val editingOrder = if (isEdit) selectedOrder else null

            // Edit prefill (note, supplier, cart) belongs to the session, for the same reason
            // session entry does: it must not depend on which destination happens to be composed.
            // This effect only covers the case where the suppliers list arrives after the session
            // has already prefilled, which would otherwise leave the header without a name.
            LaunchedEffect(suppliers, editingOrder, phase) {
                if (isEdit && formSupplier == null && editingOrder != null &&
                    phase == SessionPhase.READY
                ) {
                    session.setFormSupplier(suppliers.find { it.id == editingOrder.supplier_id })
                }
            }
            // The supplier carried in by "Nouvel achat" from a supplier page is applied by the
            // session, before autosave is armed — arriving from a supplier page is navigation, not
            // data entry, and on its own it must not spawn a Brouillon. It used to be set from here,
            // after arming, which left an empty draft behind on every stray tap.
            // An auto-advance to Step 2 used to live here, firing whenever a supplier was known.
            // "A supplier is known" is a *state* — permanently true in edit mode and after a
            // supplier is picked — not the *event* "the user just chose one", so it re-fired every
            // time Step 1 came back into composition. With `popUpTo(inclusive = false)` keeping
            // Step 1 on the back stack, Back from Step 2 popped to Step 1 and was immediately
            // pushed forward again: Step 1 could be seen but never reached, and a reopened bon
            // could only be left by saving it.
            //
            // Forward navigation is Step1Fournisseur's own "Suivant" button, which is an actual
            // user event. Step 1 is a real destination on every path into the graph.

            BackHandler { onBack() }

            Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
                PurchaseFormHeader(
                    isEdit       = isEdit,
                    orderId      = orderIdArg,
                    supplierName = formSupplier?.name,
                    stepLabel    = "Fournisseur",
                    currentStep  = 1,
                    onBackClick  = onBack
                )
                Step1Fournisseur(
                    selectedSupplier   = formSupplier,
                    canChangeSupplier  = !isEdit,
                    onChooseSupplier   = { navController.navigate(Screen.PurchaseFormSupplierPicker.route) },
                    onNext             = { navController.navigate(Screen.PurchaseFormProducts.route) }
                )
            }
        }

        composable(Screen.PurchaseFormSupplierPicker.route) { entry ->
            remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val session = purchaseFormSession(navController, graphRoute, entry.destination.route)
            val supplierViewModel = supplierViewModel()
            val suppliers by supplierViewModel.suppliers.collectAsState()
            val formSupplier by session.formSupplier.collectAsState()
            var supplierSearch by remember { mutableStateOf("") }
            var showAddSupplierDialog by remember { mutableStateOf(false) }
            var newSupplierName by remember { mutableStateOf("") }
            var newSupplierPhone by remember { mutableStateOf("") }
            var pendingNewSupplierName by remember { mutableStateOf<String?>(null) }

            // Same pattern as elsewhere in the app: select the freshly created supplier once the
            // observed suppliers flow actually contains it, instead of reading the flow
            // synchronously in the add callback (which would race the Room emission).
            LaunchedEffect(suppliers, pendingNewSupplierName) {
                val name = pendingNewSupplierName ?: return@LaunchedEffect
                val newSupplier = suppliers.find { it.name == name } ?: return@LaunchedEffect
                session.setFormSupplier(newSupplier)
                pendingNewSupplierName = null
                navController.popBackStack()
            }

            if (showAddSupplierDialog) {
                AlertDialog(
                    onDismissRequest = { showAddSupplierDialog = false; newSupplierName = ""; newSupplierPhone = "" },
                    title = { Text("Nouveau fournisseur") },
                    text  = {
                        Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                            OutlinedTextField(
                                value         = newSupplierName,
                                onValueChange = { newSupplierName = it },
                                placeholder   = { Text("Nom du fournisseur *") },
                                singleLine    = true,
                                shape         = DsShapes.medium,
                                modifier      = Modifier.fillMaxWidth(),
                                colors        = dsTextFieldColors(
                                    focusedBorderColor   = DsColors.Primary,
                                    unfocusedBorderColor = DsColors.Border
                                )
                            )
                            OutlinedTextField(
                                value         = newSupplierPhone,
                                onValueChange = { newSupplierPhone = it },
                                placeholder   = { Text("Téléphone (optionnel)") },
                                singleLine    = true,
                                shape         = DsShapes.medium,
                                modifier      = Modifier.fillMaxWidth(),
                                colors        = dsTextFieldColors(
                                    focusedBorderColor   = DsColors.Primary,
                                    unfocusedBorderColor = DsColors.Border
                                )
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (newSupplierName.isNotBlank()) {
                                supplierViewModel.addSupplier(
                                    supplier = mapOf(
                                        "name"            to newSupplierName.trim(),
                                        "phone"           to newSupplierPhone.trim().ifEmpty { null },
                                        "balance"         to 0.0,
                                        "initial_balance" to 0.0
                                    ),
                                    onSuccess = {
                                        pendingNewSupplierName = newSupplierName.trim()
                                        showAddSupplierDialog = false
                                        newSupplierName  = ""
                                        newSupplierPhone = ""
                                    },
                                    onError = { showAddSupplierDialog = false }
                                )
                            }
                        }) {
                            Text("Ajouter", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddSupplierDialog = false; newSupplierName = ""; newSupplierPhone = "" }) {
                            Text("Annuler")
                        }
                    },
                    containerColor    = DsColors.Surface,
                    titleContentColor = DsColors.TextPrimary,
                    textContentColor  = DsColors.TextSecondary
                )
            }

            BackHandler { navController.popBackStack() }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DsColors.Surface)
            ) {
                DsTopAppBar(
                    title   = "Choisir un fournisseur",
                    leading = DsTopBarLeading.Back({ navController.popBackStack() })
                ) {
                    OutlinedButton(
                        onClick = { showAddSupplierDialog = true },
                        shape   = DsShapes.pill,
                        border  = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("", fontSize = DsTextSize.bodySmall, color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                    }
                    // OutlinedButton brings its own inset; this makes up the standard end margin.
                    Spacer(Modifier.width(DsSpacing.xs))
                }
                DsCompactSearchField(
                    value         = supplierSearch,
                    onValueChange = { supplierSearch = it },
                    placeholder   = "Rechercher un fournisseur",
                    modifier      = Modifier.padding(horizontal = DsSpacing.lg)
                )
                Spacer(Modifier.height(DsSpacing.sm))
                val filteredSuppliers = suppliers.filter { supplier ->
                    val tokens = supplierSearch.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                    tokens.isEmpty() || tokens.all { token ->
                        supplier.name.contains(token, ignoreCase = true) ||
                                (supplier.phone?.contains(token, ignoreCase = true) == true)
                    }
                }
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(filteredSuppliers) { supplier ->
                        Card(
                            modifier  = Modifier.fillMaxWidth().clickable {
                                session.setFormSupplier(supplier)
                                navController.popBackStack()
                            },
                            shape     = DsShapes.large,
                            colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                            elevation = CardDefaults.cardElevation(1.dp),
                            border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                        ) {
                            Row(
                                modifier          = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val avatarColors = listOf(0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A, 0xFFC62828, 0xFFE65100, 0xFF00695C)
                                val color        = Color(avatarColors[supplier.name[0].code % avatarColors.size])
                                val initials     = supplier.name.split(" ").take(2)
                                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                                Box(
                                    modifier         = Modifier.size(40.dp).clip(DsShapes.pill),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(modifier = Modifier.fillMaxSize().background(color.copy(alpha = 0.13f)))
                                    Text(initials, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = color)
                                }
                                Spacer(Modifier.width(DsSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(supplier.name, fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary)
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Phone, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(11.dp))
                                        Text(supplier.phone ?: "", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                                    }
                                }
                                if (formSupplier?.id == supplier.id) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                                } else {
                                    Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        composable(Screen.PurchaseFormProducts.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val session = purchaseFormSession(navController, graphRoute, entry.destination.route)
            val productViewModel = productViewModel()
            val orderIdArg = parentEntry.arguments?.getInt("orderId")?.takeIf { it != -1 }
            val supplierIdArg = parentEntry.arguments?.getInt("supplierId")?.takeIf { it != -1 }
            val isEdit = orderIdArg != null
            val products by productViewModel.products.collectAsState()
            val formSupplier by session.formSupplier.collectAsState()
            val cartItems by session.formCartItems.collectAsState()
            var showScanner by remember { mutableStateOf(false) }
            var showAddProductScreen by remember { mutableStateOf(false) }
            var pendingNewProductId by remember { mutableStateOf<Int?>(null) }
            var showFilterSheet by remember { mutableStateOf(false) }
            val categories by productViewModel.categories.collectAsState()
            val sousCategories by productViewModel.sousCategories.collectAsState()
            val marques by productViewModel.marques.collectAsState()
            val suppliers by productViewModel.suppliers.collectAsState()

            // Search, filters and scroll position belong to the session, not to this destination —
            // see "Step 02 browsing state" on PurchaseFormSessionViewModel. The list reopens as it
            // was left whether the user went back to the supplier, on to the cart, or to validation.
            val search  = session.productSearch
            val filters = session.productFilters
            val step2ListState = rememberLazyListState(
                initialFirstVisibleItemIndex        = session.productListIndex,
                initialFirstVisibleItemScrollOffset = session.productListOffset
            )
            DisposableEffect(step2ListState) {
                onDispose {
                    session.productListIndex  = step2ListState.firstVisibleItemIndex
                    session.productListOffset = step2ListState.firstVisibleItemScrollOffset
                }
            }

            // Auto-add a freshly created product ("Nouveau produit") to the cart once the
            // observed products flow actually contains it — the flow's emission arrives
            // asynchronously after ProductFormScreen.onSaved fires, so reading `products` in
            // that callback would race.
            LaunchedEffect(products, pendingNewProductId) {
                val id = pendingNewProductId ?: return@LaunchedEffect
                val newProduct = products.find { it.id == id } ?: return@LaunchedEffect
                if (cartItems.none { it.product.id == id }) {
                    // Seeded exactly as a tap on the list is, so a product starts with the same
                    // line however it reached the cart.
                    session.setFormCartItems(cartItems + newPurchaseCartItem(newProduct))
                }
                pendingNewProductId = null
            }

            // Edit prefill lives on PurchaseFormSessionViewModel, which is the only writer of the
            // form state and the only place the draft fingerprint is captured. A second prefill
            // here would re-add the original lines with defaulted colis/expiry values whenever an
            // edit's cart was emptied, silently rewriting the bon on the next save.

            if (showAddProductScreen) {
                BackHandler { showAddProductScreen = false }
                com.distrigo.app.ui.products.ProductFormScreen(
                    onBack  = { showAddProductScreen = false },
                    onSaved = { newProductId ->
                        showAddProductScreen = false
                        pendingNewProductId = newProductId
                    }
                )
                return@composable
            }

            if (showScanner) {
                BackHandler { showScanner = false }
                BarcodeScannerScreen(
                    onBarcodeScanned = { code -> session.productSearch = code; showScanner = false },
                    onClose = { showScanner = false }
                )
                return@composable
            }

            BackHandler {
                if (supplierIdArg != null) onBack() else navController.popBackStack()
            }

            // matchesSearch is the rule this step always used (every word, in the name or barcode);
            // matches adds the filters. Both are unit-tested in PurchaseProductFiltersTest.
            val filteredProducts = remember(products, search, filters) {
                products.filter { it.matchesSearch(search) && it.matches(filters) }
            }

            // One removable chip per active criterion, each carrying the filters without it.
            val activeFilterChips: List<Pair<String, ProductListFilters>> = buildList {
                filters.categoryId?.let { id ->
                    add("Catégorie : ${categories.find { it.id == id }?.name ?: "—"}" to filters.copy(categoryId = null, sousCategorieId = null))
                }
                filters.sousCategorieId?.let { id ->
                    add("Sous-catégorie : ${sousCategories.find { it.id == id }?.name ?: "—"}" to filters.copy(sousCategorieId = null))
                }
                filters.marqueId?.let { id ->
                    add("Marque : ${marques.find { it.id == id }?.name ?: "—"}" to filters.copy(marqueId = null))
                }
                filters.supplierId?.let { id ->
                    add("Fournisseur : ${suppliers.find { it.id == id }?.name ?: "—"}" to filters.copy(supplierId = null))
                }
                filters.unitType?.let { unit ->
                    add((if (unit == "pièce") "Pièce" else "Carton") to filters.copy(unitType = null))
                }
                filters.stockLevel?.let { level ->
                    val label = when (level) {
                        "in_stock"  -> "En stock"
                        "low_stock" -> "Stock faible"
                        else        -> "Rupture de stock"
                    }
                    add(label to filters.copy(stockLevel = null))
                }
                val priceMin = filters.priceMin.toDoubleOrNull()
                val priceMax = filters.priceMax.toDoubleOrNull()
                if (priceMin != null || priceMax != null) {
                    val range = when {
                        priceMin != null && priceMax != null -> "${formatQty(priceMin)}–${formatQty(priceMax)} DA"
                        priceMin != null                     -> "≥ ${formatQty(priceMin)} DA"
                        else                                 -> "≤ ${formatQty(priceMax!!)} DA"
                    }
                    add("Prix d'achat : $range" to filters.copy(priceMin = "", priceMax = ""))
                }
                if (filters.expiringSoon) add("Bientôt périmé" to filters.copy(expiringSoon = false))
            }
            val total = cartItems.sumOf { it.quantity * it.unitCost }

            Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
                PurchaseFormHeader(
                    isEdit       = isEdit,
                    orderId      = orderIdArg,
                    supplierName = formSupplier?.name,
                    stepLabel    = "Produits",
                    currentStep  = 2,
                    onBackClick  = { if (supplierIdArg != null) onBack() else navController.popBackStack() }
                )

                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {

                    // ── Search ── compact and filled, in the proportions of a messaging app's search
                    // bar rather than a form field's. The query rules are unchanged; the placeholder
                    // is shorter because the scanner beside it already says "or scan a barcode".
                    DsCompactSearchField(
                        value         = search,
                        onValueChange = { session.productSearch = it },
                        placeholder   = "Rechercher un produit",
                        modifier      = Modifier
                            .padding(horizontal = DsSpacing.lg)
                            .padding(top = DsSpacing.md)
                    ) {
                        DsCompactSearchAction(
                            icon               = Icons.Default.QrCodeScanner,
                            contentDescription = "Scanner un code-barres",
                            tint               = DsColors.Primary,
                            onClick            = { showScanner = true }
                        )
                    }

                    // ── Count · Filtres · Nouveau produit ── one fixed row; it no longer collapses
                    // on scroll. The count takes whatever width the two actions leave and is the one
                    // that ellipsizes on a narrow screen, so neither action is ever clipped.
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            Step2Chip(
                                icon      = Icons.Default.Inventory2,
                                label     = "${filteredProducts.size} produit(s)",
                                container = DsColors.SurfaceSunken,
                                content   = DsColors.TextSecondary
                            )
                        }
                        Spacer(Modifier.width(DsSpacing.sm))
                        Step2Chip(
                            icon      = Icons.Default.FilterList,
                            label     = "Filtres",
                            // Blue with a dot once it narrows anything, as the Produits button is. Not
                            // "Filtres · 2": a count in the label widens the chip the moment a filter
                            // is set and squeezes the product count beside it into an ellipsis — the
                            // one figure a user filters in order to read. The chips below already say
                            // exactly what is applied.
                            container = DsColors.SurfaceSunken,
                            content   = if (filters.isActive) DsColors.Primary else DsColors.TextSecondary,
                            dot       = filters.isActive,
                            onClick   = { showFilterSheet = true }
                        )
                        Spacer(Modifier.width(DsSpacing.sm))
                        Step2Chip(
                            icon      = Icons.Default.Add,
                            label     = "Nouveau produit",
                            container = DsColors.PrimaryLight,
                            content   = DsColors.Primary,
                            onClick   = { showAddProductScreen = true }
                        )
                    }

                    // What is narrowing the list, each removable on its own — the whole chip is the
                    // target, not a 14dp cross.
                    if (activeFilterChips.isNotEmpty()) {
                        LazyRow(
                            contentPadding        = PaddingValues(horizontal = DsSpacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                            modifier              = Modifier.padding(bottom = DsSpacing.sm)
                        ) {
                            items(activeFilterChips, key = { it.first }) { (label, withoutIt) ->
                                Row(
                                    modifier = Modifier
                                        .height(Step2ChipHeight)
                                        .clip(DsShapes.pill)
                                        .background(DsColors.PrimaryLight)
                                        .clickable { session.productFilters = withoutIt }
                                        .padding(horizontal = DsSpacing.md),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
                                ) {
                                    Text(label, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Primary, maxLines = 1)
                                    Icon(Icons.Default.Close, contentDescription = "Retirer", tint = DsColors.Primary, modifier = Modifier.size(14.dp))
                                }
                            }
                            item(key = "clear-all") {
                                Box(
                                    modifier = Modifier
                                        .height(Step2ChipHeight)
                                        .clip(DsShapes.pill)
                                        .clickable { session.productFilters = ProductListFilters() }
                                        .padding(horizontal = DsSpacing.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Tout effacer", fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
                                }
                            }
                        }
                    }

                    if (showFilterSheet) {
                        PurchaseProductFilterSheet(
                            filters        = filters,
                            categories     = categories,
                            sousCategories = sousCategories,
                            marques        = marques,
                            suppliers      = suppliers,
                            resultCount    = filteredProducts.size,
                            onChange       = { session.productFilters = it },
                            onDismiss      = { showFilterSheet = false }
                        )
                    }

                    LazyColumn(
                        state               = step2ListState,
                        modifier            = Modifier.weight(1f),
                        contentPadding      = PaddingValues(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs, bottom = DsSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            val isInCart = cartItems.any { it.product.id == product.id }
                            val isLow    = product.stock < product.min_stock

                            Card(
                                modifier  = Modifier.fillMaxWidth(),
                                shape     = DsShapes.large,
                                colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                                elevation = CardDefaults.cardElevation(1.dp),
                                border    = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    when {
                                        isInCart -> DsColors.Primary
                                        isLow    -> DsColors.Danger
                                        else     -> DsColors.Border
                                    }
                                )
                            ) {
                                Row(
                                    modifier          = Modifier.padding(DsSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier         = Modifier
                                            .size(42.dp)
                                            .clip(DsShapes.medium)
                                            .background(if (isInCart) DsColors.PrimaryLight else DsColors.SurfaceSunken),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        EntityImage(
                                            ref                = product.image_uri,
                                            contentDescription = null,
                                            modifier           = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                if (isInCart) Icons.Default.Check else Icons.Default.ShoppingCart,
                                                contentDescription = null,
                                                tint     = if (isInCart) DsColors.Primary else DsColors.TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            product.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize   = DsTextSize.bodySmall,
                                            color      = DsColors.TextPrimary,
                                            maxLines   = 1
                                        )
                                        Text(
                                            "${product.category_name ?: "—"} · ${"%.2f".format(product.purchase_price)} DA",
                                            fontSize = DsTextSize.caption,
                                            color    = DsColors.TextSecondary
                                        )
                                        Text(
                                            "Stock : ${formatQty(product.stock)} ${product.unit_type}",
                                            fontSize = DsTextSize.caption,
                                            color    = if (isLow) DsColors.Danger else DsColors.TextSecondary
                                        )
                                    }
                                    Spacer(Modifier.width(DsSpacing.sm))
                                    if (!isInCart) {
                                        IconButton(
                                            onClick = {
                                                // One colis to start: for a `pièce` product
                                                // that is its catalogue packaging, still editable
                                                // for this bon. A carton line is built as before.
                                                session.setFormCartItems(cartItems + newPurchaseCartItem(product))
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

                        // Filtering can leave nothing; say so, and offer the way back, instead of an
                        // empty white list that looks like a missing catalogue.
                        if (filteredProducts.isEmpty() && (search.isNotBlank() || filters.isActive)) {
                            item(key = "no-match") {
                                Column(
                                    modifier            = Modifier.fillMaxWidth().padding(top = DsSpacing.xxl),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Aucun produit ne correspond", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                                    TextButton(onClick = {
                                        session.productSearch  = ""
                                        session.productFilters = ProductListFilters()
                                    }) {
                                        Text("Effacer la recherche et les filtres", color = DsColors.Primary, fontSize = DsTextSize.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DsSpacing.xl, vertical = DsSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.pill)
                                .background(if (cartItems.isNotEmpty()) DsColors.SuccessLight else DsColors.SurfaceSunken)
                                .clickable(enabled = cartItems.isNotEmpty()) { navController.navigate(Screen.PurchaseFormCart.route) }
                                .padding(horizontal = 14.dp, vertical = DsSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                        ) {
                            Box(
                                modifier = Modifier.size(20.dp).clip(DsShapes.pill)
                                    .background(if (cartItems.isNotEmpty()) DsColors.Success else DsColors.TextSecondary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${cartItems.size}", color = Color.White, fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "Ma sélection", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold,
                                color = if (cartItems.isNotEmpty()) DsColors.Success else DsColors.TextSecondary
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${"%.2f".format(total)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold,
                                color = if (cartItems.isNotEmpty()) DsColors.Success else DsColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        composable(Screen.PurchaseFormCart.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val session = purchaseFormSession(navController, graphRoute, entry.destination.route)
            val productViewModel = productViewModel()
            val orderIdArg = parentEntry.arguments?.getInt("orderId")?.takeIf { it != -1 }
            val isEdit = orderIdArg != null
            val selectedOrder by viewModel.selectedOrder.collectAsState()
            val editingOrder = if (isEdit) selectedOrder?.takeIf { it.id == orderIdArg } else null
            val formSupplier by session.formSupplier.collectAsState()
            val cartItems by session.formCartItems.collectAsState()
            val note by session.formNote.collectAsState()
            val missingProductIds by session.missingProductIds.collectAsState()
            var expandedCartItemId by remember { mutableStateOf<Int?>(null) }
            val total = cartItems.sumOf { it.quantity * it.unitCost }

            BackHandler { navController.popBackStack() }

            Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
                DsTopAppBar(
                    title    = "Ma sélection",
                    subtitle = formSupplier?.name ?: "",
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

                if (cartItems.isEmpty()) {
                    Box(
                        modifier         = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint     = DsColors.Primary.copy(alpha = 0.3f),
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
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                        modifier            = Modifier.weight(1f)
                    ) {
                        items(cartItems, key = { it.product.id }) { item ->
                            val isExpanded = expandedCartItemId == item.product.id
                            val history by productViewModel.priceHistory.collectAsState()

                            LaunchedEffect(isExpanded, item.product.id) {
                                if (isExpanded) productViewModel.loadPriceHistory(item.product.id)
                            }

                            val priceHistoryValues = history
                                .filterNot { isEdit && editingOrder != null && it.date.take(10) == editingOrder.date.take(10) && it.unit_cost == item.unitCost }
                                .take(4)
                                .map { it.unit_cost }

                            val metaLine = if (item.product.unit_type == "pièce")
                                "${formatQty(item.nbColis)} colis × ${item.uniteParColis} = ${formatQty(item.quantity)} pièces"
                            else
                                "${formatQty(item.quantity)} carton × ${"%.2f".format(item.unitCost)} DA"

                            // A restored draft can name a product that has since been deleted. The
                            // line stays visible, under the name the draft stored, because dropping
                            // it silently would change a total the user remembers — but validation
                            // is blocked until they remove it themselves.
                            val isMissingProduct = item.product.id in missingProductIds

                            SelectionCartCard(
                                avatarIcon      = Icons.Default.ShoppingCart,
                                title           = item.product.name,
                                metaLine        = metaLine,
                                totalPriceLabel = "${"%.2f".format(item.quantity * item.unitCost)} DA",
                                isExpanded      = isExpanded,
                                onToggleExpand  = { expandedCartItemId = if (isExpanded) null else item.product.id },
                                statusLine      = {
                                    if (isMissingProduct) CartStatusLine(
                                        icon = Icons.Default.WarningAmber,
                                        text = "Produit supprimé — retirez cette ligne pour continuer",
                                        tone = CartStatusTone.DANGER,
                                        // Two lines: this one is an instruction, and at one line the
                                        // column cut it at "pour co…" — the actionable half.
                                        maxLines = 2
                                    ) else CartStatusLine(
                                        icon = Icons.Default.ArrowUpward,
                                        text = "Stock ${formatQty(item.product.stock)} → ${formatQty(item.product.stock + item.quantity)} ${item.product.unit_type}",
                                        tone = CartStatusTone.OK
                                    )
                                },
                                expandedContent = {
                                    if (item.product.unit_type == "pièce") {
                                        var uniteParColisStr by remember(item.uniteParColis) { mutableStateOf(item.uniteParColis.toString()) }

                                        // Counted by the colis, so "+" and "−" step one whole colis
                                        // and the pieces follow: 30 to a colis, "+" adds 30. The same
                                        // stepper the carton branch below uses, stepping colis instead
                                        // of cartons.
                                        QuantityStepper(
                                            label         = "Nombre de colis",
                                            value         = item.nbColis,
                                            onValueChange = { newNb ->
                                                // The floor the free-text field enforced before it:
                                                // under one colis is not a quantity to receive.
                                                if (newNb >= 1.0) {
                                                    session.setFormCartItems(cartItems.map { ci ->
                                                        if (ci.product.id == item.product.id) ci.withNbColis(newNb) else ci
                                                    })
                                                }
                                            },
                                            formatValue   = ::formatQty,
                                            min           = 1.0
                                        )

                                        Spacer(Modifier.height(DsSpacing.md))

                                        // Seeded from the product's packaging, editable for this bon
                                        // only. Never written back to the product.
                                        OutlinedTextField(
                                            value         = uniteParColisStr,
                                            onValueChange = { raw ->
                                                val digits = raw.filter { it.isDigit() }
                                                uniteParColisStr = digits
                                                val upe = digits.toIntOrNull()
                                                if (upe != null && upe >= 1) {
                                                    session.setFormCartItems(cartItems.map { ci ->
                                                        if (ci.product.id == item.product.id) ci.withUniteParColis(upe) else ci
                                                    })
                                                }
                                            },
                                            modifier        = Modifier.fillMaxWidth(),
                                            label           = { Text("Unités/colis", fontSize = DsTextSize.caption) },
                                            singleLine      = true,
                                            shape           = DsShapes.medium,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors          = dsTextFieldColors(
                                                unfocusedBorderColor = DsColors.Border,
                                                focusedBorderColor   = DsColors.Primary
                                            )
                                        )
                                        Spacer(Modifier.height(DsSpacing.sm))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(DsShapes.small)
                                                .background(DsColors.SurfaceMuted)
                                                .padding(horizontal = DsSpacing.md, vertical = 8.dp)
                                        ) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                                Text("Total pièces à acheter : ", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                                                Text(
                                                    "${formatQty(item.quantity)} pièces",
                                                    fontSize   = DsTextSize.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color      = DsColors.Primary
                                                )
                                            }
                                        }
                                    } else {
                                        QuantityStepper(
                                            label         = "Nombre de cartons",
                                            value         = item.quantity,
                                            onValueChange = { newQty ->
                                                session.setFormCartItems(cartItems.map { ci ->
                                                    if (ci.product.id == item.product.id) ci.copy(nbColis = newQty, quantity = newQty) else ci
                                                })
                                            },
                                            formatValue   = ::formatQty,
                                            min = 0.01
                                        )
                                    }

                                    Spacer(Modifier.height(DsSpacing.md))

                                    PriceFieldWithHistory(
                                        price         = item.unitCost,
                                        onPriceChange = { newCost ->
                                            session.setFormCartItems(cartItems.map { ci ->
                                                if (ci.product.id == item.product.id) ci.copy(unitCost = newCost) else ci
                                            })
                                        },
                                        priceHistory  = priceHistoryValues
                                    )

                                    Spacer(Modifier.height(DsSpacing.md))

                                    ExpiryToggleField(
                                        hasExpiry          = item.hasExpiry,
                                        expiryDate         = item.expiryDate,
                                        onHasExpiryChange  = { checked ->
                                            session.setFormCartItems(cartItems.map { ci ->
                                                if (ci.product.id == item.product.id) ci.copy(hasExpiry = checked) else ci
                                            })
                                        },
                                        onExpiryDateChange = { date ->
                                            session.setFormCartItems(cartItems.map { ci ->
                                                if (ci.product.id == item.product.id) ci.copy(expiryDate = date) else ci
                                            })
                                        }
                                    )

                                    Spacer(Modifier.height(4.dp))

                                    TextButton(
                                        onClick  = { session.setFormCartItems(cartItems.filter { it.product.id != item.product.id }) },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Retirer", color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                                    }
                                }
                            )
                        }

                        item {
                            OutlinedTextField(
                                value         = note,
                                onValueChange = { session.setFormNote(it) },
                                placeholder   = { Text("Note (optionnel)", fontSize = DsTextSize.body) },
                                modifier      = Modifier.fillMaxWidth(),
                                shape         = DsShapes.medium,
                                minLines      = 2,
                                maxLines      = 3,
                                colors        = dsTextFieldColors(
                                    unfocusedBorderColor = DsColors.Border,
                                    focusedBorderColor   = DsColors.Primary
                                )
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(DsShapes.large)
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
                    // popUpTo(Products, inclusive=false) removes Cart from the back stack when
                    // advancing, so back-from-Validation lands directly on Products — matching
                    // the original monolith, where Cart was a modal overlay on step 2 rather than
                    // a real intermediate step, and jumping to Validation always bypassed it.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick  = { navController.popBackStack() },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape    = DsShapes.large
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(DsSpacing.sm))
                            Text("Retour", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick  = {
                                navController.navigate(Screen.PurchaseFormValidation.route) {
                                    popUpTo(Screen.PurchaseFormProducts.route) { inclusive = false }
                                }
                            },
                            enabled  = cartItems.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape    = DsShapes.large,
                            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                        ) {
                            Text("Suivant →", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        composable(Screen.PurchaseFormValidation.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val session = purchaseFormSession(navController, graphRoute, entry.destination.route)
            val orderIdArg = parentEntry.arguments?.getInt("orderId")?.takeIf { it != -1 }
            val isEdit = orderIdArg != null
            val formSupplier by session.formSupplier.collectAsState()
            val cartItems by session.formCartItems.collectAsState()
            val note by session.formNote.collectAsState()
            val montantPaye by session.formMontantPaye.collectAsState()
            val missingProductIds by session.missingProductIds.collectAsState()
            var isSaving by remember { mutableStateOf(false) }
            val total = cartItems.sumOf { it.quantity * it.unitCost }

            fun doSave() {
                if (formSupplier == null) return
                // A restored draft can reference a product that has since been deleted. Saving
                // anyway would throw "Produit introuvable" deep inside the transaction and roll
                // back with nothing the user can act on, so the line has to go first. Part 3 marks
                // it in the cart; this is the block behind that marker.
                if (missingProductIds.isNotEmpty()) return
                isSaving = true
                val orderItems = cartItems.map { ci ->
                    mapOf(
                        "product_id" to ci.product.id,
                        "quantity"   to ci.quantity,
                        "unit_cost"  to ci.unitCost,
                        "nb_colis" to ci.nbColis,
                        "unite_par_colis" to ci.uniteParColis,
                        "has_expiry" to ci.hasExpiry,
                        "expiry_date" to ci.expiryDate
                    )
                }
                if (isEdit) {
                    viewModel.updateOrder(
                        id          = orderIdArg!!,
                        supplierId  = formSupplier!!.id,
                        items       = orderItems,
                        note        = note.trim().ifEmpty { null },
                        montantPaye = montantPaye.toDoubleOrNull() ?: 0.0,
                        draftId     = session.draftId,
                        onSuccess   = { session.onCommitted(); onSaved() },
                        onError     = { isSaving = false }
                    )
                } else {
                    viewModel.createOrder(
                        supplierId  = formSupplier!!.id,
                        date        = LocalDate.now().toString(),
                        items       = orderItems,
                        note        = note.trim().ifEmpty { null },
                        montantPaye = montantPaye.toDoubleOrNull() ?: 0.0,
                        draftId     = session.draftId,
                        onSuccess   = { session.onCommitted(); onSaved() },
                        onError     = { isSaving = false }
                    )
                }
            }

            BackHandler { navController.popBackStack() }

            Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
                PurchaseFormHeader(
                    isEdit       = isEdit,
                    orderId      = orderIdArg,
                    supplierName = formSupplier?.name,
                    stepLabel    = "Validation",
                    currentStep  = 3,
                    onBackClick  = { navController.popBackStack() }
                )
                Step3Validation(
                    selectedSupplier    = formSupplier,
                    cartItems           = cartItems,
                    total               = total,
                    montantPaye         = montantPaye,
                    onMontantPayeChange = { session.setFormMontantPaye(it) },
                    note                = note,
                    onNoteChange        = { session.setFormNote(it) },
                    isEdit              = isEdit,
                    isSaving            = isSaving,
                    hasMissingProducts  = missingProductIds.isNotEmpty(),
                    onBack              = { navController.popBackStack() },
                    // The cart, not popBackStack: reaching this step pops the cart off
                    // (see the cart's own "Suivant", which popUpTo's the product list), so
                    // going back would land on the products rather than on the line to remove.
                    onFixMissing        = { navController.navigate(Screen.PurchaseFormCart.route) },
                    onConfirm           = { doSave() }
                )
            }
        }
    }
}
