package com.distrigo.app.ui.ventes

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.clients.ClientViewModel
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.products.ProductViewModel
import com.distrigo.app.ui.scanner.BarcodeScannerScreen
import androidx.compose.ui.platform.LocalContext

internal fun formatQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

data class VenteCartItem(
    val product   : Product,
    val quantity  : Double = 1.0,
    val unitPrice : Double
)

@Composable
fun VenteFormScreen(
    vente            : com.distrigo.app.data.model.Vente? = null,
    onBack           : () -> Unit,
    onSaved          : () -> Unit,
    venteViewModel   : VenteViewModel   = viewModel(),
    productViewModel : ProductViewModel = viewModel(),
    clientViewModel  : ClientViewModel  = viewModel()
) {
    val isEdit = vente != null

    val products by productViewModel.products.collectAsState()
    val clients  by clientViewModel.clients.collectAsState()

    var currentStep      by remember { mutableStateOf(if (isEdit) 2 else 1) }
    // Client is matched from the clients list once loaded, since Vente
    // only carries client_name, not a full Client object (see LaunchedEffect below)
    var selectedClient   by remember { mutableStateOf<Client?>(null) }
    val selectedSource   = "depot"
    var cartItems         by remember { mutableStateOf<List<VenteCartItem>>(emptyList()) }
    var note              by remember { mutableStateOf(vente?.note ?: "") }
    var userName          by remember { mutableStateOf("") }
    var montantPaye       by remember { mutableStateOf(if (isEdit) (vente?.montant_paye ?: 0.0).toString() else "") }
    var isSaving          by remember { mutableStateOf(false) }
    var saveError          by remember { mutableStateOf("") }
    var search            by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var showClientPicker  by remember { mutableStateOf(false) }
    var showAddClientScreen by remember { mutableStateOf(false) }
    var showCart          by remember { mutableStateOf(false) }
    var clientSearch      by remember { mutableStateOf("") }


    LaunchedEffect(Unit) {
        productViewModel.loadProducts()
        clientViewModel.loadClients()
    }

    LaunchedEffect(clients) {
        if (isEdit && selectedClient == null && clients.isNotEmpty()) {
            selectedClient = clients.find { it.id == vente!!.client_id }
        }
    }

    LaunchedEffect(products, vente) {
        if (isEdit && cartItems.isEmpty() && vente!!.items != null && products.isNotEmpty()) {
            cartItems = vente.items.map { item ->
                val product = products.find { it.id == item.product_id }
                    ?: Product(
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
                VenteCartItem(
                    product   = product,
                    quantity  = item.quantity,
                    unitPrice = item.unit_price
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

    val total = cartItems.sumOf { it.quantity * it.unitPrice }

    fun doSave() {
        if (selectedClient == null) return
        isSaving = true
        saveError = ""
        val items = cartItems.map { ci ->
            mapOf(
                "product_id" to ci.product.id,
                "quantity"   to ci.quantity,
                "unit_price" to ci.unitPrice
            )
        }
        if (isEdit) {
            venteViewModel.updateVente(
                id          = vente!!.id,
                clientId    = selectedClient!!.id,
                items       = items,
                note        = note.trim().ifEmpty { null },
                montantPaye = montantPaye.toDoubleOrNull() ?: 0.0,
                userName    = userName.trim().ifEmpty { null },
                onSuccess   = {
                    productViewModel.loadProducts()
                    clientViewModel.loadClients()
                    onSaved()
                },
                onError     = { error -> isSaving = false; saveError = error }
            )
        } else {
            venteViewModel.createVente(
                clientId    = selectedClient!!.id,
                tourneeId   = null,
                source      = selectedSource,
                items       = items,
                note        = note.trim().ifEmpty { null },
                montantPaye = montantPaye.toDoubleOrNull() ?: 0.0,
                userName    = userName.trim().ifEmpty { null },
                onSuccess   = {
                    productViewModel.loadProducts()
                    onSaved()
                },
                onError     = { error -> isSaving = false; saveError = error }
            )
        }
    }

    // ── Client Picker Sub-screen ─────────────────────────────────────────────
    if (showClientPicker) {
        BackHandler { showClientPicker = false }
        Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
            Row(
                modifier          = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showClientPicker = false }) {
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
                                selectedClient  = client
                                showClientPicker = false
                                clientSearch     = ""
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
        }
        return
    }

    // ── Add Client Sub-screen ────────────────────────────────────────────────
    if (showAddClientScreen) {
        BackHandler { showAddClientScreen = false }
        com.distrigo.app.ui.clients.ClientFormScreen(
            onBack  = { showAddClientScreen = false },
            onSaved = { newClientId ->
                showAddClientScreen = false
                clientViewModel.loadClientsAndUpdate(newClientId) { newClient ->
                    if (newClient != null) {
                        selectedClient = newClient
                    }
                }
            }
        )
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

    // ── Cart Sub-screen ──────────────────────────────────────────────────────
    if (showCart) {
        BackHandler { showCart = false }
        Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showCart = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
                    }
                    Spacer(Modifier.width(DsSpacing.xs))
                    Column {
                        Text("Ma sélection", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                        Text("${cartItems.size} article(s)", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                }
                if (cartItems.isNotEmpty()) {
                    TextButton(onClick = { cartItems = emptyList() }) {
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
                            onClick = { showCart = false },
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
                        VenteCartRow(
                            item             = item,
                            onQuantityChange = { newQty ->
                                cartItems = cartItems.map {
                                    if (it.product.id == item.product.id) it.copy(quantity = maxOf(1.0, newQty)) else it
                                }
                            },
                            onPriceChange = { newPrice ->
                                cartItems = cartItems.map {
                                    if (it.product.id == item.product.id) it.copy(unitPrice = newPrice) else it
                                }
                            },
                            onRemove = {
                                cartItems = cartItems.filter { it.product.id != item.product.id }
                            }
                        )
                    }

                    item {
                        OutlinedTextField(
                            value         = note,
                            onValueChange = { note = it },
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

                // ── Save button (always visible, no scrolling needed) ──
                Button(
                    onClick  = { showCart = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                        .height(52.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Valider la sélection", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
        return
    }

    // ── Main Screen ──────────────────────────────────────────────────────────
    fun goBack() {
        when (currentStep) {
            3    -> currentStep = 2
            2    -> currentStep = 1
            else -> onBack()
        }
    }

    BackHandler { goBack() }

    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
        // ── Compact header ──
        val stepLabel = when (currentStep) {
            1    -> "Client"
            2    -> "Produits"
            3    -> "Validation"
            else -> ""
        }
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { goBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(if (isEdit) "Modifier la vente #${vente!!.id}" else "Vente dépôt", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Text(
                    selectedClient?.name ?: "Choisir un client",
                    fontSize = DsTextSize.caption,
                    color    = if (selectedClient != null) DsColors.Primary else DsColors.TextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .clip(DsShapes.pill)
                    .background(DsColors.PrimaryLight)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
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
            1 -> Step1Client(
                selectedClient = selectedClient,
                onChooseClient = { showClientPicker = true },
                onAddNewClient = { showAddClientScreen = true },
                onNext         = { currentStep = 2 }
            )

                2 -> Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                                                cartItems = cartItems + VenteCartItem(
                                                    product   = product,
                                                    quantity  = 1.0,
                                                    unitPrice = product.selling_price
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
                }

                // ── Cart summary + Suivant ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(DsShapes.medium)
                            .background(if (cartItems.isNotEmpty()) DsColors.PrimaryLight else DsColors.SurfaceSunken)
                            .clickable(enabled = cartItems.isNotEmpty()) { showCart = true }
                            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
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

                    Button(
                        onClick        = { currentStep = 3 },
                        enabled        = cartItems.isNotEmpty(),
                        shape          = DsShapes.medium,
                        colors         = ButtonDefaults.buttonColors(containerColor = DsColors.Primary),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Text("Suivant →", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            3 -> Step3Validation(
                selectedClient      = selectedClient,
                cartItems           = cartItems,
                total               = total,
                montantPaye         = montantPaye,
                onMontantPayeChange = { montantPaye = it },
                note                = note,
                onNoteChange        = { note = it },
                userName            = userName,
                onUserNameChange    = { userName = it },
                isSaving            = isSaving,
                saveError           = saveError,
                onBack              = { currentStep = 2 },
                onConfirm           = { doSave() }
            )

            else -> Unit
        }
    }
}

// ── Step 1 : Client ──────────────────────────────────────────────────────────
@Composable
private fun Step1Client(
    selectedClient : Client?,
    onChooseClient : () -> Unit,
    onAddNewClient : () -> Unit,
    onNext         : () -> Unit
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
                Icon(Icons.Default.Person, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(44.dp))
            }

            Spacer(Modifier.height(DsSpacing.xl))

            Text(
                "Choisir un client",
                fontSize   = DsTextSize.headline,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(DsSpacing.xs))

            Text(
                "Sélectionnez le client avant d'ajouter des produits",
                fontSize  = DsTextSize.bodySmall,
                color     = DsColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(DsSpacing.xxl))

            if (selectedClient == null) {
                Button(
                    onClick  = onChooseClient,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text("Choisir un client", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(DsSpacing.sm))

                OutlinedButton(
                    onClick  = onAddNewClient,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape    = DsShapes.medium
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text("Nouveau client", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(DsSpacing.lg))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.PrimaryLight)
                        .padding(DsSpacing.md)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                        Text(
                            "Pourquoi d'abord le client ?",
                            fontSize   = DsTextSize.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = DsColors.Primary
                        )
                    }
                    Spacer(Modifier.height(DsSpacing.xs))
                    Text(
                        "Le solde et l'historique du client permettent de vérifier sa situation avant de préparer la vente. " +
                            "Cela évite les erreurs de facturation et de règlement.",
                        fontSize = DsTextSize.caption,
                        color    = DsColors.TextPrimary
                    )
                }

                Spacer(Modifier.height(DsSpacing.md))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.SurfaceMuted)
                        .padding(DsSpacing.md),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Text(
                        "La recherche de produits sera disponible après la sélection",
                        fontSize = DsTextSize.bodySmall,
                        color    = DsColors.TextSecondary
                    )
                }
            } else {
                val typeColors = when (selectedClient.customer_type) {
                    "wholesale" -> DsColors.TagWholesale
                    "business"  -> DsColors.TagBusiness
                    else        -> DsColors.TagRetail
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.Surface)
                        .border(1.dp, DsColors.Primary, DsShapes.large)
                        .padding(DsSpacing.lg)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val initials = selectedClient.name.split(" ").take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                        Box(
                            modifier         = Modifier.size(46.dp).clip(DsShapes.medium).background(typeColors.second),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(initials, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = typeColors.first)
                        }
                        Spacer(Modifier.width(DsSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selectedClient.name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                            Text(
                                "Solde : ${"%.2f".format(selectedClient.balance)} DA",
                                fontSize = DsTextSize.bodySmall,
                                color    = if (selectedClient.balance > 0) DsColors.Danger else DsColors.TextSecondary
                            )
                        }
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(DsSpacing.md))
                    OutlinedButton(
                        onClick  = onChooseClient,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = DsShapes.medium
                    ) {
                        Text("Changer", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Button(
            onClick  = onNext,
            enabled  = selectedClient != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                .height(52.dp),
            shape  = DsShapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            Text("Suivant →", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Step 3 : Validation ──────────────────────────────────────────────────────
@Composable
private fun Step3Validation(
    selectedClient      : Client?,
    cartItems           : List<VenteCartItem>,
    total               : Double,
    montantPaye         : String,
    onMontantPayeChange : (String) -> Unit,
    note                : String,
    onNoteChange        : (String) -> Unit,
    userName            : String,
    onUserNameChange    : (String) -> Unit,
    isSaving            : Boolean,
    saveError           : String,
    onBack              : () -> Unit,
    onConfirm           : () -> Unit
)    {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Text("Validation", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }

        LazyColumn(
            modifier            = Modifier.weight(1f).fillMaxWidth(),
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            // ── Client summary card ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.Surface)
                        .border(1.dp, DsColors.Border, DsShapes.large)
                        .padding(DsSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(DsSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedClient?.name ?: "—", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                        Text(
                            "Solde du client : ${"%.2f".format(selectedClient?.balance ?: 0.0)} DA",
                            fontSize = DsTextSize.bodySmall,
                            color    = if ((selectedClient?.balance ?: 0.0) > 0) DsColors.Danger else DsColors.TextSecondary
                        )
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
                    modifier   = Modifier.padding(top = DsSpacing.xs)
                )
            }

            // ── Articles list (read-only) ──
            items(cartItems, key = { it.product.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.Surface)
                        .border(1.dp, DsColors.Border, DsShapes.large)
                        .padding(DsSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
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
                    Spacer(Modifier.width(DsSpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.product.name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1)
                        Text("${formatQty(item.quantity)} × ${"%.2f".format(item.unitPrice)} DA", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text(
                        "${"%.2f".format(item.quantity * item.unitPrice)} DA",
                        fontSize   = DsTextSize.body,
                        fontWeight = FontWeight.Bold,
                        color      = DsColors.Primary
                    )
                }
            }

            // ── Total ──
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

// ── Montant payé ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.medium)
                        .background(DsColors.SurfaceMuted)
                        .padding(DsSpacing.md)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Montant payé (DA)", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                        TextButton(onClick = { onMontantPayeChange("%.2f".format(total)) }) {
                            Text("Tout réglé", fontSize = DsTextSize.caption, color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(DsSpacing.xs))
                    OutlinedTextField(
                        value           = montantPaye,
                        onValueChange   = { raw -> onMontantPayeChange(raw.filter { it.isDigit() || it == '.' }) },
                        placeholder     = { Text("0.00", fontSize = DsTextSize.body) },
                        modifier        = Modifier.fillMaxWidth(),
                        shape           = DsShapes.small,
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
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
                            Text("Reste", fontSize = DsTextSize.caption, color = DsColors.Danger)
                            Text("${"%.2f".format(reste)} DA", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Danger)
                        }
                    } else if (paye >= total && total > 0) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Statut", fontSize = DsTextSize.caption, color = DsColors.Success)
                            Box(
                                modifier = Modifier
                                    .clip(DsShapes.pill)
                                    .background(DsColors.SuccessLight)
                                    .padding(horizontal = DsSpacing.sm, vertical = 3.dp)
                            ) {
                                Text("Réglé ✓", fontSize = DsTextSize.caption, color = DsColors.Success, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            // ── Effectué par ──
            item {
                OutlinedTextField(
                    value         = userName,
                    onValueChange = onUserNameChange,
                    placeholder   = { Text("Effectué par (optionnel)", fontSize = DsTextSize.body) },
                    leadingIcon   = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = DsShapes.medium,
                    singleLine    = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
                )
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
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
                )
            }

            // ── Erreur de sauvegarde ──
            item {
                if (saveError.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.medium)
                            .background(DsColors.DangerLight)
                            .padding(DsSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(18.dp))
                        Text(saveError, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                    }
                }
            }
        }

        // ── Confirm button ──
        Button(
            onClick  = onConfirm,
            enabled  = !isSaving && selectedClient != null && cartItems.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                .height(52.dp),
            shape  = DsShapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = DsColors.Success)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(
                    "Confirmer la vente · ${"%.2f".format(total)} DA",
                    fontSize   = DsTextSize.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Cart row (expandable) ────────────────────────────────────────────────────
@Composable
private fun VenteCartRow(
    item             : VenteCartItem,
    onQuantityChange : (Double) -> Unit,
    onPriceChange    : (Double) -> Unit,
    onRemove         : () -> Unit
) {
    var isExpanded  by remember { mutableStateOf(false) }
    var priceStr    by remember(item.unitPrice) { mutableStateOf("%.2f".format(item.unitPrice)) }
    val context = LocalContext.current

    val availableStock = item.product.stock - item.product.camion_stock   // dépôt uniquement (stock = total)
    val remainingAfter = availableStock - item.quantity
    val isNegative     = remainingAfter < 0
    val isLow          = !isNegative && remainingAfter <= item.product.min_stock
    val progressColor  = when {
        isNegative -> Color(0xFFEF4444)
        isLow      -> Color(0xFFF59E0B)
        else       -> Color(0xFF22C55E)
    }
    val progressFraction = if (availableStock > 0)
        (remainingAfter.toFloat() / availableStock.toFloat()).coerceIn(0f, 1f)
    else 0f

    LaunchedEffect(isNegative) {
        if (isNegative) {
            com.distrigo.app.ui.components.vibrateWarning(context)
        }
    }

    val cardBg     = if (isNegative) Color(0xFFFEF2F2) else DsColors.Surface
    val cardBorder = if (isNegative) Color(0xFFEF4444) else if (isExpanded) DsColors.Primary else DsColors.Border
    val accentColor = if (isNegative) Color(0xFFDC2626) else DsColors.Primary
    val mutedColor   = if (isNegative) Color(0xFFB91C1C) else DsColors.TextSecondary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(cardBg)
            .border(1.5.dp, cardBorder, DsShapes.large)
    ) {
        // ── Collapsed header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(DsSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier.size(36.dp).clip(DsShapes.medium).background(if (isNegative) Color(0xFFFEE2E2) else DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isNegative) Icons.Default.Warning else Icons.Default.ShoppingCart,
                    contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.product.name,
                    fontSize   = DsTextSize.body,
                    fontWeight = FontWeight.Medium,
                    color      = if (isNegative) Color(0xFF1A1A1A) else DsColors.TextPrimary,
                    maxLines   = 1
                )
                Text(
                    "${formatQty(item.quantity)} × ${"%.2f".format(item.unitPrice)} DA",
                    fontSize = DsTextSize.caption,
                    color    = mutedColor
                )
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Text(
                "${"%.2f".format(item.quantity * item.unitPrice)} DA",
                fontSize   = DsTextSize.body,
                fontWeight = FontWeight.Bold,
                color      = accentColor
            )
            Spacer(Modifier.width(DsSpacing.xs))
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = accentColor
            )
        }
        // ── Live stock indicator ──
        Column(
            modifier = Modifier
                .padding(horizontal = DsSpacing.md)
                .padding(bottom = DsSpacing.sm)
                .fillMaxWidth()
                .clip(DsShapes.medium)
                .background(DsColors.SurfaceMuted)
                .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Stock avant : ${formatQty(availableStock)}",
                    fontSize = DsTextSize.caption,
                    color = mutedColor
                )
                Text(
                    "Vendu : ${formatQty(item.quantity)}",
                    fontSize = DsTextSize.caption,
                    color = mutedColor
                )
            }

            Spacer(Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(DsShapes.pill),
                color = progressColor,
                trackColor = DsColors.Border,
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Stock restant",
                    fontSize = DsTextSize.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = mutedColor
                )
                Text(
                    "${formatQty(remainingAfter)} ${item.product.unit_type}",
                    fontSize = DsTextSize.body,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }
        }

        if (isNegative) {
            Row(
                modifier = Modifier
                    .padding(horizontal = DsSpacing.md)
                    .padding(bottom = DsSpacing.sm)
                    .fillMaxWidth()
                    .clip(DsShapes.medium)
                    .background(Color(0xFFFEE2E2))
                    .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
            )

            {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                Text(
                    "Stock négatif : ${formatQty(remainingAfter)} ${item.product.unit_type} disponible",
                    fontSize = DsTextSize.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFDC2626)
                )
            }
        }

        // ── Expanded panel ──
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .padding(horizontal = DsSpacing.md)
                    .padding(bottom = DsSpacing.md)
            ) {
                HorizontalDivider(color = if (isNegative) Color(0xFFFCA5A5) else DsColors.Border, thickness = 1.dp)
                Spacer(Modifier.height(DsSpacing.md))

                Text("Quantité", fontSize = DsTextSize.caption, color = mutedColor)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick  = {
                            val newQty = item.quantity - 1
                            onQuantityChange(newQty)
                            if (availableStock - newQty < 0) com.distrigo.app.ui.components.vibrateWarning(context)
                        },
                        modifier = Modifier.size(36.dp).clip(DsShapes.pill).background(if (isNegative) Color(0xFFFEE2E2) else DsColors.SurfaceMuted)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        formatQty(item.quantity),
                        fontSize   = DsTextSize.headline,
                        fontWeight = FontWeight.Medium,
                        color      = accentColor,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier.widthIn(min = 28.dp).padding(horizontal = DsSpacing.md)
                    )
                    IconButton(
                        onClick  = {
                            val newQty = item.quantity + 1
                            onQuantityChange(newQty)
                            if (availableStock - newQty < 0) com.distrigo.app.ui.components.vibrateWarning(context)
                        },
                        modifier = Modifier.size(36.dp).clip(DsShapes.pill).background(if (isNegative) Color(0xFFFEE2E2) else DsColors.SurfaceMuted)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(DsSpacing.md))

                Text("Prix unitaire (DA)", fontSize = DsTextSize.caption, color = mutedColor)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value         = priceStr,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() || it == '.' }.let { s ->
                            val dot = s.indexOf('.')
                            if (dot < 0) s
                            else s.substring(0, dot + 1) + s.substring(dot + 1).filter { it.isDigit() }
                        }
                        priceStr = filtered
                        val price = filtered.toDoubleOrNull()
                        if (price != null && price >= 0) onPriceChange(price)
                    },
                    modifier        = Modifier.fillMaxWidth(),
                    singleLine      = true,
                    shape           = DsShapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = if (isNegative) Color(0xFFFCA5A5) else DsColors.Border,
                        focusedBorderColor   = accentColor
                    )
                )

                Spacer(Modifier.height(DsSpacing.xs))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retirer", color = Color(0xFFDC2626), fontSize = DsTextSize.bodySmall)
                    }
                }
            }
        }
    }
}
