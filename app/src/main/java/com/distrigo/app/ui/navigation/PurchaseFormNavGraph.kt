package com.distrigo.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
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
import com.distrigo.app.ui.components.CollapsibleHeader
import com.distrigo.app.ui.components.rememberScrollCollapsed
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
import com.distrigo.app.ui.purchases.CartItem
import com.distrigo.app.ui.purchases.PurchaseViewModel
import com.distrigo.app.ui.purchases.Step1Fournisseur
import com.distrigo.app.ui.purchases.Step3Validation
import com.distrigo.app.ui.purchases.formatQty
import java.time.LocalDate

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

// Mirrors venteFormGraph/tourneeVenteFormGraph: form state (supplier/cart/note/montantPaye)
// lives on the PurchaseViewModel instance the caller provides — graph-scoped to AchatsGraph when
// invoked from AchatsNavHost (reusing the same instance AchatsHome/AchatsDetail already share, so
// edit-mode's selectedOrder lookup works), or scoped to this graph's own entry when invoked from
// SuppliersNavHost (no outer PurchaseViewModel to reuse there). See
// PurchaseViewModel.formSupplier/formCartItems/formNote/formMontantPaye.
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
            navArgument("supplierId") { type = NavType.IntType; defaultValue = -1 }
        )
    ) {
        composable(Screen.PurchaseFormSupplier.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val supplierViewModel = supplierViewModel()
            val orderIdArg = parentEntry.arguments?.getInt("orderId")?.takeIf { it != -1 }
            val supplierIdArg = parentEntry.arguments?.getInt("supplierId")?.takeIf { it != -1 }
            val isEdit = orderIdArg != null
            val selectedOrder by viewModel.selectedOrder.collectAsState()
            val suppliers by supplierViewModel.suppliers.collectAsState()
            val formSupplier by viewModel.formSupplier.collectAsState()

            var initialized by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!initialized) {
                    viewModel.resetPurchaseForm()
                    if (orderIdArg != null) viewModel.loadOrderDetail(orderIdArg)
                    initialized = true
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

            var orderPrefillDone by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(editingOrder) {
                if (isEdit && editingOrder != null && !orderPrefillDone) {
                    viewModel.setFormNote(editingOrder.note ?: "")
                    orderPrefillDone = true
                }
            }
            LaunchedEffect(suppliers, editingOrder) {
                if (isEdit && formSupplier == null && editingOrder != null) {
                    viewModel.setFormSupplier(suppliers.find { it.id == editingOrder.supplier_id })
                }
            }
            LaunchedEffect(supplierIdArg, suppliers) {
                if (supplierIdArg != null && formSupplier == null && !isEdit) {
                    viewModel.setFormSupplier(suppliers.find { it.id == supplierIdArg })
                }
            }
            LaunchedEffect(formSupplier, isEdit, supplierIdArg) {
                if (formSupplier != null && (isEdit || supplierIdArg != null)) {
                    navController.navigate(Screen.PurchaseFormProducts.route) {
                        popUpTo(Screen.PurchaseFormSupplier.route) { inclusive = false }
                    }
                }
            }

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
                    selectedSupplier = formSupplier,
                    onChooseSupplier = { navController.navigate(Screen.PurchaseFormSupplierPicker.route) },
                    onNext           = { navController.navigate(Screen.PurchaseFormProducts.route) }
                )
            }
        }

        composable(Screen.PurchaseFormSupplierPicker.route) { entry ->
            remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val supplierViewModel = supplierViewModel()
            val suppliers by supplierViewModel.suppliers.collectAsState()
            val formSupplier by viewModel.formSupplier.collectAsState()
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
                viewModel.setFormSupplier(newSupplier)
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
                OutlinedTextField(
                    value         = supplierSearch,
                    onValueChange = { supplierSearch = it },
                    placeholder   = { Text("Rechercher…", fontSize = DsTextSize.body) },
                    leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DsSpacing.lg)
                        .clip(DsShapes.large),
                    shape         = DsShapes.large,
                    singleLine    = true,
                    colors        = dsTextFieldColors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
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
                                viewModel.setFormSupplier(supplier)
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
            val productViewModel = productViewModel()
            val orderIdArg = parentEntry.arguments?.getInt("orderId")?.takeIf { it != -1 }
            val supplierIdArg = parentEntry.arguments?.getInt("supplierId")?.takeIf { it != -1 }
            val isEdit = orderIdArg != null
            val selectedOrder by viewModel.selectedOrder.collectAsState()
            val editingOrder = if (isEdit) selectedOrder?.takeIf { it.id == orderIdArg } else null
            val products by productViewModel.products.collectAsState()
            val formSupplier by viewModel.formSupplier.collectAsState()
            val cartItems by viewModel.formCartItems.collectAsState()
            var search by remember { mutableStateOf("") }
            var showScanner by remember { mutableStateOf(false) }
            var showAddProductScreen by remember { mutableStateOf(false) }
            var pendingNewProductId by remember { mutableStateOf<Int?>(null) }
            val step2ListState = rememberLazyListState()
            val step2Collapsed by rememberScrollCollapsed(step2ListState)

            // Auto-add a freshly created product ("Nouveau produit") to the cart once the
            // observed products flow actually contains it — the flow's emission arrives
            // asynchronously after ProductFormScreen.onSaved fires, so reading `products` in
            // that callback would race.
            LaunchedEffect(products, pendingNewProductId) {
                val id = pendingNewProductId ?: return@LaunchedEffect
                val newProduct = products.find { it.id == id } ?: return@LaunchedEffect
                if (cartItems.none { it.product.id == id }) {
                    viewModel.setFormCartItems(
                        cartItems + CartItem(
                            product  = newProduct,
                            quantity = 1.0,
                            unitCost = newProduct.purchase_price
                        )
                    )
                }
                pendingNewProductId = null
            }

            LaunchedEffect(products) {
                if (isEdit && cartItems.isEmpty() && editingOrder?.items != null) {
                    viewModel.setFormCartItems(editingOrder.items.map { item ->
                        val product = products.find { it.id == item.product_id }
                            ?: com.distrigo.app.data.model.Product(
                                id             = item.product_id,
                                name           = item.product_name,
                                barcode        = null,
                                selling_price  = 0.0,
                                purchase_price = item.unit_cost,
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
                        CartItem(
                            product       = product,
                            quantity      = item.quantity,
                            unitCost      = item.unit_cost,
                            nbColis       = item.quantity,
                            uniteParColis = 1
                        )
                    })
                }
            }

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
                    onBarcodeScanned = { code -> search = code; showScanner = false },
                    onClose = { showScanner = false }
                )
                return@composable
            }

            BackHandler {
                if (supplierIdArg != null) onBack() else navController.popBackStack()
            }

            val filteredProducts = products.filter { product ->
                val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                tokens.isEmpty() || tokens.all { token ->
                    product.name.contains(token, ignoreCase = true) ||
                            (product.barcode?.contains(token, ignoreCase = true) == true)
                }
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

                    OutlinedTextField(
                        value         = search,
                        onValueChange = { search = it },
                        placeholder   = { Text("Rechercher par nom ou code-barres…", fontSize = DsTextSize.bodySmall) },
                        leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon  = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (search.isNotEmpty()) {
                                    IconButton(onClick = { search = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Effacer", tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
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
                            .padding(top = DsSpacing.md)
                            .clip(DsShapes.large),
                        shape         = DsShapes.large,
                        singleLine    = true,
                        colors        = dsTextFieldColors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary
                        )
                    )

                    CollapsibleHeader(collapsed = step2Collapsed) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                "${filteredProducts.size} produit(s)",
                                fontSize = DsTextSize.bodySmall,
                                color    = DsColors.TextSecondary
                            )
                            OutlinedButton(
                                onClick        = { showAddProductScreen = true },
                                shape          = DsShapes.pill,
                                border         = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Primary),
                                colors         = ButtonDefaults.outlinedButtonColors(contentColor = DsColors.Primary),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Nouveau produit", fontSize = DsTextSize.bodySmall, color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
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
                                                viewModel.setFormCartItems(
                                                    cartItems + CartItem(
                                                        product       = product,
                                                        quantity      = 1.0,
                                                        unitCost      = product.purchase_price,
                                                        nbColis       = 1.0,
                                                        uniteParColis = 1
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
            val productViewModel = productViewModel()
            val orderIdArg = parentEntry.arguments?.getInt("orderId")?.takeIf { it != -1 }
            val isEdit = orderIdArg != null
            val selectedOrder by viewModel.selectedOrder.collectAsState()
            val editingOrder = if (isEdit) selectedOrder?.takeIf { it.id == orderIdArg } else null
            val formSupplier by viewModel.formSupplier.collectAsState()
            val cartItems by viewModel.formCartItems.collectAsState()
            val note by viewModel.formNote.collectAsState()
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
                        TextButton(onClick = { viewModel.setFormCartItems(emptyList()) }) {
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

                            SelectionCartCard(
                                avatarIcon      = Icons.Default.ShoppingCart,
                                title           = item.product.name,
                                metaLine        = metaLine,
                                totalPriceLabel = "${"%.2f".format(item.quantity * item.unitCost)} DA",
                                isExpanded      = isExpanded,
                                onToggleExpand  = { expandedCartItemId = if (isExpanded) null else item.product.id },
                                statusLine      = {
                                    CartStatusLine(
                                        icon = Icons.Default.ArrowUpward,
                                        text = "Stock ${formatQty(item.product.stock)} → ${formatQty(item.product.stock + item.quantity)} ${item.product.unit_type}",
                                        tone = CartStatusTone.OK
                                    )
                                },
                                expandedContent = {
                                    if (item.product.unit_type == "pièce") {
                                        var nbColisStr       by remember(item.nbColis)       { mutableStateOf(formatQty(item.nbColis)) }
                                        var uniteParColisStr by remember(item.uniteParColis) { mutableStateOf(item.uniteParColis.toString()) }

                                        Text("Quantité à réceptionner", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                                        Spacer(Modifier.height(6.dp))
                                        Row(
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            OutlinedTextField(
                                                value         = nbColisStr,
                                                onValueChange = { raw ->
                                                    val filtered = raw.filter { it.isDigit() || it == '.' }.let { s ->
                                                        val dot = s.indexOf('.')
                                                        if (dot < 0) s
                                                        else s.substring(0, dot + 1) + s.substring(dot + 1).filter { it.isDigit() }
                                                    }
                                                    nbColisStr = filtered
                                                    val nb = filtered.toDoubleOrNull()
                                                    if (nb != null && nb >= 1) {
                                                        viewModel.setFormCartItems(cartItems.map { ci ->
                                                            if (ci.product.id == item.product.id)
                                                                ci.copy(nbColis = nb, quantity = nb * ci.uniteParColis)
                                                            else ci
                                                        })
                                                    }
                                                },
                                                modifier        = Modifier.weight(1f),
                                                label           = { Text("Nb colis", fontSize = DsTextSize.caption) },
                                                singleLine      = true,
                                                shape           = DsShapes.medium,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                colors          = dsTextFieldColors(
                                                    unfocusedBorderColor = DsColors.Border,
                                                    focusedBorderColor   = DsColors.Primary
                                                )
                                            )
                                            Text(
                                                "×",
                                                fontSize   = DsTextSize.title,
                                                fontWeight = FontWeight.Bold,
                                                color      = DsColors.TextSecondary
                                            )
                                            OutlinedTextField(
                                                value         = uniteParColisStr,
                                                onValueChange = { raw ->
                                                    val digits = raw.filter { it.isDigit() }
                                                    uniteParColisStr = digits
                                                    val upe = digits.toIntOrNull()
                                                    if (upe != null && upe >= 1) {
                                                        viewModel.setFormCartItems(cartItems.map { ci ->
                                                            if (ci.product.id == item.product.id)
                                                                ci.copy(uniteParColis = upe, quantity = ci.nbColis * upe)
                                                            else ci
                                                        })
                                                    }
                                                },
                                                modifier        = Modifier.weight(1f),
                                                label           = { Text("Unités/colis", fontSize = DsTextSize.caption) },
                                                singleLine      = true,
                                                shape           = DsShapes.medium,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                colors          = dsTextFieldColors(
                                                    unfocusedBorderColor = DsColors.Border,
                                                    focusedBorderColor   = DsColors.Primary
                                                )
                                            )
                                        }
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
                                                viewModel.setFormCartItems(cartItems.map { ci ->
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
                                            viewModel.setFormCartItems(cartItems.map { ci ->
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
                                            viewModel.setFormCartItems(cartItems.map { ci ->
                                                if (ci.product.id == item.product.id) ci.copy(hasExpiry = checked) else ci
                                            })
                                        },
                                        onExpiryDateChange = { date ->
                                            viewModel.setFormCartItems(cartItems.map { ci ->
                                                if (ci.product.id == item.product.id) ci.copy(expiryDate = date) else ci
                                            })
                                        }
                                    )

                                    Spacer(Modifier.height(4.dp))

                                    TextButton(
                                        onClick  = { viewModel.setFormCartItems(cartItems.filter { it.product.id != item.product.id }) },
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
                                onValueChange = { viewModel.setFormNote(it) },
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
            val orderIdArg = parentEntry.arguments?.getInt("orderId")?.takeIf { it != -1 }
            val isEdit = orderIdArg != null
            val formSupplier by viewModel.formSupplier.collectAsState()
            val cartItems by viewModel.formCartItems.collectAsState()
            val note by viewModel.formNote.collectAsState()
            val montantPaye by viewModel.formMontantPaye.collectAsState()
            var isSaving by remember { mutableStateOf(false) }
            val total = cartItems.sumOf { it.quantity * it.unitCost }

            fun doSave() {
                if (formSupplier == null) return
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
                        onSuccess   = { onSaved() },
                        onError     = { isSaving = false }
                    )
                } else {
                    viewModel.createOrder(
                        supplierId  = formSupplier!!.id,
                        date        = LocalDate.now().toString(),
                        items       = orderItems,
                        note        = note.trim().ifEmpty { null },
                        montantPaye = montantPaye.toDoubleOrNull() ?: 0.0,
                        onSuccess   = { onSaved() },
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
                    onMontantPayeChange = { viewModel.setFormMontantPaye(it) },
                    note                = note,
                    onNoteChange        = { viewModel.setFormNote(it) },
                    isEdit              = isEdit,
                    isSaving            = isSaving,
                    onBack              = { navController.popBackStack() },
                    onConfirm           = { doSave() }
                )
            }
        }
    }
}
