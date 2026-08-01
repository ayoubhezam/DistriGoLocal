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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.scanner.BarcodeScannerScreen
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.shadow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    val coroutineScope = rememberCoroutineScope()

    var name          by remember { mutableStateOf(product?.name ?: "") }
    var barcode       by remember { mutableStateOf(product?.barcode ?: "") }
    var sellingPrice  by remember { mutableStateOf(if (isEdit) product!!.selling_price.toString() else "") }
    var purchasePrice by remember { mutableStateOf(if (isEdit) product!!.purchase_price.toString() else "") }
    var packages      by remember { mutableStateOf(if (isEdit) product!!.packages.toString() else "") }
    var packSize      by remember { mutableStateOf(if (isEdit) product!!.pack_size.toString() else "") }
    var minStock      by remember { mutableStateOf(if (isEdit) product!!.min_stock.toString() else "0") }
    var unitType      by remember { mutableStateOf(if (isEdit) product!!.unit_type else "carton") }
    var hasExpiry     by remember { mutableStateOf(if (isEdit) product!!.has_expiry == 1 else false) }
    var expiryDate    by remember { mutableStateOf(if (isEdit) product?.expiry_date ?: "" else "") }
    var imageBase64   by remember { mutableStateOf<String?>(if (isEdit) product!!.image_uri else null) }
    var imageRotation by remember { mutableStateOf(0f) }

    val categories            by viewModel.categories.collectAsState()
    var selectedCategoryId    by remember { mutableStateOf<Int?>(if (isEdit) product!!.category_id else null) }
    var categoryExpanded      by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName       by remember { mutableStateOf("") }

    val sousCategories              by viewModel.sousCategories.collectAsState()
    var selectedSousCategorieId     by remember { mutableStateOf<Int?>(if (isEdit) product?.sous_categorie_id else null) }
    var sousCategorieExpanded       by remember { mutableStateOf(false) }
    var showAddSousCategorieDialog  by remember { mutableStateOf(false) }
    var newSousCategorieName        by remember { mutableStateOf("") }
    var isInitialCategoryEffect     by remember { mutableStateOf(true) }

    LaunchedEffect(selectedCategoryId) {
        if (isInitialCategoryEffect) {
            isInitialCategoryEffect = false
        } else {
            selectedSousCategorieId = null
        }
    }

    val marques             by viewModel.marques.collectAsState()
    var selectedMarqueId    by remember { mutableStateOf<Int?>(if (isEdit) product?.marque_id else null) }
    var marqueExpanded      by remember { mutableStateOf(false) }
    var showAddMarqueDialog by remember { mutableStateOf(false) }
    var newMarqueName       by remember { mutableStateOf("") }

    val suppliers             by viewModel.suppliers.collectAsState()
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

    val pagerState = rememberPagerState(pageCount = { 2 })
    val tabItems = listOf(
        Triple("Essentiel", Icons.Default.Inventory2, 0),
        Triple("Options", Icons.Default.AutoAwesome, 1)
    )

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
        if (!validate()) {
            if (pagerState.currentPage != 0) {
                coroutineScope.launch { pagerState.animateScrollToPage(0) }
            }
            return
        }
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
                "category_id"    to selectedCategoryId,
                "sous_categorie_id" to selectedSousCategorieId,
                "marque_id"      to selectedMarqueId
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
                "category_id"    to selectedCategoryId,
                "sous_categorie_id" to selectedSousCategorieId,
                "marque_id"      to selectedMarqueId
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
                    Text("Prix d'achat  : $purchasePrice DA", color = DsColors.Danger)
                    Spacer(Modifier.height(8.dp))
                    Text("Voulez-vous continuer malgré la perte ?", fontSize = DsTextSize.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showMarginWarn = false; save(forceSubmit = true) }) {
                    Text("Continuer", color = DsColors.Danger)
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
                        placeholder   = { Text("Téléphone") },
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
                        viewModel.addSupplierAndRefresh(
                            name      = newSupplierName.trim(),
                            phone     = newSupplierPhone.trim().ifEmpty { null },
                            onSuccess = { id ->
                                selectedSupplierId    = id
                                showAddSupplierDialog = false
                                newSupplierName       = ""
                                newSupplierPhone      = ""
                            }
                        )
                    }
                }) { Text("Ajouter", color = DsColors.Primary, fontWeight = FontWeight.SemiBold) }
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
                    shape         = DsShapes.medium,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = DsColors.Primary,
                        unfocusedBorderColor = DsColors.Border
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
                }) { Text("Ajouter", color = DsColors.Primary, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false; newCategoryName = "" }) {
                    Text("Annuler")
                }
            }
        )
    }

    // ── Add Sous-catégorie Dialog ──
    if (showAddSousCategorieDialog && selectedCategoryId != null) {
        AlertDialog(
            onDismissRequest = { showAddSousCategorieDialog = false; newSousCategorieName = "" },
            title = { Text("Nouvelle sous-catégorie") },
            text  = {
                OutlinedTextField(
                    value         = newSousCategorieName,
                    onValueChange = { newSousCategorieName = it },
                    placeholder   = { Text("Ex: Sodas") },
                    singleLine    = true,
                    shape         = DsShapes.medium,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = DsColors.Primary,
                        unfocusedBorderColor = DsColors.Border
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newSousCategorieName.isNotBlank()) {
                        viewModel.addSousCategorieAndRefresh(
                            name       = newSousCategorieName.trim(),
                            categoryId = selectedCategoryId!!,
                            onSuccess  = { id: Int ->
                                selectedSousCategorieId    = id
                                showAddSousCategorieDialog = false
                                newSousCategorieName       = ""
                            }
                        )
                    }
                }) { Text("Ajouter", color = DsColors.Primary, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showAddSousCategorieDialog = false; newSousCategorieName = "" }) {
                    Text("Annuler")
                }
            }
        )
    }

    // ── Add Marque Dialog ──
    if (showAddMarqueDialog) {
        AlertDialog(
            onDismissRequest = { showAddMarqueDialog = false; newMarqueName = "" },
            title = { Text("Nouvelle marque") },
            text  = {
                OutlinedTextField(
                    value         = newMarqueName,
                    onValueChange = { newMarqueName = it },
                    placeholder   = { Text("Ex: Danone") },
                    singleLine    = true,
                    shape         = DsShapes.medium,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = DsColors.Primary,
                        unfocusedBorderColor = DsColors.Border
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newMarqueName.isNotBlank()) {
                        viewModel.addMarqueAndRefresh(
                            name      = newMarqueName.trim(),
                            onSuccess = { id: Int ->
                                selectedMarqueId    = id
                                showAddMarqueDialog = false
                                newMarqueName        = ""
                            }
                        )
                    }
                }) { Text("Ajouter", color = DsColors.Primary, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showAddMarqueDialog = false; newMarqueName = "" }) {
                    Text("Annuler")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        // ── Header ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    if (isEdit) "Modifier le produit" else "Nouveau produit",
                    fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary
                )
                Text(
                    if (isEdit) "Modifiez les informations du produit" else "Créez un nouveau produit facilement",
                    fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary
                )
            }
        }

