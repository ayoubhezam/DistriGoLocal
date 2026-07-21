package com.distrigo.app.ui.tournees

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.asImageBitmap
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.clients.ClientViewModel
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.products.ProductViewModel
import com.distrigo.app.ui.ventes.VenteViewModel
import com.distrigo.app.ui.scanner.BarcodeScannerScreen


internal fun formatQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

data class TourneeVenteCartItem(
    val product   : Product,
    val quantity  : Double = 1.0,
    val unitPrice : Double
)

@Composable
fun TourneeVenteFormScreen(
    tourneeId          : Int,
    preSelectedClientId: Int? = null,
    onBack              : () -> Unit,
    onSaved             : () -> Unit,
    venteViewModel      : VenteViewModel   = viewModel(),
    productViewModel    : ProductViewModel = viewModel(),
    clientViewModel     : ClientViewModel  = viewModel()
) {
    val products by productViewModel.products.collectAsState()
    val clients  by clientViewModel.clients.collectAsState()

    var currentStep      by remember { mutableStateOf(if (preSelectedClientId != null) 2 else 1) }
    var selectedClient   by remember { mutableStateOf<Client?>(null) }
    var cartItems         by remember { mutableStateOf<List<TourneeVenteCartItem>>(emptyList()) }
    var note              by remember { mutableStateOf("") }
    var montantPaye       by remember { mutableStateOf("") }
    var isSaving          by remember { mutableStateOf(false) }
    var search            by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var showClientPicker  by remember { mutableStateOf(false) }
    var showCart          by remember { mutableStateOf(false) }
    var clientSearch      by remember { mutableStateOf("") }
    var showAddClientScreen by remember { mutableStateOf(false) }


    LaunchedEffect(Unit) {
        productViewModel.loadProducts()
        clientViewModel.loadClients()
    }
    LaunchedEffect(preSelectedClientId, clients) {
        if (preSelectedClientId != null && selectedClient == null) {
            selectedClient = clients.find { it.id == preSelectedClientId }
        }
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

    fun save() {
        if (selectedClient == null) return
        isSaving = true
        val items = cartItems.map { ci ->
            mapOf(
                "product_id" to ci.product.id,
                "quantity"   to ci.quantity,
                "unit_price" to ci.unitPrice
            )
        }
        venteViewModel.createVente(
            clientId    = selectedClient!!.id,
            tourneeId   = tourneeId,
            source      = "camion",
            items       = items,
            note        = note.trim().ifEmpty { null },
            montantPaye = montantPaye.toDoubleOrNull() ?: 0.0,
            onSuccess   = {
                productViewModel.loadProducts()
                clientViewModel.loadClients()
                onSaved()
            },
            onError     = { isSaving = false }
        )
    }

    // ── Add Client Dialog ────────────────────────────────────────────────────
    if (showAddClientScreen) {
        BackHandler { showAddClientScreen = false }
        com.distrigo.app.ui.clients.ClientFormScreen(
            onBack  = { showAddClientScreen = false },
            onSaved = { newClientId ->
                showAddClientScreen = false
                clientViewModel.loadClientsAndUpdate(newClientId) { newClient ->
                    if (newClient != null) {
                        selectedClient = newClient
                        showClientPicker = false
                    }
                }
            }
        )
        return
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
                        TourneeVenteCartRow(
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

    // ── Main Screen ──────────────────────────────────────────────────────────
    fun goBack() {
        when (currentStep) {
            3    -> currentStep = 2
            2    -> if (preSelectedClientId != null) onBack() else currentStep = 1
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
                Text("Vente — Tournée #$tourneeId", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
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
                                                cartItems = cartItems + TourneeVenteCartItem(
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
                isSaving            = isSaving,
                onBack              = { currentStep = 2 },
                onConfirm           = { save() }
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
    cartItems           : List<TourneeVenteCartItem>,
    total               : Double,
    montantPaye         : String,
    onMontantPayeChange : (String) -> Unit,
    note                : String,
    onNoteChange        : (String) -> Unit,
    isSaving            : Boolean,
    onBack              : () -> Unit,
    onConfirm           : () -> Unit
) {
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
private fun TourneeVenteCartRow(
    item             : TourneeVenteCartItem,
    onQuantityChange : (Double) -> Unit,
    onPriceChange    : (Double) -> Unit,
    onRemove         : () -> Unit
) {
    var isExpanded  by remember { mutableStateOf(false) }
    var priceStr    by remember(item.unitPrice) { mutableStateOf("%.2f".format(item.unitPrice)) }

    val availableStock = item.product.camion_stock
    val isLow          = item.quantity > availableStock

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, if (isExpanded) DsColors.Primary else DsColors.Border, DsShapes.large)
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
                modifier         = Modifier.size(36.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.product.name,
                    fontSize   = DsTextSize.body,
                    fontWeight = FontWeight.Medium,
                    color      = DsColors.TextPrimary,
                    maxLines   = 1
                )
                Text(
                    "${formatQty(item.quantity)} × ${"%.2f".format(item.unitPrice)} DA",
                    fontSize = DsTextSize.caption,
                    color    = DsColors.TextSecondary
                )
                Text(
                    "Disponible : ${formatQty(availableStock)} ${item.product.unit_type}",
                    fontSize = DsTextSize.caption,
                    color    = if (isLow) DsColors.Warning else DsColors.TextSecondary
                )
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Text(
                "${"%.2f".format(item.quantity * item.unitPrice)} DA",
                fontSize   = DsTextSize.body,
                fontWeight = FontWeight.Bold,
                color      = DsColors.Primary
            )
            Spacer(Modifier.width(DsSpacing.xs))
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = DsColors.TextSecondary
            )
        }

        // ── Expanded panel ──
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .padding(horizontal = DsSpacing.md)
                    .padding(bottom = DsSpacing.md)
            ) {
                HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
                Spacer(Modifier.height(DsSpacing.md))

                // ── Quantité ──
                Text("Quantité", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick  = { onQuantityChange(item.quantity - 1) },
                        modifier = Modifier.size(36.dp).clip(DsShapes.pill).background(DsColors.SurfaceMuted)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null, tint = DsColors.TextPrimary, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        formatQty(item.quantity),
                        fontSize   = DsTextSize.headline,
                        fontWeight = FontWeight.Medium,
                        color      = DsColors.TextPrimary,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier.widthIn(min = 28.dp).padding(horizontal = DsSpacing.md)
                    )
                    IconButton(
                        onClick  = { if (item.quantity < availableStock) onQuantityChange(item.quantity + 1) },
                        enabled  = item.quantity < availableStock,
                        modifier = Modifier.size(36.dp).clip(DsShapes.pill).background(DsColors.SurfaceMuted)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = if (item.quantity < availableStock) DsColors.TextPrimary else DsColors.TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(DsSpacing.md))

                // ── Prix unitaire ──
                Text("Prix unitaire (DA)", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
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
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
                )

                Spacer(Modifier.height(DsSpacing.xs))

                // ── Retirer ──
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retirer", color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                    }
                }
            }
        }
    }
}
