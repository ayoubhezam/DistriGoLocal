package com.distrigo.app.ui.products

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.scanner.BarcodeScannerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    product   : Product? = null,
    onBack    : () -> Unit,
    onSaved   : (Int) -> Unit,
    viewModel : ProductViewModel = viewModel()
) {
    val isEdit = product != null

    val context  = LocalContext.current
    val products by viewModel.products.collectAsState()

    var name          by remember { mutableStateOf(product?.name ?: "") }
    var barcode       by remember { mutableStateOf(product?.barcode ?: "") }
    var sellingPrice  by remember { mutableStateOf(if (isEdit) product!!.selling_price.toString() else "") }
    var purchasePrice by remember { mutableStateOf(if (isEdit) product!!.purchase_price.toString() else "") }
    var packages      by remember { mutableStateOf(if (isEdit) product!!.packages.toString() else "") }
    var packSize      by remember { mutableStateOf(if (isEdit) product!!.pack_size.toString() else "") }
    var minStock      by remember { mutableStateOf(if (isEdit) product!!.min_stock.toString() else "10") }
    var unitType      by remember { mutableStateOf(if (isEdit) product!!.unit_type else "pièce") }
    var hasExpiry     by remember { mutableStateOf(if (isEdit) product!!.has_expiry == 1 else false) }
    var expiryDate    by remember { mutableStateOf(if (isEdit) product?.expiry_date ?: "" else "") }
    var imageBase64   by remember { mutableStateOf<String?>(if (isEdit) product!!.image_uri else null) }
    var imageRotation by remember { mutableStateOf(0f) }

    val categories            by viewModel.categories.collectAsState()
    var selectedCategoryId    by remember { mutableStateOf<Int?>(if (isEdit) product!!.category_id else null) }
    var categoryExpanded      by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName       by remember { mutableStateOf("") }

    val suppliers             by viewModel.suppliers.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadSuppliers() }
    var selectedSupplierId by remember { mutableStateOf<Int?>(if (isEdit) product?.supplier_id else null) }
    var supplierExpanded      by remember { mutableStateOf(false) }
    var showAddSupplierDialog by remember { mutableStateOf(false) }
    var showAddProductDialog by remember { mutableStateOf(false) }
    var newProductName        by remember { mutableStateOf("") }
    var newProductUnitType    by remember { mutableStateOf("pièce") }
    var newProductPurchasePrice by remember { mutableStateOf("") }
    var newProductSellingPrice  by remember { mutableStateOf("") }
    var newSupplierName       by remember { mutableStateOf("") }
    var newSupplierPhone      by remember { mutableStateOf("") }

    var nameError          by remember { mutableStateOf("") }
    var barcodeError       by remember { mutableStateOf("") }
    var sellingPriceError  by remember { mutableStateOf("") }
    var purchasePriceError by remember { mutableStateOf("") }
    var showMarginWarn     by remember { mutableStateOf(false) }
    var isSaving           by remember { mutableStateOf(false) }
    val datePickerState    = rememberDatePickerState()
    var showDatePicker     by remember { mutableStateOf(false) }
    var showScanner        by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val stream         = context.contentResolver.openInputStream(it)
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(stream)
            stream?.close()
            val maxSize  = 400
            val width    = originalBitmap.width
            val height   = originalBitmap.height
            val ratio    = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
            val resized  = android.graphics.Bitmap.createScaledBitmap(
                originalBitmap, (width * ratio).toInt(), (height * ratio).toInt(), true
            )
            val outputStream = java.io.ByteArrayOutputStream()
            resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, outputStream)
            imageBase64 = "data:image/jpeg;base64," +
                    android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
        }
    }

    val computedStock = if (unitType == "pièce")
        (packages.toIntOrNull() ?: 0) * (packSize.toIntOrNull() ?: 0)
    else
        packages.toIntOrNull() ?: 0

    fun validate(): Boolean {
        var valid = true
        nameError = ""; barcodeError = ""; sellingPriceError = ""; purchasePriceError = ""
        if (name.isBlank()) {
            nameError = "Le nom est obligatoire."
            valid = false
        } else {
            // تحقق من التكرار مع استثناء المنتج الحالي عند التعديل
            val duplicate = products.find {
                it.name.trim().lowercase() == name.trim().lowercase() &&
                        it.id != (product?.id ?: -1)
            }
            if (duplicate != null) { nameError = "Ce nom de produit est déjà enregistré."; valid = false }
        }

        if (barcode.isBlank()) {
            barcodeError = "Le code-barres est obligatoire."
            valid = false
        } else {
            val dupBarcode = products.find {
                it.barcode?.trim() == barcode.trim() &&
                        it.id != (product?.id ?: -1)
            }
            if (dupBarcode != null) { barcodeError = "Ce code-barres est déjà enregistré."; valid = false }
        }
        if (sellingPrice.toDoubleOrNull() == null || sellingPrice.toDouble() <= 0)
            { sellingPriceError = "Obligatoire."; valid = false }
        if (purchasePrice.toDoubleOrNull() == null || purchasePrice.toDouble() <= 0)
            { purchasePriceError = "Obligatoire."; valid = false }
        return valid
    }

    fun save(forceSubmit: Boolean = false) {
        if (!validate()) return
        val sp = sellingPrice.toDouble()
        val pp = purchasePrice.toDouble()
        if (pp >= sp && !forceSubmit) { showMarginWarn = true; return }
        isSaving = true
        val data = if (isEdit) {
            mapOf(
                "name"           to name.trim(),
                "barcode"        to barcode.trim().ifEmpty { null },
                "selling_price"  to sp,
                "purchase_price" to pp,
                "min_stock"      to (minStock.toIntOrNull() ?: 10),
                "packages"       to 0,
                "pack_size"      to 0,
                "unit_type"      to unitType,
                "has_expiry"     to if (hasExpiry) 1 else 0,
                "expiry_date"    to if (hasExpiry) expiryDate else null,
                "image_uri"      to imageBase64,
                "category_id"    to selectedCategoryId
            )
        } else {
            mapOf(
                "name"           to name.trim(),
                "barcode"        to barcode.trim().ifEmpty { null },
                "selling_price"  to sp,
                "purchase_price" to pp,
                "stock"          to 0,
                "min_stock"      to (minStock.toIntOrNull() ?: 10),
                "packages"       to 0,
                "pack_size"      to 0,
                "unit_type"      to unitType,
                "has_expiry"     to if (hasExpiry) 1 else 0,
                "expiry_date"    to if (hasExpiry) expiryDate else null,
                "image_uri"      to imageBase64,
                "category_id"    to selectedCategoryId
            )
        }
        if (isEdit) {
            viewModel.updateProduct(
                id        = product!!.id,
                product   = data,
                onSuccess = {
                    val originalSupplierId = product?.supplier_id
                    when {
                        selectedSupplierId != null && selectedSupplierId != originalSupplierId -> {
                            viewModel.unlinkProductFromAllSuppliers(
                                productId = product.id,
                                onSuccess = {
                                    viewModel.linkProductToSupplier(
                                        supplierId    = selectedSupplierId!!,
                                        productId     = product.id,
                                        purchasePrice = pp,
                                        onSuccess     = { onSaved(product.id) },
                                        onError       = { onSaved(product.id) }
                                    )
                                },
                                onError = { onSaved(product.id) }
                            )
                        }
                        selectedSupplierId == null && originalSupplierId != null -> {
                            viewModel.unlinkProductFromAllSuppliers(
                                productId = product.id,
                                onSuccess = { onSaved(product.id) },
                                onError   = { onSaved(product.id) }
                            )
                        }
                        else -> onSaved(product.id)
                    }
                },
                onError = { error ->
                    android.util.Log.e("DISTRIGO", "Update error: $error")
                    isSaving = false
                }
            )
        } else {
            viewModel.addProduct(
                product   = data,
                onSuccess = { result ->
                    val newProductId = (result["id"] as? Double)?.toInt() ?: 0
                    if (selectedSupplierId != null && newProductId > 0) {
                        viewModel.linkProductToSupplier(
                            supplierId    = selectedSupplierId!!,
                            productId     = newProductId,
                            purchasePrice = pp,
                            onSuccess     = { onSaved(newProductId) },
                            onError       = { onSaved(newProductId) }
                        )
                    } else {
                        onSaved(newProductId)
                    }
                },
                onError = { error ->
                    android.util.Log.e("DISTRIGO", "Add error: $error")
                    isSaving = false
                }
            )
        }
    }

    BackHandler { onBack() }

    if (showScanner) {
        BackHandler { showScanner = false }
        BarcodeScannerScreen(
            onBarcodeScanned = { code ->
                barcode = code
                barcodeError = ""
                showScanner = false
            },
            onClose = { showScanner = false }
        )
        return
    }

    // ── Date Picker Dialog ──
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        expiryDate = sdf.format(java.util.Date(millis))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annuler") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Margin Warning Dialog ──
    if (showMarginWarn) {
        AlertDialog(
            onDismissRequest = { showMarginWarn = false },
            title = { Text("Prix d'achat ≥ Prix de vente") },
            text  = {
                Column {
                    Text("Prix de vente : $sellingPrice DA")
                    Text("Prix d'achat  : $purchasePrice DA", color = DestructiveRed)
                    Spacer(Modifier.height(8.dp))
                    Text("Voulez-vous continuer malgré la perte ?", fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showMarginWarn = false; save(forceSubmit = true) }) {
                    Text("Continuer", color = DestructiveRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarginWarn = false }) { Text("Corriger") }
            }
        )
    }

    // ── Add Supplier Dialog ──
    if (showAddSupplierDialog) {
        AlertDialog(
            onDismissRequest = { showAddSupplierDialog = false; newSupplierName = ""; newSupplierPhone = "" },
            title = { Text("Nouveau fournisseur") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = newSupplierName,
                        onValueChange = { newSupplierName = it },
                        placeholder   = { Text("Nom du fournisseur") },
                        singleLine    = true,
                        shape         = RoundedCornerShape(12.dp),
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = PrimaryBlue,
                            unfocusedBorderColor = BorderGray
                        )
                    )
                    OutlinedTextField(
                        value         = newSupplierPhone,
                        onValueChange = { newSupplierPhone = it },
                        placeholder   = { Text("Téléphone") },
                        singleLine    = true,
                        shape         = RoundedCornerShape(12.dp),
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = PrimaryBlue,
                            unfocusedBorderColor = BorderGray
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newSupplierName.isNotBlank() && newSupplierPhone.isNotBlank()) {
                        viewModel.addSupplierAndRefresh(
                            name      = newSupplierName.trim(),
                            phone     = newSupplierPhone.trim(),
                            onSuccess = { id ->
                                selectedSupplierId    = id
                                showAddSupplierDialog = false
                                newSupplierName       = ""
                                newSupplierPhone      = ""
                            }
                        )
                    }
                }) { Text("Ajouter", color = PrimaryBlue, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddSupplierDialog = false
                    newSupplierName  = ""
                    newSupplierPhone = ""
                }) { Text("Annuler") }
            }
        )
    }


    // ── Add Category Dialog ──
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false; newCategoryName = "" },
            title = { Text("Nouvelle catégorie") },
            text  = {
                OutlinedTextField(
                    value         = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    placeholder   = { Text("Ex: Boissons") },
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = PrimaryBlue,
                        unfocusedBorderColor = BorderGray
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        viewModel.addCategoryAndRefresh(
                            name      = newCategoryName.trim(),
                            onSuccess = { id: Int ->
                                selectedCategoryId    = id
                                showAddCategoryDialog = false
                                newCategoryName       = ""
                            }
                        )
                    }
                }) { Text("Ajouter", color = PrimaryBlue, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false; newCategoryName = "" }) {
                    Text("Annuler")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── Header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Spacer(Modifier.width(4.dp))
            Text(
                if (isEdit) "Modifier le produit" else "Nouveau produit",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Photo ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MutedGray)
                .clickable { imagePicker.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (imageBase64 != null) {
                val imageBytes = Base64.decode(imageBase64!!.substringAfter("base64,"), Base64.NO_WRAP)
                val bitmap     = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                bitmap?.let {
                    Image(
                        bitmap             = it.asImageBitmap(),
                        contentDescription = null,
                        modifier           = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = imageRotation },
                        contentScale       = ContentScale.Crop
                    )
                }
                Row(
                    modifier              = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { imageRotation = (imageRotation + 90f) % 360f }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { imagePicker.launch("image/*") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("Changer", fontSize = 11.sp, color = Color.White) }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Ajouter une photo", fontSize = 12.sp, color = TextMuted)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Nom ──
        FormField(
            label         = "Nom du produit *",
            value         = name,
            onValueChange = { name = it; nameError = "" },
            error         = nameError,
            placeholder   = "Ex: Coca-Cola 1.5L"
        )
        Spacer(Modifier.height(12.dp))

        // ── Code-barres ──
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                FormField(
                    label         = "Code-barres *",
                    value         = barcode,
                    onValueChange = { barcode = it; barcodeError = "" },
                    error         = barcodeError,
                    placeholder   = "EAN-13"
                )
            }
            Button(
                onClick  = { showScanner = true },
                modifier = Modifier.height(56.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = BlueLight, contentColor = PrimaryBlue)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner un code-barres", modifier = Modifier.size(18.dp))
            }
            Button(
                onClick  = {
                    // نبحث عن أعلى ID في قائمة المنتجات، وإذا كانت فارغة نعتبره 0
                    val maxId = products.maxOfOrNull { it.id } ?: 0
                    val nextId = (maxId + 1).toLong()
                    barcode = nextId.toString().padStart(13, '0')
                },
                modifier = Modifier.height(56.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = BlueLight, contentColor = PrimaryBlue)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Fournisseur ──
        Text("Fournisseur", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded         = supplierExpanded,
                onExpandedChange = { supplierExpanded = it },
                modifier         = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value         = suppliers.find { it.id == selectedSupplierId }?.name ?: "Sans fournisseur",
                    onValueChange = {},
                    readOnly      = true,
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supplierExpanded) },
                    modifier      = Modifier.fillMaxWidth().menuAnchor(),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = BorderGray,
                        focusedBorderColor   = PrimaryBlue
                    )
                )
                ExposedDropdownMenu(
                    expanded         = supplierExpanded,
                    onDismissRequest = { supplierExpanded = false }
                ) {
                    DropdownMenuItem(
                        text    = { Text("Sans fournisseur", color = TextMuted) },
                        onClick = { selectedSupplierId = null; supplierExpanded = false }
                    )
                    suppliers.forEach { supplier ->
                        DropdownMenuItem(
                            text    = { Text(supplier.name) },
                            onClick = { selectedSupplierId = supplier.id; supplierExpanded = false }
                        )
                    }
                }
            }
            Button(
                onClick  = { showAddSupplierDialog = true },
                modifier = Modifier.height(56.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = BlueLight, contentColor = PrimaryBlue)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Catégorie ──
        Text("Catégorie", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded         = categoryExpanded,
                onExpandedChange = { categoryExpanded = it },
                modifier         = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value         = categories.find { it.id == selectedCategoryId }?.name ?: "Sans catégorie",
                    onValueChange = {},
                    readOnly      = true,
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier      = Modifier.fillMaxWidth().menuAnchor(),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = BorderGray,
                        focusedBorderColor   = PrimaryBlue
                    )
                )
                ExposedDropdownMenu(
                    expanded         = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DropdownMenuItem(
                        text    = { Text("Sans catégorie", color = TextMuted) },
                        onClick = { selectedCategoryId = null; categoryExpanded = false }
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text    = { Text(category.name) },
                            onClick = { selectedCategoryId = category.id; categoryExpanded = false }
                        )
                    }
                }
            }
            Button(
                onClick  = { showAddCategoryDialog = true },
                modifier = Modifier.height(56.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = BlueLight, contentColor = PrimaryBlue)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Prix ──
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                FormField(
                    label         = "Prix de vente *",
                    value         = sellingPrice,
                    onValueChange = { sellingPrice = it; sellingPriceError = "" },
                    error         = sellingPriceError,
                    placeholder   = "0.00",
                    isNumber      = true
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                FormField(
                    label         = "Prix d'achat *",
                    value         = purchasePrice,
                    onValueChange = { purchasePrice = it; purchasePriceError = "" },
                    error         = purchasePriceError,
                    placeholder   = "0.00",
                    isNumber      = true
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Stock minimum ──
        FormField(
            label         = "Stock minimum",
            value         = minStock,
            onValueChange = { minStock = it },
            placeholder   = "10",
            isNumber      = true,
            imeAction     = ImeAction.Done
        )
        Spacer(Modifier.height(16.dp))

        // ── Stock & conditionnement ──
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = MutedGray),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Stock & conditionnement", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                Text("Unité de stockage", fontSize = 12.sp, color = TextMuted)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("pièce", "carton").forEach { unit ->
                        val active = unitType == unit
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) PrimaryBlue else Color.White)
                                .border(1.dp, if (active) PrimaryBlue else BorderGray, RoundedCornerShape(12.dp))
                                .clickable { unitType = unit }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (unit == "pièce") "Pièce" else "Carton",
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color      = if (active) Color.White else TextPrimary
                            )
                        }
                    }
                }
                if ((packages.toIntOrNull() ?: 0) > 0 && (packSize.toIntOrNull() ?: 0) > 0) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BlueLight)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                if (unitType == "pièce") "Unités totales" else "Colis en stock",
                                fontSize = 12.sp, color = PrimaryBlue
                            )
                            Text(
                                "$computedStock ${if (unitType == "pièce") "pièces" else "cartons"}",
                                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryBlue
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Date d'expiration ──
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = MutedGray),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Date d'expiration", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        Text("Produit soumis à une date de péremption", fontSize = 11.sp, color = TextMuted)
                    }
                    Switch(
                        checked         = hasExpiry,
                        onCheckedChange = { hasExpiry = it },
                        colors          = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue)
                    )
                }
                if (hasExpiry) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick  = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (expiryDate.isEmpty()) "Choisir une date" else expiryDate, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Save ──
        Button(
            onClick  = { save() },
            enabled  = !isSaving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            else Text(
                if (isEdit) "Enregistrer les modifications" else "Ajouter le produit",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun FormField(
    label         : String,
    value         : String,
    onValueChange : (String) -> Unit,
    error         : String = "",
    placeholder   : String = "",
    isNumber      : Boolean = false,
    imeAction     : ImeAction = ImeAction.Next,
    onNext        : (() -> Unit)? = null,
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    Column {
        Text(label, fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value           = value,
            onValueChange   = onValueChange,
            placeholder     = { Text(placeholder, fontSize = 14.sp) },
            singleLine      = true,
            isError         = error.isNotEmpty(),
            modifier        = Modifier.fillMaxWidth(),
            shape           = RoundedCornerShape(12.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (isNumber) androidx.compose.ui.text.input.KeyboardType.Number
                               else          androidx.compose.ui.text.input.KeyboardType.Text,
                imeAction    = imeAction
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onNext = { onNext?.invoke() ?: focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                onDone = { focusManager.clearFocus() }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderGray,
                focusedBorderColor   = PrimaryBlue,
                errorBorderColor     = DestructiveRed
            )
        )
        if (error.isNotEmpty()) {
            Text(error, fontSize = 11.sp, color = DestructiveRed, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }
    }
}
