@file:OptIn(ExperimentalMaterial3Api::class)
package com.distrigo.app.ui.pertes

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.purchases.formatOrderDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import com.distrigo.app.data.model.Perte
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import com.distrigo.app.ui.scanner.BarcodeScannerScreen
@Composable
fun PerteFormScreen(
    typeId    : Int,
    perte     : Perte? = null,
    viewModel : PerteViewModel = viewModel(),
    onBack    : () -> Unit,
    onSaved   : () -> Unit
) {
    val context = LocalContext.current
    val perteTypes by viewModel.perteTypes.collectAsState()
    val products   by viewModel.products.collectAsState()
    val type = perteTypes.find { it.id == typeId }

    val isEdit = perte != null
    val initialDate = perte?.date_time?.let { java.time.Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }

    var currentStep     by remember { mutableStateOf(1) }
    var selectedProduct by remember { mutableStateOf<Product?>(products.find { it.id == perte?.product_id }) }
    var quantity         by remember { mutableStateOf(perte?.quantity ?: 0) }
    var source            by remember { mutableStateOf(perte?.source ?: "depot") }
    var selectedDate      by remember { mutableStateOf(initialDate ?: LocalDate.now()) }
    var motif              by remember { mutableStateOf(perte?.motif ?: "") }
    var photoBase64         by remember { mutableStateOf(perte?.photo_path) }
    var showProductPicker   by remember { mutableStateOf(false) }
    var showDatePicker       by remember { mutableStateOf(false) }
    var quantityError         by remember { mutableStateOf("") }
    var saveError               by remember { mutableStateOf("") }
    var isSaving                 by remember { mutableStateOf(false) }

    val isoDateTime = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            val original = BitmapFactory.decodeStream(stream)
            stream?.close()
            val maxSize = 400
            val ratio = minOf(maxSize.toFloat() / original.width, maxSize.toFloat() / original.height)
            val resized = android.graphics.Bitmap.createScaledBitmap(
                original, (original.width * ratio).toInt(), (original.height * ratio).toInt(), true
            )
            val out = java.io.ByteArrayOutputStream()
            resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, out)
            photoBase64 = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }

    fun save() {
        val product = selectedProduct
        if (product == null) { saveError = "Sélectionnez un produit"; currentStep = 1; return }
        if (quantity <= 0) { quantityError = "Quantité invalide"; currentStep = 1; return }
        isSaving = true

        if (isEdit) {
            viewModel.updatePerte(
                id = perte!!.id, typeId = typeId, productId = product.id, quantity = quantity, source = source,
                dateTime = isoDateTime, motif = motif.trim().ifEmpty { null }, photoPath = photoBase64,
                onSuccess = onSaved,
                onError   = { msg -> isSaving = false; saveError = msg; currentStep = 1 }
            )
        } else {
            viewModel.addPerte(
                typeId = typeId, productId = product.id, quantity = quantity, source = source,
                dateTime = isoDateTime, motif = motif.trim().ifEmpty { null }, photoPath = photoBase64,
                onSuccess = onSaved,
                onError   = { msg -> isSaving = false; saveError = msg; currentStep = 1 }
            )
        }
    }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (currentStep == 2) currentStep = 1 else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Text(if (isEdit) "Modifier la perte" else "Nouvelle perte", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)        }
        StepIndicator(currentStep)
        Spacer(Modifier.height(DsSpacing.md))

        if (currentStep == 1) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                FixedInfoField(label = "Type de perte", icon = type?.icon ?: "category", value = type?.name ?: "")

                // ── Source du stock ──
                Column {
                    Text("Source du stock", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.SurfaceSunken).padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("depot" to "Dépôt", "camion" to "Camion").forEach { (value, label) ->
                            val active = source == value
                            Box(
                                modifier = Modifier.weight(1f).clip(DsShapes.small)
                                    .background(if (active) DsColors.Primary else Color.Transparent)
                                    .clickable { source = value }
                                    .padding(vertical = DsSpacing.sm),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = if (active) Color.White else DsColors.TextSecondary)
                            }
                        }
                    }
                }

                // ── Produit ──
                Column {
                    Text("Produit *", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                    Surface(
                        shape = DsShapes.medium, color = DsColors.SurfaceSunken,
                        modifier = Modifier.fillMaxWidth().clickable { showProductPicker = true }
                    ) {
                        Row(
                            Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                selectedProduct?.name ?: "Sélectionner un produit",
                                fontSize = DsTextSize.body,
                                color = if (selectedProduct != null) DsColors.TextPrimary else DsColors.TextTertiary
                            )
                            Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(14.dp))                        }
                    }
                }

                // ── Quantité + Unité (ثابتة من المنتج) ──
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                    Column(Modifier.weight(1f)) {
                        Text("Quantité *", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.SurfaceSunken),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = { if (quantity > 0) { quantity--; quantityError = "" } }) {
                                Icon(Icons.Default.Remove, contentDescription = null, tint = DsColors.TextPrimary)
                            }
                            Text("$quantity", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                            IconButton(onClick = { quantity++; quantityError = "" }) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.TextPrimary)
                            }
                        }
                        if (quantityError.isNotEmpty()) {
                            Text(quantityError, fontSize = DsTextSize.caption, color = DsColors.Danger, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Unité", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                        Surface(shape = DsShapes.medium, color = DsColors.SurfaceSunken, modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.padding(DsSpacing.md), contentAlignment = Alignment.CenterStart) {
                                Text(selectedProduct?.unit_type ?: "—", fontSize = DsTextSize.body, color = DsColors.TextPrimary)
                            }
                        }
                    }
                }

                // ── Date ──
                Column {
                    Text("Date", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth(), shape = DsShapes.medium) {
                        Text(formatOrderDate(isoDateTime), color = DsColors.TextPrimary)
                    }
                }

                // ── Motif ──
                Column {
                    Text("Motif (optionnel)", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                    OutlinedTextField(
                        value = motif, onValueChange = { motif = it },
                        placeholder = { Text("Ex: Cartons abîmés, chute, casse...") },
                        modifier = Modifier.fillMaxWidth(), shape = DsShapes.medium, minLines = 2, maxLines = 3,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                }



                if (saveError.isNotEmpty()) {
                    Text(saveError, color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                }
                Spacer(Modifier.height(DsSpacing.sm))
            }

            Button(
                onClick = {
                    var valid = true
                    if (selectedProduct == null) { saveError = "Sélectionnez un produit"; valid = false } else saveError = ""
                    if (quantity <= 0) { quantityError = "Quantité invalide"; valid = false }
                    if (valid) currentStep = 2
                },
                modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
                shape = DsShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
            ) { Text("Suivant", color = Color.White, fontWeight = FontWeight.SemiBold) }
        } else {
            val valeurEstimee = (selectedProduct?.purchase_price ?: 0.0) * quantity
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                Text("Vérifiez les informations", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                SummaryRow("Type de perte", type?.name ?: "")
                SummaryRow("Produit", selectedProduct?.name ?: "")
                SummaryRow("Quantité", "$quantity ${selectedProduct?.unit_type ?: ""}")
                SummaryRow("Source", if (source == "camion") "Camion" else "Dépôt")
                SummaryRow("Valeur totale", "${"%,.0f".format(valeurEstimee)} DA", highlight = true)
                SummaryRow("Date", formatOrderDate(isoDateTime))
                SummaryRow("Motif", motif.ifBlank { "Aucun" })
                if (saveError.isNotEmpty()) {
                    Text(saveError, color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                OutlinedButton(onClick = { currentStep = 1 }, modifier = Modifier.weight(1f).height(52.dp), shape = DsShapes.medium) {
                    Text("Modifier")
                }
                Button(
                    onClick = { save() }, enabled = !isSaving,
                    modifier = Modifier.weight(1f).height(52.dp), shape = DsShapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    else Text("Enregistrer", color = Color.White, fontWeight = FontWeight.SemiBold)
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

    if (showProductPicker) {
        ProductPickerDialog(
            products   = products,
            onSelect   = { selectedProduct = it; showProductPicker = false },
            onDismiss  = { showProductPicker = false }
        )
    }
}

@Composable
private fun ProductPickerDialog(products: List<Product>, onSelect: (Product) -> Unit, onDismiss: () -> Unit) {
    var search      by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }

    // ── Recherche par tokens (ex: "br li 5L" → "Brilex Linge 5L") + code-barres ──
    val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    val filtered = products.filter { product ->
        tokens.isEmpty() || tokens.all { token ->
            product.name.contains(token, ignoreCase = true) ||
                    (product.barcode?.contains(token, ignoreCase = true) == true)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = DsColors.Surface) {

            // ── Scanner de code-barres (plein écran, remplace le contenu du Dialog) ──
            if (showScanner) {
                BarcodeScannerScreen(
                    onBarcodeScanned = { code ->
                        search = code
                        showScanner = false
                    },
                    onClose = { showScanner = false }
                )
                return@Surface
            }

            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fermer", tint = DsColors.TextPrimary)
                    }
                    Text("Sélectionner un produit", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                }
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("Rechercher par nom ou code-barres…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (search.isNotEmpty()) {
                                IconButton(onClick = { search = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Effacer", tint = DsColors.TextTertiary, modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(onClick = { showScanner = true }) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner un code-barres", tint = DsColors.Primary)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
                    shape = DsShapes.medium, singleLine = true
                )
                Spacer(Modifier.height(DsSpacing.sm))

                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Aucun produit trouvé", color = DsColors.TextSecondary)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                    ) {
                        items(filtered, key = { it.id }) { product ->
                            ProductPickerRow(product = product, onClick = { onSelect(product) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductPickerRow(product: Product, onClick: () -> Unit) {
    val isLow = product.stock < product.min_stock

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape    = DsShapes.medium,
        color    = DsColors.SurfaceMuted
    ) {
        Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            // ── صورة المنتج، بنفس نمط ProductCard تماماً ──
            Box(
                modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = remember(product.image_uri) {
                    product.image_uri?.let { uri ->
                        val bytes = Base64.decode(uri.substringAfter("base64,"), Base64.NO_WRAP)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.width(DsSpacing.md))

            Column(Modifier.weight(1f)) {
                Text(product.name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1)
                Text(
                    "${product.category_name ?: "—"} · ${product.supplier_name ?: "—"}",
                    fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1
                )
            }

            Spacer(Modifier.width(DsSpacing.sm))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${product.stock} ${product.unit_type}",
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = if (isLow) FontWeight.SemiBold else FontWeight.Medium,
                    color      = if (isLow) DsColors.Danger else DsColors.TextPrimary
                )
                Text(
                    "${product.camion_stock} en camion",
                    fontSize = DsTextSize.caption, color = DsColors.TextTertiary
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = DsSpacing.xxxl), verticalAlignment = Alignment.CenterVertically) {
        StepDot(1, "Détails", currentStep >= 1)
        Box(Modifier.weight(1f).height(2.dp).background(if (currentStep >= 2) DsColors.Primary else DsColors.Border))
        StepDot(2, "Résumé", currentStep >= 2)
    }
}

@Composable
private fun StepDot(number: Int, label: String, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(28.dp).clip(DsShapes.pill).background(if (active) DsColors.Primary else DsColors.Border), contentAlignment = Alignment.Center) {
            Text("$number", color = Color.White, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(DsSpacing.xs))
        Text(label, fontSize = DsTextSize.caption, color = if (active) DsColors.Primary else DsColors.TextTertiary)
    }
}

@Composable
private fun FixedInfoField(label: String, icon: String, value: String) {
    Column {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
        Surface(shape = DsShapes.medium, color = DsColors.SurfaceSunken, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(PerteIconMapper.iconFor(icon), contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(value, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = DsSpacing.sm), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        Text(value, fontSize = DsTextSize.bodySmall, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium, color = if (highlight) DsColors.Primary else DsColors.TextPrimary)
    }
    HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
}