// ── Tabs (pill segmented control, elastic indicator) ──
        val density      = LocalDensity.current
        val tabOffsetsPx = remember { mutableStateListOf(0f, 0f) }
        val tabWidthsPx  = remember { mutableStateListOf(0f, 0f) }
        var tabHeightPx  by remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(DsShapes.large)
                .background(DsColors.SurfaceSunken)
                .padding(4.dp)
        ) {
            val pageFraction = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, (tabItems.size - 1).toFloat())

            // ── Floating elastic pill (behind the labels) ──
            if (tabWidthsPx[0] > 0f && tabWidthsPx[1] > 0f && tabHeightPx > 0f) {
                val leftIndex  = kotlin.math.floor(pageFraction).toInt().coerceIn(0, tabItems.size - 1)
                val rightIndex = kotlin.math.ceil(pageFraction).toInt().coerceIn(0, tabItems.size - 1)
                val localT     = pageFraction - leftIndex

                val indicatorLeftPx: Float
                val indicatorRightPx: Float
                if (leftIndex == rightIndex) {
                    indicatorLeftPx  = tabOffsetsPx[leftIndex]
                    indicatorRightPx = tabOffsetsPx[leftIndex] + tabWidthsPx[leftIndex]
                } else {
                    // حافة تتأخر ثم تلحق بسرعة (ease-in) وحافة تتقدم بسرعة ثم تتباطأ (ease-out) = تمدد مرن
                    val easeIn  = localT * localT
                    val easeOut = 1f - (1f - localT) * (1f - localT)
                    indicatorLeftPx  = androidx.compose.ui.util.lerp(tabOffsetsPx[leftIndex], tabOffsetsPx[rightIndex], easeIn)
                    indicatorRightPx = androidx.compose.ui.util.lerp(
                        tabOffsetsPx[leftIndex] + tabWidthsPx[leftIndex],
                        tabOffsetsPx[rightIndex] + tabWidthsPx[rightIndex],
                        easeOut
                    )
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(indicatorLeftPx.roundToInt(), 0) }
                        .width(with(density) { (indicatorRightPx - indicatorLeftPx).toDp() })
                        .height(with(density) { tabHeightPx.toDp() })
                        .shadow(2.dp, DsShapes.large)
                        .clip(DsShapes.large)
                        .background(DsColors.Surface)
                )
            }

            // ── Labels (on top، ألوان متدرّجة بسلاسة بدل القفز) ──
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tabItems.forEach { (title, icon, index) ->
                    val proximity = (1f - kotlin.math.abs(pageFraction - index)).coerceIn(0f, 1f)
                    val tint      = androidx.compose.ui.graphics.lerp(DsColors.TextSecondary, DsColors.Primary, proximity)

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned {
                                tabOffsetsPx[index] = it.positionInParent().x
                                tabWidthsPx[index]  = it.size.width.toFloat()
                                tabHeightPx         = it.size.height.toFloat()
                            }
                            .clickable { coroutineScope.launch { pagerState.animateScrollToPage(index) } }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            title,
                            fontSize   = DsTextSize.bodySmall,
                            color      = tint,
                            fontWeight = if (proximity > 0.5f) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // ── Pages ──
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val absOffset  = pageOffset.coerceIn(-1f, 1f).let { if (it < 0f) -it else it }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        shadowElevation = lerp(0.dp, 16.dp, absOffset).toPx()
                        shape = RectangleShape
                        clip  = false
                        alpha = lerp(1f, 0.97f, absOffset)
                    }
                    .background(DsColors.Surface)
            ) {
            when (page) {
                0 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
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
                            onClick        = { showScanner = true },
                            modifier       = Modifier.height(56.dp),
                            shape          = DsShapes.medium,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors         = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryLight, contentColor = DsColors.Primary)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner un code-barres", modifier = Modifier.size(18.dp))
                                Text("Scanner", fontSize = DsTextSize.caption, color = DsColors.Primary)
                            }
                        }
                        Button(
                            onClick        = {
                                // نبحث عن أعلى ID في قائمة المنتجات، وإذا كانت فارغة نعتبره 0
                                val maxId = products.maxOfOrNull { it.id } ?: 0
                                val nextId = (maxId + 1).toLong()
                                barcode = nextId.toString().padStart(13, '0')
                            },
                            modifier       = Modifier.height(56.dp),
                            shape          = DsShapes.medium,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors         = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryLight, contentColor = DsColors.Primary)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Refresh, contentDescription = "Générer un code-barres", modifier = Modifier.size(18.dp))
                                Text("Générer", fontSize = DsTextSize.caption, color = DsColors.Primary)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Fournisseur ──
                    Text("Fournisseur", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
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
                                shape         = DsShapes.medium,
                                colors        = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = DsColors.Border,
                                    focusedBorderColor   = DsColors.Primary
                                )
                            )
                            ExposedDropdownMenu(
                                expanded         = supplierExpanded,
                                onDismissRequest = { supplierExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text    = { Text("Sans fournisseur", color = DsColors.TextSecondary) },
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
                            shape    = DsShapes.medium,
                            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryLight, contentColor = DsColors.Primary)
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
                                isNumber      = true,
                                trailingText  = "DA"
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            FormField(
                                label         = "Prix d'achat *",
                                value         = purchasePrice,
                                onValueChange = { purchasePrice = it; purchasePriceError = "" },
                                error         = purchasePriceError,
                                placeholder   = "0.00",
                                isNumber      = true,
                                trailingText  = "DA"
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Stock minimum ──
                    FormField(
                        label         = "Stock minimum",
                        value         = minStock,
                        onValueChange = { minStock = it },
                        placeholder   = "0",
                        isNumber      = true,
                        imeAction     = ImeAction.Done,
                    )
                    Spacer(Modifier.height(16.dp))

                    // ── Stock & conditionnement ──
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = DsShapes.large,
                        colors    = CardDefaults.cardColors(containerColor = DsColors.SurfaceSunken),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Stock & conditionnement", fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary)
                            Spacer(Modifier.height(12.dp))
                            Text("Unité de stockage", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("carton", "pièce").forEach { unit ->
                                    val active = unitType == unit
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(DsShapes.medium)
                                            .background(if (active) DsColors.Primary else DsColors.Surface)
                                            .border(1.dp, if (active) DsColors.Primary else DsColors.Border, DsShapes.medium)
                                            .clickable { unitType = unit }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Inventory2,
                                                contentDescription = null,
                                                tint     = if (active) Color.White else DsColors.TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                if (unit == "pièce") "Pièce" else "Carton",
                                                fontSize   = DsTextSize.body,
                                                fontWeight = FontWeight.Medium,
                                                color      = if (active) Color.White else DsColors.TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                            if ((packages.toIntOrNull() ?: 0) > 0 && (packSize.toIntOrNull() ?: 0) > 0) {
                                Spacer(Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(DsShapes.medium)
                                        .background(DsColors.PrimaryLight)
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier              = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (unitType == "pièce") "Unités totales" else "Colis en stock",
                                            fontSize = DsTextSize.caption, color = DsColors.Primary
                                        )
                                        Text(
                                            "$computedStock ${if (unitType == "pièce") "pièces" else "cartons"}",
                                            fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.Primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                1 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // ── Photo ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(DsShapes.large)
                            .background(DsColors.SurfaceSunken)
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
                                        .clip(DsShapes.small)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .clickable { imageRotation = (imageRotation + 90f) % 360f }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.RotateRight, contentDescription = null,
                                        tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(DsShapes.small)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .clickable { imagePicker.launch("image/*") }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) { Text("Changer", fontSize = DsTextSize.caption, color = Color.White) }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(6.dp))
                                Text("Ajouter une photo", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Catégorie ──
                    Text("Catégorie", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
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
                                shape         = DsShapes.medium,
                                colors        = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = DsColors.Border,
                                    focusedBorderColor   = DsColors.Primary
                                )
                            )
                            ExposedDropdownMenu(
                                expanded         = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text    = { Text("Sans catégorie", color = DsColors.TextSecondary) },
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
                            shape    = DsShapes.medium,
                            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryLight, contentColor = DsColors.Primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Sous-catégorie ──
                    val sousCategorieEnabled = selectedCategoryId != null
                    Text(
                        "Sous-catégorie",
                        fontSize = DsTextSize.bodySmall,
                        color    = if (sousCategorieEnabled) DsColors.TextSecondary else DsColors.TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(
                            expanded         = sousCategorieEnabled && sousCategorieExpanded,
                            onExpandedChange = { if (sousCategorieEnabled) sousCategorieExpanded = it },
                            modifier         = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value         = sousCategories.find { it.id == selectedSousCategorieId }?.name ?: "Sans sous-catégorie",
                                onValueChange = {},
                                readOnly      = true,
                                enabled       = sousCategorieEnabled,
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sousCategorieExpanded) },
                                modifier      = Modifier.fillMaxWidth().menuAnchor(),
                                shape         = DsShapes.medium,
                                colors        = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = DsColors.Border,
                                    focusedBorderColor   = DsColors.Primary,
                                    disabledBorderColor  = DsColors.Border.copy(alpha = 0.5f),
                                    disabledTextColor    = DsColors.TextSecondary.copy(alpha = 0.5f)
                                )
                            )
                            ExposedDropdownMenu(
                                expanded         = sousCategorieExpanded,
                                onDismissRequest = { sousCategorieExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text    = { Text("Sans sous-catégorie", color = DsColors.TextSecondary) },
                                    onClick = { selectedSousCategorieId = null; sousCategorieExpanded = false }
                                )
                                sousCategories.filter { it.category_id == selectedCategoryId }.forEach { sousCategorie ->
                                    DropdownMenuItem(
                                        text    = { Text(sousCategorie.name) },
                                        onClick = { selectedSousCategorieId = sousCategorie.id; sousCategorieExpanded = false }
                                    )
                                }
                            }
                        }
                        Button(
                            onClick  = { showAddSousCategorieDialog = true },
                            enabled  = sousCategorieEnabled,
                            modifier = Modifier.height(56.dp),
                            shape    = DsShapes.medium,
                            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryLight, contentColor = DsColors.Primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Marque ──
                    Text("Marque", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(
                            expanded         = marqueExpanded,
                            onExpandedChange = { marqueExpanded = it },
                            modifier         = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value         = marques.find { it.id == selectedMarqueId }?.name ?: "Sans marque",
                                onValueChange = {},
                                readOnly      = true,
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = marqueExpanded) },
                                modifier      = Modifier.fillMaxWidth().menuAnchor(),
                                shape         = DsShapes.medium,
                                colors        = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = DsColors.Border,
                                    focusedBorderColor   = DsColors.Primary
                                )
                            )
                            ExposedDropdownMenu(
                                expanded         = marqueExpanded,
                                onDismissRequest = { marqueExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text    = { Text("Sans marque", color = DsColors.TextSecondary) },
                                    onClick = { selectedMarqueId = null; marqueExpanded = false }
                                )
                                marques.forEach { marque ->
                                    DropdownMenuItem(
                                        text    = { Text(marque.name) },
                                        onClick = { selectedMarqueId = marque.id; marqueExpanded = false }
                                    )
                                }
                            }
                        }
                        Button(
                            onClick  = { showAddMarqueDialog = true },
                            modifier = Modifier.height(56.dp),
                            shape    = DsShapes.medium,
                            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryLight, contentColor = DsColors.Primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Date d'expiration ──
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = DsShapes.large,
                        colors    = CardDefaults.cardColors(containerColor = DsColors.SurfaceSunken),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Date d'expiration", fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary)
                                    Text("Produit soumis à une date de péremption", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                }
                                Switch(
                                    checked         = hasExpiry,
                                    onCheckedChange = { hasExpiry = it },
                                    colors          = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DsColors.Primary)
                                )
                            }
                            if (hasExpiry) {
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick  = { showDatePicker = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape    = DsShapes.medium,
                                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = DsColors.Primary),
                                    border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (expiryDate.isEmpty()) "Choisir une date" else expiryDate, fontSize = DsTextSize.body)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
            }
        }

        // ── Save (persistent, visible on both tabs) ──
        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick  = { save() },
                enabled  = !isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = DsShapes.large,
                colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
            ) {
                if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isEdit) "Enregistrer les modifications" else "Ajouter le produit",
                        fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color.White
                    )
                }
            }
        }
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
    leadingIcon   : androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingText  : String? = null,
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    Column {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value           = value,
            onValueChange   = onValueChange,
            placeholder     = { Text(placeholder, fontSize = DsTextSize.body) },
            singleLine      = true,
            isError         = error.isNotEmpty(),
            modifier        = Modifier.fillMaxWidth(),
            shape           = DsShapes.medium,
            leadingIcon     = leadingIcon?.let { icon ->
                { Icon(icon, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp)) }
            },
            trailingIcon    = trailingText?.let { txt ->
                { Text(txt, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, fontWeight = FontWeight.Medium) }
            },
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
                unfocusedBorderColor = DsColors.Border,
                focusedBorderColor   = DsColors.Primary,
                errorBorderColor     = DsColors.Danger
            )
        )
        if (error.isNotEmpty()) {
            Text(error, fontSize = DsTextSize.caption, color = DsColors.Danger, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }
    }
}
