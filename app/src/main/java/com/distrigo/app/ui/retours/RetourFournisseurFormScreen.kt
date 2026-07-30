package com.distrigo.app.ui.retours

import android.graphics.BitmapFactory
import android.util.Base64
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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

internal val RETOUR_FOURNISSEUR_MOTIFS = listOf(
    "Produit défectueux",
    "Produit périmé",
    "Erreur de commande",
    "Excédent de stock",
    "Autre"
)

data class RetourCartItem(
    val product  : Product,
    val quantity : Double = 1.0
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RetourFournisseurFormScreen(
    supplierId   : Int,
    supplierName : String,
    viewModel    : RetourFournisseurViewModel = viewModel(),
    onBack       : () -> Unit,
    onSaved      : () -> Unit
) {
    val products by viewModel.products.collectAsState()

    var currentStep    by remember { mutableStateOf(1) }
    var selectedDate    by remember { mutableStateOf(LocalDate.now()) }
    var motif            by remember { mutableStateOf<String?>(null) }
    var note              by remember { mutableStateOf("") }
    var cartItems          by remember { mutableStateOf<List<RetourCartItem>>(emptyList()) }
    var search              by remember { mutableStateOf("") }
    var showDatePicker       by remember { mutableStateOf(false) }
    var motifExpanded         by remember { mutableStateOf(false) }
    var showScanner            by remember { mutableStateOf(false) }
    var isSaving                by remember { mutableStateOf(false) }
    var saveError                 by remember { mutableStateOf("") }

    val filteredProducts = products.filter { product ->
        val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        tokens.isEmpty() || tokens.all { token ->
            product.name.contains(token, ignoreCase = true) ||
                    (product.barcode?.contains(token, ignoreCase = true) == true)
        }
    }

    val totalQuantity = cartItems.sumOf { it.quantity }
    val totalValue     = cartItems.sumOf { it.quantity * it.product.purchase_price }

    fun doSave() {
        isSaving = true
        saveError = ""
        val items = cartItems.map { ci ->
            mapOf("product_id" to ci.product.id, "quantity" to ci.quantity)
        }
        viewModel.createRetour(
            supplierId = supplierId,
            date       = selectedDate.toString(),
            motif      = motif,
            note       = note.trim().ifEmpty { null },
            items      = items,
            onSuccess  = onSaved,
            onError    = { msg -> isSaving = false; saveError = msg }
        )
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
                    items(filteredProducts, key = { it.id }) { product ->
                        val cartItem = cartItems.find { it.product.id == product.id }
                        RetourProductRow(
                            product  = product,
                            quantity = cartItem?.quantity,
                            onAdd    = { cartItems = cartItems + RetourCartItem(product = product, quantity = 1.0) },
                            onQuantityChange = { newQty ->
                                cartItems = if (newQty <= 0) cartItems.filter { it.product.id != product.id }
                                else cartItems.map { if (it.product.id == product.id) it.copy(quantity = newQty) else it }
                            }
                        )
                    }
                }

                // ── Bottom bar ──
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).clip(DsShapes.medium)
                            .background(if (cartItems.isNotEmpty()) DsColors.PrimaryLight else DsColors.SurfaceSunken)
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
                            "Produits sélectionnés", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold,
                            color = if (cartItems.isNotEmpty()) DsColors.Primary else DsColors.TextTertiary
                        )
                    }
                    Button(
                        onClick        = { currentStep = 2 },
                        enabled        = cartItems.isNotEmpty(),
                        shape          = DsShapes.medium,
                        colors         = ButtonDefaults.buttonColors(containerColor = DsColors.Primary),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Text("Suivant →", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold)
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
                        OutlinedTextField(
                            value = note, onValueChange = { note = it },
                            placeholder = { Text("Note (optionnel)", fontSize = DsTextSize.body) },
                            modifier = Modifier.fillMaxWidth(), shape = DsShapes.medium, minLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary)
                        )
                    }

                    item {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.PrimaryLight).padding(DsSpacing.lg)
                        ) {
                            Text("Résumé du retour", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Primary)
                            Spacer(Modifier.height(DsSpacing.sm))
                            RetourSummaryRow("Produits", "${cartItems.size}")
                            RetourSummaryRow("Cartons", formatRetourQty(totalQuantity))
                            RetourSummaryRow("Valeur totale (DA)", "${"%,.2f".format(totalValue)} DA", highlight = true)
                        }
                    }

                    if (saveError.isNotEmpty()) {
                        item { Text(saveError, color = DsColors.Danger, fontSize = DsTextSize.bodySmall) }
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
    onAdd            : () -> Unit,
    onQuantityChange : (Double) -> Unit
) {
    val isInCart = quantity != null
    Column(
        modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface)
            .border(1.dp, if (isInCart) DsColors.Primary else DsColors.Border, DsShapes.large)
            .padding(DsSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(if (isInCart) DsColors.PrimaryLight else DsColors.SurfaceMuted),
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
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = if (isInCart) DsColors.Primary else DsColors.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary, maxLines = 1)
                Text(product.category_name ?: "—", fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1)
                Text("Stock : ${formatRetourQty(product.stock)} ${product.unit_type}", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
            }
            Spacer(Modifier.width(DsSpacing.sm))
            if (!isInCart) {
                IconButton(onClick = onAdd, modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight)) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter", tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (isInCart) {
            Spacer(Modifier.height(DsSpacing.sm))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("à retourner", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick  = { onQuantityChange((quantity ?: 1.0) - 1) },
                        modifier = Modifier.size(32.dp).clip(DsShapes.pill).background(DsColors.SurfaceMuted)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(15.dp))
                    }
                    Text(
                        formatRetourQty(quantity ?: 0.0),
                        fontSize   = DsTextSize.body,
                        fontWeight = FontWeight.Bold,
                        color      = DsColors.Primary,
                        modifier   = Modifier.widthIn(min = 28.dp),
                        textAlign  = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(
                        onClick  = { onQuantityChange((quantity ?: 0.0) + 1) },
                        modifier = Modifier.size(32.dp).clip(DsShapes.pill).background(DsColors.SurfaceMuted)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(15.dp))
                    }
                }
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
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.Primary)
        Text(
            value,
            fontSize   = if (highlight) DsTextSize.headline else DsTextSize.bodySmall,
            fontWeight = FontWeight.ExtraBold,
            color      = DsColors.Primary
        )
    }
}