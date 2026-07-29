package com.distrigo.app.ui.purchases

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.PurchaseOrder
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.products.ProductViewModel
import com.distrigo.app.ui.scanner.BarcodeScannerScreen
import com.distrigo.app.ui.suppliers.SupplierViewModel
import com.distrigo.app.ui.suppliers.formatDZD
import java.time.LocalDate
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import com.distrigo.app.ui.components.rememberScrollCollapsed
import com.distrigo.app.ui.components.CollapsibleHeader

internal fun formatQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

data class CartItem(
    val product       : Product,
    val quantity      : Double,   // final quantity sent to API
    val unitCost      : Double,
    val nbColis       : Double = 1.0,
    val uniteParColis : Int = 1,  // only used for pièce
    val hasExpiry     : Boolean = false,
    val expiryDate    : String? = null   // "yyyy-MM-dd"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseFormScreen(
    order                 : PurchaseOrder?     = null,
    preSelectedSupplierId : Int?               = null,
    onBack                : () -> Unit,
    onSaved               : () -> Unit,
    purchaseViewModel     : PurchaseViewModel  = viewModel(),
    productViewModel      : ProductViewModel   = viewModel(),
    supplierViewModel     : SupplierViewModel  = viewModel()
) {
    val isEdit = order != null

    val products  by productViewModel.products.collectAsState()
    val suppliers by supplierViewModel.suppliers.collectAsState()

    var selectedSupplier   by remember { mutableStateOf<Supplier?>(null) }
    var cartItems          by remember { mutableStateOf<List<CartItem>>(emptyList()) }
    var note               by remember { mutableStateOf(if (isEdit) order!!.note ?: "" else "") }
    var isSaving           by remember { mutableStateOf(false) }
    var search             by remember { mutableStateOf("") }
    var showSupplierPicker by remember { mutableStateOf(false) }
    var showCart           by remember { mutableStateOf(false) }
    var showScanner        by remember { mutableStateOf(false) }
    var supplierSearch     by remember { mutableStateOf("") }
    var montantPaye by remember { mutableStateOf("") }
    var currentStep by remember { mutableStateOf(if (isEdit || preSelectedSupplierId != null) 2 else 1) }
    var showAddSupplierDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var newSupplierName       by remember { mutableStateOf("") }
    var newSupplierPhone      by remember { mutableStateOf("") }
    var showAddProductScreen by remember { mutableStateOf(false) }
    val step2ListState = rememberLazyListState()
    val step2Collapsed by rememberScrollCollapsed(step2ListState)

    LaunchedEffect(Unit) {
        productViewModel.loadProducts()
        supplierViewModel.loadSuppliers()
    }

    LaunchedEffect(suppliers) {
        if (isEdit && selectedSupplier == null) {
            selectedSupplier = suppliers.find { it.id == order!!.supplier_id }
        }
    }

    LaunchedEffect(preSelectedSupplierId, suppliers) {
        if (preSelectedSupplierId != null && selectedSupplier == null) {
            selectedSupplier = suppliers.find { it.id == preSelectedSupplierId }
        }
    }

    LaunchedEffect(products) {
        if (isEdit && cartItems.isEmpty() && order!!.items != null) {
            cartItems = order.items.map { item ->
                val product = products.find { it.id == item.product_id }
                    ?: Product(
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
            }
        }
    }

    val filteredProducts = products.filter { product ->
        val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        tokens.isEmpty() || tokens.all { token ->
            product.name.contains(token, ignoreCase = true) ||
                    (product.barcode?.contains(token, ignoreCase = true) == true)
        }
    }

    val total = cartItems.sumOf { it.quantity * it.unitCost }

    fun doSave() {
        if (selectedSupplier == null) return
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
            purchaseViewModel.updateOrder(
                id          = order!!.id,
                supplierId  = selectedSupplier!!.id,
                items       = orderItems,
                note        = note.trim().ifEmpty { null },
                montantPaye = montantPaye.toDoubleOrNull() ?: 0.0,  // ← أضف هذا
                onSuccess   = { onSaved() },
                onError     = { isSaving = false }
            )
        } else {
            purchaseViewModel.createOrder(
                supplierId = selectedSupplier!!.id,
                date       = LocalDate.now().toString(),
                items      = orderItems,
                note       = note.trim().ifEmpty { null },
                montantPaye = montantPaye.toDoubleOrNull() ?: 0.0,
                onSuccess  = { onSaved() },
                onError    = { isSaving = false }
            )
        }
    }



    // ── Supplier Picker Sub-screen ──────────────────────────────────────────
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
                        colors        = OutlinedTextFieldDefaults.colors(
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
                        colors        = OutlinedTextFieldDefaults.colors(
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
                                supplierViewModel.loadSuppliers()
                                // اختر المورد الجديد تلقائياً
                                val newSupplier = suppliers.find { it.name == newSupplierName.trim() }
                                if (newSupplier != null) {
                                    selectedSupplier   = newSupplier
                                    showSupplierPicker = false
                                }
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
            }
        )
    }


    if (showSupplierPicker) {
        BackHandler { showSupplierPicker = false }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DsColors.Surface)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSupplierPicker = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                    Text("Choisir un fournisseur", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                }
                OutlinedButton(
                    onClick = { showAddSupplierDialog = true },
                    shape   = DsShapes.pill,
                    border  = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("", fontSize = DsTextSize.bodySmall, color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
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
                colors        = OutlinedTextFieldDefaults.colors(
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
                            selectedSupplier   = supplier
                            showSupplierPicker = false
                            supplierSearch     = ""
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
                                    Text(supplier.phone?:"", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                                }
                            }
                            if (selectedSupplier?.id == supplier.id) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
        return
    }

    if (showAddProductScreen) {
        BackHandler { showAddProductScreen = false }
        com.distrigo.app.ui.products.ProductFormScreen(
            onBack  = { showAddProductScreen = false },
            onSaved = { newProductId ->
                showAddProductScreen = false
                productViewModel.loadProducts()
                val newProduct = products.find { it.id == newProductId }
                if (newProduct != null) {
                    cartItems = cartItems + CartItem(
                        product  = newProduct,
                        quantity = 1.0,
                        unitCost = newProduct.purchase_price
                    )
                }
            }
        )
        return
    }

// ── Cart Sub-screen ─────────────────────────────────────────────────────
    if (showCart) {
        BackHandler { showCart = false }
        Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
            // Header
            Row(
                modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showCart = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                    Column {
                        Text("Ma sélection", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                        Text(selectedSupplier?.name ?: "", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                    }
                }
                if (cartItems.isNotEmpty()) {
                    TextButton(onClick = { cartItems = emptyList() }) {
                        Text("Vider", color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                    }
                }
            }

            // ── TOP PART (scrollable, takes remaining space) ──
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
                            onClick = { showCart = false },
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
                    // ── Expandable cart item cards ──
                    items(cartItems, key = { it.product.id }) { item ->
                        var isExpanded        by remember { mutableStateOf(false) }
                        var nbColisStr        by remember(item.nbColis)       { mutableStateOf(formatQty(item.nbColis)) }
                        var uniteParColisStr  by remember(item.uniteParColis) { mutableStateOf(item.uniteParColis.toString()) }
                        var unitCostStr       by remember(item.unitCost)      { mutableStateOf("%.2f".format(item.unitCost)) }
                        var nbColisManualStr  by remember(item.nbColis)       { mutableStateOf(formatQty(item.nbColis)) }

                        val subtitle = if (item.product.unit_type == "pièce")
                            "${formatQty(item.nbColis)} colis × ${item.uniteParColis} = ${formatQty(item.quantity)} pièces"
                        else
                            "${formatQty(item.nbColis)} cartons · ${"%.2f".format(item.unitCost)} DA/u"

                        Card(
                            modifier  = Modifier.fillMaxWidth(),
                            shape     = DsShapes.large,
                            colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                            elevation = CardDefaults.cardElevation(1.dp),
                            border    = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isExpanded) DsColors.Primary else DsColors.Border
                            )
                        ) {
                            Column {
                                // ── Collapsed row (always visible) ──
                                Row(
                                    modifier              = Modifier
                                        .fillMaxWidth()
                                        .clickable { isExpanded = !isExpanded }
                                        .padding(DsSpacing.md),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier              = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier         = Modifier.size(38.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val bitmap = remember(item.product.image_uri) {
                                                item.product.image_uri?.let { uri ->
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
                                                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Column {
                                            Text(
                                                item.product.name,
                                                fontSize   = DsTextSize.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color      = DsColors.TextPrimary,
                                                maxLines   = 1
                                            )
                                            Text(subtitle, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                        }
                                    }
                                    Spacer(Modifier.width(DsSpacing.sm))
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            "${"%.2f".format(item.quantity * item.unitCost)} DA",
                                            fontSize   = DsTextSize.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color      = DsColors.Primary
                                        )
                                        Icon(
                                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint     = DsColors.TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // ── Expanded section ──
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .padding(horizontal = DsSpacing.md)
                                            .padding(bottom = DsSpacing.md)
                                    ) {
                                        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
                                        Spacer(Modifier.height(DsSpacing.md))

                                        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                                            StatBox(
                                                modifier = Modifier.weight(1f),
                                                label    = "Stock actuel",
                                                value    = "${formatQty(item.product.stock)} ${item.product.unit_type}",
                                                color    = DsColors.TextPrimary
                                            )
                                            StatBox(
                                                modifier = Modifier.weight(1f),
                                                label    = "Après réception",
                                                value    = "${formatQty(item.product.stock + item.quantity)} ${item.product.unit_type}",
                                                color    = DsColors.Success
                                            )
                                        }

                                        Spacer(Modifier.height(DsSpacing.md))

                                        if (item.product.unit_type == "pièce") {
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
                                                            cartItems = cartItems.map { ci ->
                                                                if (ci.product.id == item.product.id)
                                                                    ci.copy(nbColis = nb, quantity = nb * ci.uniteParColis)
                                                                else ci
                                                            }
                                                        }
                                                    },
                                                    modifier        = Modifier.weight(1f),
                                                    label           = { Text("Nb colis", fontSize = DsTextSize.caption) },
                                                    singleLine      = true,
                                                    shape           = DsShapes.medium,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    colors          = OutlinedTextFieldDefaults.colors(
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
                                                            cartItems = cartItems.map { ci ->
                                                                if (ci.product.id == item.product.id)
                                                                    ci.copy(uniteParColis = upe, quantity = ci.nbColis * upe)
                                                                else ci
                                                            }
                                                        }
                                                    },
                                                    modifier        = Modifier.weight(1f),
                                                    label           = { Text("Unités/colis", fontSize = DsTextSize.caption) },
                                                    singleLine      = true,
                                                    shape           = DsShapes.medium,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    colors          = OutlinedTextFieldDefaults.colors(
                                                        unfocusedBorderColor = DsColors.Border,
                                                        focusedBorderColor   = DsColors.Primary
                                                    )
                                                )
                                            }
                                            Spacer(Modifier.height(DsSpacing.sm))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(DsShapes.medium)
                                                    .background(DsColors.PrimaryLight)
                                                    .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm)
                                            ) {
                                                Row(
                                                    modifier              = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment     = Alignment.CenterVertically
                                                ) {
                                                    Text("Total pièces à acheter", fontSize = DsTextSize.bodySmall, color = DsColors.Primary)
                                                    Text(
                                                        "${formatQty(item.quantity)} pièces",
                                                        fontSize   = DsTextSize.body,
                                                        fontWeight = FontWeight.Bold,
                                                        color      = DsColors.Primary
                                                    )
                                                }
                                            }
                                        } else {
                                            Text("Nombre de cartons", fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary, fontWeight = FontWeight.Medium)
                                            Spacer(Modifier.height(6.dp))
                                            Row(
                                                verticalAlignment     = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                                            ) {
                                                IconButton(
                                                    onClick  = {
                                                        val newNb = maxOf(1.0, item.nbColis - 1)
                                                        nbColisManualStr = formatQty(newNb)
                                                        cartItems = cartItems.map { ci ->
                                                            if (ci.product.id == item.product.id)
                                                                ci.copy(nbColis = newNb, quantity = newNb)
                                                            else ci
                                                        }
                                                    },
                                                    modifier = Modifier.size(36.dp).clip(DsShapes.medium).background(DsColors.SurfaceSunken)
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = null, tint = DsColors.TextPrimary, modifier = Modifier.size(16.dp))
                                                }

                                                OutlinedTextField(
                                                    value         = nbColisManualStr,
                                                    onValueChange = { raw ->
                                                        val filtered = raw.filter { it.isDigit() || it == '.' }.let { s ->
                                                            val dot = s.indexOf('.')
                                                            if (dot < 0) s
                                                            else s.substring(0, dot + 1) + s.substring(dot + 1).filter { it.isDigit() }
                                                        }
                                                        nbColisManualStr = filtered
                                                        val nb = filtered.toDoubleOrNull()
                                                        if (nb != null && nb >= 1) {
                                                            cartItems = cartItems.map { ci ->
                                                                if (ci.product.id == item.product.id)
                                                                    ci.copy(nbColis = nb, quantity = nb)
                                                                else ci
                                                            }
                                                        }
                                                    },
                                                    modifier        = Modifier.weight(1f),
                                                    singleLine      = true,
                                                    textStyle       = androidx.compose.ui.text.TextStyle(
                                                        fontSize = DsTextSize.body, fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Center, color = DsColors.Primary
                                                    ),
                                                    shape           = DsShapes.medium,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    colors          = OutlinedTextFieldDefaults.colors(
                                                        unfocusedBorderColor = DsColors.Border,
                                                        focusedBorderColor   = DsColors.Primary
                                                    )
                                                )

                                                IconButton(
                                                    onClick  = {
                                                        val newNb = item.nbColis + 1
                                                        nbColisManualStr = formatQty(newNb)
                                                        cartItems = cartItems.map { ci ->
                                                            if (ci.product.id == item.product.id)
                                                                ci.copy(nbColis = newNb, quantity = newNb)
                                                            else ci
                                                        }
                                                    },
                                                    modifier = Modifier.size(36.dp).clip(DsShapes.medium).background(DsColors.SurfaceSunken)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.TextPrimary, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(DsSpacing.md))

                                        Row(
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                                        ) {
                                            OutlinedTextField(
                                                value         = unitCostStr,
                                                onValueChange = { raw ->
                                                    val filtered = raw.filter { it.isDigit() || it == '.' }.let { s ->
                                                        val dot = s.indexOf('.')
                                                        if (dot < 0) s
                                                        else s.substring(0, dot + 1) + s.substring(dot + 1).filter { it.isDigit() }
                                                    }
                                                    unitCostStr = filtered
                                                    val cost = filtered.toDoubleOrNull()
                                                    if (cost != null && cost > 0) {
                                                        cartItems = cartItems.map { ci ->
                                                            if (ci.product.id == item.product.id) ci.copy(unitCost = cost) else ci
                                                        }
                                                    }
                                                },
                                                modifier        = Modifier.weight(1f),
                                                label           = { Text("Prix unitaire (DA)") },
                                                singleLine      = true,
                                                shape           = DsShapes.medium,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                colors          = OutlinedTextFieldDefaults.colors(
                                                    unfocusedBorderColor = DsColors.Border,
                                                    focusedBorderColor   = DsColors.Primary
                                                )
                                            )

                                            var showPriceHistory by remember { mutableStateOf(false) }
                                            val history by productViewModel.priceHistory.collectAsState()
                                            val hasHistory = history.isNotEmpty()

                                            Box {
                                                IconButton(
                                                    onClick = {
                                                        productViewModel.loadPriceHistory(item.product.id)
                                                        showPriceHistory = true
                                                    },
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(DsShapes.medium)
                                                        .background(if (hasHistory) DsColors.PrimaryLight else DsColors.SurfaceSunken)
                                                ) {
                                                    Icon(
                                                        Icons.Default.History,
                                                        contentDescription = "Historique des prix",
                                                        tint = if (hasHistory) DsColors.Primary else DsColors.TextSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                DropdownMenu(
                                                    expanded = showPriceHistory,
                                                    onDismissRequest = { showPriceHistory = false },
                                                    modifier = Modifier.background(DsColors.Surface)
                                                ) {
                                                    Text(
                                                        "DERNIERS PRIX",
                                                        fontSize = DsTextSize.caption,
                                                        fontWeight = FontWeight.Bold,
                                                        color = DsColors.TextSecondary,
                                                        modifier = Modifier.padding(horizontal = DsSpacing.md, vertical = 6.dp)
                                                    )

                                                    val filteredHistory = history
                                                        .filterNot { isEdit && order != null && it.date.take(10) == order.date.take(10) && it.unit_cost == item.unitCost }
                                                        .take(4)

                                                    if (filteredHistory.isEmpty()) {
                                                        Text(
                                                            "Aucun historique disponible",
                                                            fontSize = DsTextSize.caption,
                                                            color = DsColors.TextSecondary,
                                                            modifier = Modifier.padding(DsSpacing.md)
                                                        )
                                                    } else {
                                                        val minPrice = filteredHistory.minOf { it.unit_cost }
                                                        val maxPrice = filteredHistory.maxOf { it.unit_cost }

                                                        filteredHistory.forEach { h ->
                                                            DropdownMenuItem(
                                                                text = {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Column {
                                                                            Text(h.supplier_name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                                                                            Text(h.date.take(10), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                                                        }
                                                                        Text(
                                                                            "${"%.2f".format(h.unit_cost)} DA",
                                                                            fontSize = DsTextSize.bodySmall,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = when {
                                                                                h.unit_cost == minPrice -> DsColors.Success
                                                                                h.unit_cost == maxPrice -> DsColors.Danger
                                                                                else -> DsColors.TextPrimary
                                                                            }
                                                                        )
                                                                    }
                                                                },
                                                                onClick = {
                                                                    unitCostStr = "%.2f".format(h.unit_cost)
                                                                    cartItems = cartItems.map { ci ->
                                                                        if (ci.product.id == item.product.id) ci.copy(unitCost = h.unit_cost) else ci
                                                                    }
                                                                    showPriceHistory = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(DsSpacing.md))

                                        // ── Date d'expiration (optionnelle) ──
                                        var showExpiryPicker by remember { mutableStateOf(false) }
                                        val expiryDateState = rememberDatePickerState(
                                            initialSelectedDateMillis = item.expiryDate?.let {
                                                runCatching {
                                                    java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                                                }.getOrNull()
                                            }
                                        )

                                        if (showExpiryPicker) {
                                            DatePickerDialog(
                                                onDismissRequest = { showExpiryPicker = false },
                                                confirmButton = {
                                                    TextButton(onClick = {
                                                        expiryDateState.selectedDateMillis?.let { millis ->
                                                            val date = java.time.Instant.ofEpochMilli(millis)
                                                                .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                                                            cartItems = cartItems.map { ci ->
                                                                if (ci.product.id == item.product.id) ci.copy(expiryDate = date) else ci
                                                            }
                                                        }
                                                        showExpiryPicker = false
                                                    }) { Text("OK") }
                                                },
                                                dismissButton = { TextButton(onClick = { showExpiryPicker = false }) { Text("Annuler") } }
                                            ) { DatePicker(state = expiryDateState) }
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(DsShapes.medium)
                                                .background(DsColors.SurfaceSunken)
                                                .padding(DsSpacing.md)
                                        ) {
                                            Row(
                                                modifier              = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment     = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Icon(Icons.Default.Info, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(14.dp))
                                                    Column {
                                                        Text("Date d'expiration", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                                                        Text("Le produit a une date d'expiration", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                                    }
                                                }
                                                Switch(
                                                    checked         = item.hasExpiry,
                                                    onCheckedChange = { checked ->
                                                        cartItems = cartItems.map { ci ->
                                                            if (ci.product.id == item.product.id) ci.copy(hasExpiry = checked) else ci
                                                        }
                                                    },
                                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DsColors.Primary)
                                                )
                                            }

                                            AnimatedVisibility(visible = item.hasExpiry) {
                                                Column(modifier = Modifier.padding(top = DsSpacing.sm)) {
                                                    OutlinedTextField(
                                                        value         = item.expiryDate ?: "",
                                                        onValueChange = {},
                                                        readOnly      = true,
                                                        label         = { Text("Date d'expiration", fontSize = DsTextSize.caption) },
                                                        trailingIcon  = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                                                        modifier      = Modifier.fillMaxWidth().clickable { showExpiryPicker = true },
                                                        shape         = DsShapes.medium,
                                                        enabled       = false,
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            disabledBorderColor = DsColors.Border,
                                                            disabledTextColor   = DsColors.TextPrimary,
                                                            disabledLabelColor  = DsColors.TextSecondary,
                                                            disabledTrailingIconColor = DsColors.Primary
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(4.dp))

                                        TextButton(
                                            onClick  = { cartItems = cartItems.filter { it.product.id != item.product.id } },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Retirer", color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Note ──
                    item {
                        OutlinedTextField(
                            value         = note,
                            onValueChange = { note = it },
                            placeholder   = { Text("Note (optionnel)", fontSize = DsTextSize.body) },
                            modifier      = Modifier.fillMaxWidth(),
                            shape         = DsShapes.medium,
                            minLines      = 2,
                            maxLines      = 3,
                            colors        = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = DsColors.Border,
                                focusedBorderColor   = DsColors.Primary
                            )
                        )
                    }

                    // ── Total ──
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick  = { showCart = false },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape    = DsShapes.large
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(DsSpacing.sm))
                        Text("Retour", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick  = { showCart = false; currentStep = 3 },
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
        return
    }

    // ── Barcode Scanner Sub-screen ──────────────────────────────────────────
    if (showScanner) {
        BackHandler { showScanner = false }
        BarcodeScannerScreen(
            onBarcodeScanned = { code ->
                search = code
                showScanner = false
            },
            onClose = { showScanner = false }
        )
        return
    }

    // ── Main Screen ─────────────────────────────────────────────────────────
    fun goBack() {
        when (currentStep) {
            3    -> currentStep = 2
            2    -> if (preSelectedSupplierId != null) onBack() else currentStep = 1
            else -> onBack()
        }
    }

    BackHandler { goBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        // ── Compact header ──
        val stepLabel = when (currentStep) {
            1    -> "Fournisseur"
            2    -> "Produits"
            3    -> "Validation"
            else -> ""
        }
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { goBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isEdit) "Modifier le bon #${order!!.id}" else "Nouveau bon",
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color      = DsColors.TextPrimary
                )
                Text(
                    selectedSupplier?.name ?: "Choisir un fournisseur",
                    fontSize = DsTextSize.caption,
                    color    = if (selectedSupplier != null) DsColors.Primary else DsColors.TextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .clip(DsShapes.pill)
                    .background(DsColors.PrimaryLight)
                    .padding(horizontal = DsSpacing.sm, vertical = 5.dp)
            ) {
                Text(
                    "$stepLabel · $currentStep/3",
                    fontSize   = DsTextSize.caption,
                    fontWeight = FontWeight.Bold,
                    color      = DsColors.Primary
                )
            }
        }
        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

        when (currentStep) {
            1 -> Step1Fournisseur(
                selectedSupplier = selectedSupplier,
                onChooseSupplier = { showSupplierPicker = true },
                onNext           = { currentStep = 2 }
            )

            2 -> Column(modifier = Modifier.weight(1f).fillMaxWidth()) {

                // ── Search (ثابتة دائمًا، خارج منطقة التمرير) ──
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
                    colors        = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
                )

                // ── العدد + زر Nouveau produit (يختفي/يظهر مع التمرير) ──
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

                // ── Products list (تتمدد لتأخذ المساحة الإضافية عند اختفاء الصف أعلاه) ──
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
                                            cartItems = cartItems + CartItem(
                                                product       = product,
                                                quantity      = 1.0,
                                                unitCost      = product.purchase_price,
                                                nbColis       = 1.0,
                                                uniteParColis = 1
                                            )
                                        },
                                        modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight)
                                    ) {
                                        Icon(Icons.Default.AddShoppingCart, contentDescription = "Ajouter au panier", tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                                    }
                                } else {
                                    IconButton(
                                        onClick = { cartItems = cartItems.filter { it.product.id != product.id } },
                                        modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.SuccessLight)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Ajouté", tint = DsColors.Success, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Cart summary + Suivant (ثابت دائمًا) ──
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
                            .clickable(enabled = cartItems.isNotEmpty()) { showCart = true }
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

            3 -> Step3Validation(
                selectedSupplier        = selectedSupplier,
                cartItems               = cartItems,
                total                   = total,
                montantPaye             = montantPaye,
                onMontantPayeChange     = { montantPaye = it },
                note                    = note,
                onNoteChange            = { note = it },
                isEdit                  = isEdit,
                isSaving                = isSaving,
                onBack                  = { currentStep = 2 },
                onConfirm               = { doSave() }
            )

            else -> Unit
        }
    }
}

// ── Step indicator ──────────────────────────────────────────────────────────
@Composable
private fun StepIndicator(currentStep: Int) {
    val steps = listOf("Fournisseur", "Produits", "Validation")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DsSpacing.xxl, vertical = DsSpacing.lg),
        verticalAlignment = Alignment.Top
    ) {
        steps.forEachIndexed { index, label ->
            val step        = index + 1
            val isCompleted = step < currentStep
            val isActive    = step == currentStep

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(DsShapes.pill)
                        .background(if (isCompleted || isActive) DsColors.Primary else Color.Transparent)
                        .border(
                            width = if (isCompleted || isActive) 0.dp else 1.5.dp,
                            color = if (isCompleted || isActive) Color.Transparent else DsColors.Border,
                            shape = DsShapes.pill
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            step.toString(),
                            fontSize   = DsTextSize.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color      = if (isActive) Color.White else DsColors.TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(DsSpacing.xs))
                Text(
                    label,
                    fontSize   = DsTextSize.caption,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (isActive || isCompleted) DsColors.Primary else DsColors.TextSecondary,
                    textAlign  = TextAlign.Center
                )
            }

            if (index < steps.lastIndex) {
                HorizontalDivider(
                    modifier  = Modifier
                        .weight(1f)
                        .padding(top = 15.dp, start = 4.dp, end = 4.dp),
                    thickness = 2.dp,
                    color     = if (step < currentStep) DsColors.Primary else DsColors.Border
                )
            }
        }
    }
}

// ── Step 1 : Fournisseur ─────────────────────────────────────────────────────
@Composable
private fun Step1Fournisseur(
    selectedSupplier : Supplier?,
    onChooseSupplier : () -> Unit,
    onNext           : () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DsSpacing.xl, vertical = DsSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(DsSpacing.xxl))

            Box(
                modifier         = Modifier.size(88.dp).clip(DsShapes.large).background(DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Business, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(44.dp))
            }

            Spacer(Modifier.height(DsSpacing.xl))

            Text(
                "Choisir un fournisseur",
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "Sélectionnez le fournisseur avant d'ajouter des produits",
                fontSize  = DsTextSize.bodySmall,
                color     = DsColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(DsSpacing.xxl))

            if (selectedSupplier == null) {
                Button(
                    onClick  = onChooseSupplier,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = DsShapes.large,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text("Choisir un fournisseur", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(DsSpacing.lg))

                // ── Info box ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.PrimaryLight)
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                        Text(
                            "Pourquoi d'abord le fournisseur ?",
                            fontSize   = DsTextSize.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = DsColors.Primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Les prix d'achat et les produits disponibles dépendent du fournisseur choisi. " +
                            "Cela permet de proposer une liste de produits pertinente et d'éviter les erreurs de saisie.",
                        fontSize = DsTextSize.caption,
                        color    = DsColors.TextPrimary
                    )
                }

                Spacer(Modifier.height(DsSpacing.md))

                // ── Search preview (disabled) ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.SurfaceSunken)
                        .padding(14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Text(
                        "La recherche sera disponible après la sélection",
                        fontSize = DsTextSize.bodySmall,
                        color    = DsColors.TextSecondary
                    )
                }
            } else {
                // ── Selected supplier card ──
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = DsShapes.large,
                    colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Primary)
                ) {
                    Column(modifier = Modifier.padding(DsSpacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val avatarColors = listOf(0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A, 0xFFC62828, 0xFFE65100, 0xFF00695C)
                            val color        = Color(avatarColors[selectedSupplier.name[0].code % avatarColors.size])
                            val initials     = selectedSupplier.name.split(" ").take(2)
                                .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                            Box(
                                modifier         = Modifier.size(46.dp).clip(DsShapes.pill),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.fillMaxSize().background(color.copy(alpha = 0.13f)))
                                Text(initials, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = color)
                            }
                            Spacer(Modifier.width(DsSpacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(selectedSupplier.name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                                Text(
                                    "Solde : ${formatDZD(selectedSupplier.balance)} DA",
                                    fontSize = DsTextSize.bodySmall,
                                    color    = if (selectedSupplier.balance > 0) DsColors.Danger else DsColors.TextSecondary
                                )
                            }
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(DsSpacing.md))
                        OutlinedButton(
                            onClick  = onChooseSupplier,
                            modifier = Modifier.fillMaxWidth(),
                            shape    = DsShapes.medium
                        ) {
                            Text("Changer", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // ── Suivant ──
        Button(
            onClick  = onNext,
            enabled  = selectedSupplier != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                .height(52.dp),
            shape  = DsShapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            Text("Suivant →", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Step 3 : Validation ──────────────────────────────────────────────────────
@Composable
private fun Step3Validation(
    selectedSupplier        : Supplier?,
    cartItems               : List<CartItem>,
    total                   : Double,
    montantPaye             : String,
    onMontantPayeChange     : (String) -> Unit,
    note                    : String,
    onNoteChange            : (String) -> Unit,
    isEdit                  : Boolean,
    isSaving                : Boolean,
    onBack                  : () -> Unit,
    onConfirm               : () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ──
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Text("Validation", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }

        LazyColumn(
            modifier            = Modifier.weight(1f).fillMaxWidth(),
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Supplier summary card ──
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = DsShapes.large,
                    colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Business, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(DsSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selectedSupplier?.name ?: "—", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                            Text(
                                "Solde du fournisseur : ${formatDZD(selectedSupplier?.balance ?: 0.0)} DA",
                                fontSize = DsTextSize.bodySmall,
                                color    = if ((selectedSupplier?.balance ?: 0.0) > 0) DsColors.Danger else DsColors.TextSecondary
                            )
                        }
                    }
                }
            }

            // ── Articles header ──
            item {
                Text(
                    "Articles (${cartItems.size})",
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = DsColors.TextSecondary,
                    modifier   = Modifier.padding(top = 4.dp)
                )
            }

// ── Articles list (read-only) ──
            items(cartItems, key = { it.product.id }) { item ->
                val quantityLine = if (item.product.unit_type == "pièce")
                    "${formatQty(item.nbColis)} colis × ${item.uniteParColis} = ${formatQty(item.quantity)} pièces"
                else
                    "${formatQty(item.nbColis)} cartons"

                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = DsShapes.large,
                    colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                ) {
                    Row(modifier = Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier         = Modifier.size(38.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            val bitmap = remember(item.product.image_uri) {
                                item.product.image_uri?.let { uri ->
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
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.product.name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1)
                            Text(quantityLine, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                            Text("${"%.2f".format(item.unitCost)} DA/u", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                        }
                        Spacer(Modifier.width(DsSpacing.sm))
                        Text(
                            "${"%.2f".format(item.quantity * item.unitCost)} DA",
                            fontSize   = DsTextSize.body,
                            fontWeight = FontWeight.Bold,
                            color      = DsColors.Primary
                        )
                    }
                }
            }

            // ── Total ──
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

            // ── Montant payé ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.SurfaceSunken)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Montant payé (DA)", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                        TextButton(
                            onClick = { onMontantPayeChange("%.2f".format(total)) }
                        ) {
                            Text("Tout réglé", fontSize = DsTextSize.bodySmall, color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value         = montantPaye,
                        onValueChange = { raw -> onMontantPayeChange(raw.filter { it.isDigit() || it == '.' }) },
                        placeholder     = { Text("0.00", fontSize = DsTextSize.body) },
                        modifier        = Modifier.fillMaxWidth(),
                        shape           = DsShapes.medium,
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors          = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary
                        )
                    )

                    val paye  = montantPaye.toDoubleOrNull() ?: 0.0
                    val reste = total - paye
                    if (paye > 0 && reste > 0) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Reste", fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                            Text(
                                "${"%.2f".format(reste)} DA",
                                fontSize   = DsTextSize.body,
                                fontWeight = FontWeight.Bold,
                                color      = DsColors.Danger
                            )
                        }
                    } else if (paye >= total && total > 0) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Statut", fontSize = DsTextSize.bodySmall, color = DsColors.Success)
                            Box(
                                modifier = Modifier
                                    .clip(DsShapes.pill)
                                    .background(DsColors.SuccessLight)
                                    .padding(horizontal = DsSpacing.sm, vertical = 3.dp)
                            ) {
                                Text("Réglé ✓", fontSize = DsTextSize.bodySmall, color = DsColors.Success, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ── Note ──
            item {
                OutlinedTextField(
                    value         = note,
                    onValueChange = onNoteChange,
                    placeholder   = { Text("Note (optionnel)", fontSize = DsTextSize.body) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = DsShapes.medium,
                    minLines      = 2,
                    maxLines      = 3,
                    colors        = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
                )
            }
        }

        // ── Confirm button ──
        Button(
            onClick  = onConfirm,
            enabled  = !isSaving && selectedSupplier != null && (cartItems.isNotEmpty() || isEdit),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                .height(52.dp),
            shape    = DsShapes.large,
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (isEdit) DsColors.Primary else DsColors.Success
            )
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(
                    if (isEdit) "Enregistrer · ${"%.2f".format(total)} DA"
                    else        "Confirmer la commande · ${"%.2f".format(total)} DA",
                    fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
