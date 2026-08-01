package com.distrigo.app.ui.retours

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.scanner.BarcodeScannerScreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal fun formatRetourQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

internal val RETOUR_FOURNISSEUR_MOTIFS = com.distrigo.app.data.model.RetourFournisseurMotifs.ALL.map { it.id }

data class RetourCartItem(
    val product     : Product,
    val quantity    : Double = 1.0,
    val maxQuantity : Double = Double.MAX_VALUE
)

data class ReturnableProduct(val product: Product, val maxQuantity: Double)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RetourFournisseurFormScreen(
    supplierId   : Int,
    supplierName : String,
    viewModel    : RetourFournisseurViewModel = viewModel(),
    onBack       : () -> Unit,
    onSaved      : () -> Unit
) {
    val returnableProducts by viewModel.returnableProducts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    var currentStep    by remember { mutableStateOf(1) }
    var selectedDate    by remember { mutableStateOf(LocalDate.now()) }
    var motif            by remember { mutableStateOf<String?>(null) }
    var cartItems          by remember { mutableStateOf<List<RetourCartItem>>(emptyList()) }
    var search              by remember { mutableStateOf("") }
    var showCart              by remember { mutableStateOf(false) }
    var showDatePicker         by remember { mutableStateOf(false) }
    var motifExpanded           by remember { mutableStateOf(false) }
    var showScanner              by remember { mutableStateOf(false) }
    var isSaving                  by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadReturnableProducts(supplierId) }

    val filteredProducts = returnableProducts.filter { rp ->
        val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        tokens.isEmpty() || tokens.all { token ->
            rp.product.name.contains(token, ignoreCase = true) ||
                    (rp.product.barcode?.contains(token, ignoreCase = true) == true)
        }
    }

    val totalQuantity = cartItems.sumOf { it.quantity }
    val totalValue     = cartItems.sumOf { it.quantity * it.product.purchase_price }

    fun doSave() {
        isSaving = true
        val items = cartItems.map { ci ->
            mapOf("product_id" to ci.product.id, "quantity" to ci.quantity)
        }
        viewModel.createRetour(
            supplierId = supplierId,
            date       = selectedDate.toString(),
            motif      = motif,
            note       = null,
            items      = items,
            onSuccess  = onSaved,
            onError    = { msg -> isSaving = false; Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
        )
    }

    if (showCart) {
        BackHandler { showCart = false }
        Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
            Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showCart = false }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
                }
                Spacer(Modifier.width(DsSpacing.xs))
                Column {
                    Text("Ma sélection", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                    Text("${cartItems.size} produit(s)", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                }
            }
            HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                items(cartItems, key = { it.product.id }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface)
                            .border(1.dp, DsColors.Border, DsShapes.large).padding(DsSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.product.name, fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary, maxLines = 1)
                            Text("${"%.2f".format(item.product.purchase_price)} DA / ${item.product.unit_type}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                            Text("Max : ${formatRetourQty(item.maxQuantity)}", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val newQty = item.quantity - 1
                                    cartItems = if (newQty <= 0) cartItems.filter { it.product.id != item.product.id }
                                    else cartItems.map { if (it.product.id == item.product.id) it.copy(quantity = newQty) else it }
                                },
                                modifier = Modifier.size(32.dp).clip(DsShapes.pill).background(DsColors.SurfaceMuted)
                            ) { Icon(Icons.Default.Remove, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(15.dp)) }
                            Text(
                                formatRetourQty(item.quantity), fontSize = DsTextSize.body, fontWeight = FontWeight.Bold,
                                color = DsColors.Success, modifier = Modifier.widthIn(min = 28.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            val atMax = item.quantity >= item.maxQuantity
                            IconButton(
                                onClick = { cartItems = cartItems.map { if (it.product.id == item.product.id) it.copy(quantity = (it.quantity + 1).coerceAtMost(it.maxQuantity)) else it } },
                                enabled = !atMax,
                                modifier = Modifier.size(32.dp).alpha(if (atMax) 0.4f else 1f).clip(DsShapes.pill).background(DsColors.SurfaceMuted)
                            ) { Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(15.dp)) }
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(DsSpacing.lg)) {
                Button(
                    onClick = {
                        if (motif == null) {
                            Toast.makeText(context, "Veuillez sélectionner un motif", Toast.LENGTH_SHORT).show()
                        } else {
                            showCart = false
                            currentStep = 2
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = DsShapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = DsColors.Success)
                ) { Text("Suivant →", color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        }
        return
    }

    if (showScanner) {
        BackHandler { showScanner = false }
        BarcodeScannerScreen(
            onBarcodeScanned = { code -> search = code; showScanner = false },
            onClose          = { showScanner = false }
        )
        return
    }

    fun goBack() {
        when (currentStep) {
            2    -> currentStep = 1
            else -> onBack()
        }
    }

    BackHandler { goBack() }

    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { goBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Nouveau retour", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Text(supplierName, fontSize = DsTextSize.caption, color = DsColors.Primary)
            }
            Box(
                modifier = Modifier.clip(DsShapes.pill).background(DsColors.PrimaryLight).padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    "${if (currentStep == 1) "Produits" else "Résumé"} · $currentStep/2",
                    fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = DsColors.Primary
                )
            }
        }
        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

        if (currentStep == 1) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
              if (!isLoading && returnableProducts.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = DsColors.Success.copy(alpha = 0.3f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Aucun produit acheté auprès de ce fournisseur", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }
              } else {
                LazyColumn(
                    modifier            = Modifier.weight(1f),
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    // ── Date + Motif (يسحبان بشكل عادي) ──
                    item(key = "date_motif") {
                        Column(
                            modifier            = Modifier.padding(vertical = DsSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
                        ) {
                            Column {
                                Text("Date", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                                Surface(shape = DsShapes.medium, color = DsColors.SurfaceSunken, modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }) {
                                    Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(DsSpacing.sm))
                                            Text(selectedDate.toString(), fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Column {
                                Text("Motif", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                                Box {
                                    Surface(shape = DsShapes.medium, color = DsColors.SurfaceSunken, modifier = Modifier.fillMaxWidth().clickable { motifExpanded = true }) {
                                        Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Notes, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(DsSpacing.sm))
                                                Text(
                                                    motif ?: "Sélectionner un motif",
                                                    fontSize = DsTextSize.body,
                                                    color    = if (motif != null) DsColors.TextPrimary else DsColors.TextTertiary
                                                )
                                            }
                                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    DropdownMenu(expanded = motifExpanded, onDismissRequest = { motifExpanded = false }) {
                                        RETOUR_FOURNISSEUR_MOTIFS.forEach { m ->
                                            DropdownMenuItem(text = { Text(m) }, onClick = { motif = m; motifExpanded = false })
                                        }
                                    }
                                }
                            }

                            Text("Ajouter des produits", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                        }
                    }

                    // ── Recherche (تلتصق أعلى الشاشة عند الوصول إليها) ──
                    stickyHeader(key = "search_header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DsColors.Surface)
                                .padding(vertical = DsSpacing.xs)
                        ) {
                            OutlinedTextField(
                                value         = search,
                                onValueChange = { search = it },
                                placeholder   = { Text("Rechercher par nom ou code-barres…", fontSize = DsTextSize.body) },
                                leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon  = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (search.isNotEmpty()) {
                                            IconButton(onClick = { search = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "Effacer", tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        IconButton(onClick = { showScanner = true }) {
                                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner un code-barres", tint = DsColors.Primary)
                                        }
                                    }
                                },
                                modifier   = Modifier.fillMaxWidth(),
                                shape      = DsShapes.large,
                                singleLine = true,
                                colors     = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary)
                            )
                            Spacer(Modifier.height(DsSpacing.xs))
                            HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
                        }
                    }

                    // ── Product list ──
                    items(filteredProducts, key = { it.product.id }) { rp ->
                        val cartItem = cartItems.find { it.product.id == rp.product.id }
                        RetourProductRow(
                            product     = rp.product,
                            quantity    = cartItem?.quantity,
                            maxQuantity = rp.maxQuantity,
                            onAdd       = { cartItems = cartItems + RetourCartItem(product = rp.product, quantity = 1.0, maxQuantity = rp.maxQuantity) },
                            onQuantityChange = { newQty ->
                                cartItems = if (newQty <= 0) cartItems.filter { it.product.id != rp.product.id }
                                else cartItems.map { if (it.product.id == rp.product.id) it.copy(quantity = newQty) else it }
                            }
                        )
                    }
                }
              }

                // ── Voir la sélection ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(DsShapes.medium)
                            .background(if (cartItems.isNotEmpty()) DsColors.SuccessLight else DsColors.SurfaceSunken)
                            .clickable(enabled = cartItems.isNotEmpty()) { showCart = true }
                            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(20.dp).clip(DsShapes.pill)
                                .background(if (cartItems.isNotEmpty()) DsColors.Success else DsColors.TextTertiary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${cartItems.size}", color = Color.White, fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(DsSpacing.sm))
                        Text(
                            "Voir la sélection", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold,
                            color = if (cartItems.isNotEmpty()) DsColors.Success else DsColors.TextTertiary
                        )
                        if (cartItems.isNotEmpty()) {
                            Spacer(Modifier.width(DsSpacing.xs))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                LazyColumn(
                    modifier            = Modifier.weight(1f),
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    item { Text("Vérifiez les informations", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary) }

                    items(cartItems, key = { it.product.id }) { item ->
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(vertical = DsSpacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.product.name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary, maxLines = 1)
                                Text("${formatRetourQty(item.quantity)} ${item.product.unit_type} × ${"%.2f".format(item.product.purchase_price)} DA", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                            }
                            Text("${"%.2f".format(item.quantity * item.product.purchase_price)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                        }
                        HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
                    }

                    item {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.SuccessLight).padding(DsSpacing.lg)
                        ) {
                            Text("Résumé du retour", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Success)
                            Spacer(Modifier.height(DsSpacing.sm))
                            RetourSummaryRow("Motif", motif ?: "—")
                            RetourSummaryRow("Produits", "${cartItems.size}")
                            RetourSummaryRow("Cartons", formatRetourQty(totalQuantity))
                            RetourSummaryRow("Valeur totale (DA)", "${"%,.2f".format(totalValue)} DA", highlight = true)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                ) {
                    OutlinedButton(onClick = { currentStep = 1 }, modifier = Modifier.weight(1f).height(52.dp), shape = DsShapes.medium) {
                        Text("Modifier")
                    }
                    Button(
                        onClick  = { doSave() },
                        enabled  = !isSaving,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape    = DsShapes.medium,
                        colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                    ) {
                        if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text("Valider le retour", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annuler") } }
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
internal fun RetourProductRow(
    product          : Product,
    quantity         : Double?,
    maxQuantity      : Double,
    onAdd            : () -> Unit,
    onQuantityChange : (Double) -> Unit   // kept for signature compatibility; no longer called from this row
) {
    val isInCart = quantity != null
    Row(
        modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface)
            .border(1.dp, if (isInCart) DsColors.Success else DsColors.Border, DsShapes.large)
            .padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(if (isInCart) DsColors.SuccessLight else DsColors.SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = remember(product.image_uri) {
                product.image_uri?.let { uri ->
                    val bytes = Base64.decode(uri.substringAfter("base64,"), Base64.NO_WRAP)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
            if (bitmap != null) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = if (isInCart) DsColors.Success else DsColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary, maxLines = 1)
            Text(product.category_name ?: "—", fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1)
            Text("Retour max : ${formatRetourQty(maxQuantity)} ${product.unit_type}", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
        }
        Spacer(Modifier.width(DsSpacing.sm))
        if (isInCart) {
            Row(
                modifier = Modifier
                    .clip(DsShapes.pill)
                    .background(DsColors.SuccessLight)
                    .clickable { onQuantityChange(0.0) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ajouté", fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = DsColors.Success)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.Close, contentDescription = "Retirer de la sélection", tint = DsColors.Success, modifier = Modifier.size(14.dp))
            }
        } else {
            IconButton(onClick = onAdd, modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.SuccessLight)) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter", tint = DsColors.Success, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
internal fun RetourSummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.Success)
        Text(
            value,
            fontSize   = if (highlight) DsTextSize.headline else DsTextSize.bodySmall,
            fontWeight = FontWeight.ExtraBold,
            color      = DsColors.Success
        )
    }
}