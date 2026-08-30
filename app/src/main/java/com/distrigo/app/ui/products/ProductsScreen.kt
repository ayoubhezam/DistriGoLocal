package com.distrigo.app.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.distrigo.app.data.model.Category
import com.distrigo.app.ui.scanner.BarcodeScannerScreen
import androidx.compose.foundation.shape.RoundedCornerShape
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsTopBarRootActions
import com.distrigo.app.ui.designsystem.DsTopBarSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.Product
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

internal fun formatQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

enum class SortOption(val label: String) {
    NAME_ASC   ("Nom (A → Z)"),
    NAME_DESC  ("Nom (Z → A)"),
    STOCK_ASC  ("Stock (croissant)"),
    STOCK_DESC ("Stock (décroissant)"),
    PRICE_ASC  ("Prix (croissant)"),
    PRICE_DESC ("Prix (décroissant)")
}

private data class ActiveFilterEntry(val label: String, val onClear: () -> Unit)

private fun isExpiringSoon(expiryDate: String?, withinDays: Int = 30): Boolean {
    val date = expiryDate?.let { runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(it) }.getOrNull() } ?: return false
    val diffDays = (date.time - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)
    return diffDays in 0..withinDays
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel      : ProductViewModel = hiltViewModel(),
    onOpenMenu           : (() -> Unit)? = null,
    onNotificationsClick : () -> Unit = {},
    onProfileClick       : () -> Unit = {},

    modifier       : Modifier = Modifier,
    onAddProduct   : () -> Unit = {},
    onEditProduct  : (Int) -> Unit = {},
    onProductClick : (Int) -> Unit = {}
) {
    val products      by viewModel.products.collectAsState()
    val isLoading     by viewModel.isLoading.collectAsState()
    val error         by viewModel.error.collectAsState()
    val categories    by viewModel.categories.collectAsState()
    val sousCategories by viewModel.sousCategories.collectAsState()
    val marques        by viewModel.marques.collectAsState()
    val suppliers       by viewModel.suppliers.collectAsState()
    var longPressProduct by remember { mutableStateOf<Product?>(null) }

    var search           by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<Product?>(null) }
    var showScanner      by remember { mutableStateOf(false) }
    var isGridView       by remember { mutableStateOf(false) }
    var sortOption       by remember { mutableStateOf(SortOption.NAME_ASC) }
    var showSortSheet    by remember { mutableStateOf(false) }
    val sheetState        = rememberModalBottomSheetState()

    // ── Filters ──
    var filterCategoryId      by remember { mutableStateOf<Int?>(null) }
    var filterSousCategorieId by remember { mutableStateOf<Int?>(null) }
    var filterMarqueId        by remember { mutableStateOf<Int?>(null) }
    var filterSupplierId      by remember { mutableStateOf<Int?>(null) }
    var filterUnitType        by remember { mutableStateOf<String?>(null) }
    var filterStockLevel      by remember { mutableStateOf<String?>(null) }
    var filterPriceMin        by remember { mutableStateOf("") }
    var filterPriceMax        by remember { mutableStateOf("") }
    var filterExpiringSoon    by remember { mutableStateOf(false) }
    var showFilterSheet       by remember { mutableStateOf(false) }
    val filterSheetState       = rememberModalBottomSheetState()

    var isInitialFilterCategoryEffect by remember { mutableStateOf(true) }
    LaunchedEffect(filterCategoryId) {
        if (isInitialFilterCategoryEffect) {
            isInitialFilterCategoryEffect = false
        } else {
            filterSousCategorieId = null
        }
    }

    fun clearAllFilters() {
        filterCategoryId      = null
        filterSousCategorieId = null
        filterMarqueId        = null
        filterSupplierId      = null
        filterUnitType        = null
        filterStockLevel      = null
        filterPriceMin        = ""
        filterPriceMax        = ""
        filterExpiringSoon    = false
    }

    if (showScanner) {
        BarcodeScannerScreen(
            onBarcodeScanned = { code -> search = code; showScanner = false },
            onClose          = { showScanner = false }
        )
        return
    }

    val tokens   = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    val filtered = products.filter { product ->
        (tokens.isEmpty() || tokens.all { token ->
            product.name.contains(token, ignoreCase = true) ||
                    (product.barcode?.contains(token, ignoreCase = true) == true)
        }) &&
                (filterCategoryId == null || product.category_id == filterCategoryId) &&
                (filterSousCategorieId == null || product.sous_categorie_id == filterSousCategorieId) &&
                (filterMarqueId == null || product.marque_id == filterMarqueId) &&
                (filterSupplierId == null || product.supplier_id == filterSupplierId) &&
                (filterUnitType == null || product.unit_type == filterUnitType) &&
                (filterStockLevel == null || when (filterStockLevel) {
                    "in_stock"     -> product.stock > product.min_stock
                    "low_stock"    -> product.stock in 1.0..product.min_stock.toDouble()
                    "out_of_stock" -> product.stock <= 0
                    else           -> true
                }) &&
                (filterPriceMin.toDoubleOrNull()?.let { product.selling_price >= it } ?: true) &&
                (filterPriceMax.toDoubleOrNull()?.let { product.selling_price <= it } ?: true) &&
                (!filterExpiringSoon || (product.has_expiry == 1 && isExpiringSoon(product.expiry_date)))
    }
    val sorted = when (sortOption) {
        SortOption.NAME_ASC   -> filtered.sortedBy { it.name.lowercase() }
        SortOption.NAME_DESC  -> filtered.sortedByDescending { it.name.lowercase() }
        SortOption.STOCK_ASC  -> filtered.sortedBy { it.stock }
        SortOption.STOCK_DESC -> filtered.sortedByDescending { it.stock }
        SortOption.PRICE_ASC  -> filtered.sortedBy { it.selling_price }
        SortOption.PRICE_DESC -> filtered.sortedByDescending { it.selling_price }
    }

    val activeFilters = buildList {
        filterCategoryId?.let { id ->
            categories.find { it.id == id }?.let {
                add(ActiveFilterEntry("Catégorie: ${it.name}") { filterCategoryId = null })
            }
        }
        filterSousCategorieId?.let { id ->
            sousCategories.find { it.id == id }?.let {
                add(ActiveFilterEntry("Sous-catégorie: ${it.name}") { filterSousCategorieId = null })
            }
        }
        filterMarqueId?.let { id ->
            marques.find { it.id == id }?.let {
                add(ActiveFilterEntry("Marque: ${it.name}") { filterMarqueId = null })
            }
        }
        filterSupplierId?.let { id ->
            suppliers.find { it.id == id }?.let {
                add(ActiveFilterEntry("Fournisseur: ${it.name}") { filterSupplierId = null })
            }
        }
        filterUnitType?.let { unit ->
            add(ActiveFilterEntry("Unité: ${if (unit == "pièce") "Pièce" else "Carton"}") { filterUnitType = null })
        }
        filterStockLevel?.let { level ->
            val label = when (level) {
                "in_stock"     -> "En stock"
                "low_stock"    -> "Stock faible"
                "out_of_stock" -> "Rupture de stock"
                else           -> level
            }
            add(ActiveFilterEntry("Stock: $label") { filterStockLevel = null })
        }
        if (filterPriceMin.isNotBlank()) add(ActiveFilterEntry("Prix min: $filterPriceMin") { filterPriceMin = "" })
        if (filterPriceMax.isNotBlank()) add(ActiveFilterEntry("Prix max: $filterPriceMax") { filterPriceMax = "" })
        if (filterExpiringSoon) add(ActiveFilterEntry("Bientôt périmé") { filterExpiringSoon = false })
    }
    val hasActiveFilters = activeFilters.isNotEmpty()

    showDeleteDialog?.let { product ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Supprimer le produit") },
            text  = { Text("Voulez-vous supprimer \"${product.name}\" ?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProduct(product.id)
                    showDeleteDialog = null
                }) { Text("Supprimer", color = DsColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Annuler") }
            },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    longPressProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { longPressProduct = null },
            title = { Text(product.name, fontWeight = FontWeight.Bold) },
            confirmButton = {},
            dismissButton = {},
            shape = DsShapes.large,
            containerColor = DsColors.Surface,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.medium)
                            .background(DsColors.PrimaryLight)
                            .clickable {
                                onEditProduct(product.id)
                                longPressProduct = null
                            }
                            .padding(14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                        Text("Modifier", fontSize = DsTextSize.body, color = DsColors.Primary, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.medium)
                            .background(DsColors.DangerLight)
                            .clickable {
                                showDeleteDialog = product
                                longPressProduct = null
                            }
                            .padding(14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(20.dp))
                        Text("Supprimer", fontSize = DsTextSize.body, color = DsColors.Danger, fontWeight = FontWeight.Medium)
                    }
                }
            },
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            sheetState       = sheetState,
            containerColor   = DsColors.Surface
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
                Text("Trier par", fontWeight = FontWeight.Bold, fontSize = DsTextSize.bodyLarge, color = DsColors.TextPrimary)
                Spacer(Modifier.height(DsSpacing.md))
                SortOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.medium)
                            .background(if (sortOption == option) DsColors.PrimaryLight else Color.Transparent)
                            .clickable { sortOption = option; showSortSheet = false }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text       = option.label,
                            fontSize   = DsTextSize.body,
                            color      = if (sortOption == option) DsColors.Primary else DsColors.TextPrimary,
                            fontWeight = if (sortOption == option) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (sortOption == option) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState       = filterSheetState,
            containerColor   = DsColors.Surface
        ) {
            var filterCategoryExpanded      by remember { mutableStateOf(false) }
            var filterSousCategorieExpanded by remember { mutableStateOf(false) }
            var filterMarqueExpanded        by remember { mutableStateOf(false) }
            var filterSupplierExpanded      by remember { mutableStateOf(false) }
            var filterStockLevelExpanded    by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Filtres avancés", fontWeight = FontWeight.Bold, fontSize = DsTextSize.bodyLarge, color = DsColors.TextPrimary)
                    IconButton(onClick = { showFilterSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = DsColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(DsSpacing.sm))

                Text("Catégorie", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                ExposedDropdownMenuBox(
                    expanded         = filterCategoryExpanded,
                    onExpandedChange = { filterCategoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value         = categories.find { it.id == filterCategoryId }?.name ?: "Toutes les catégories",
                        onValueChange = {},
                        readOnly      = true,
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterCategoryExpanded) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                        shape         = DsShapes.medium,
                        colors        = dsTextFieldColors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary
                        )
                    )
                    ExposedDropdownMenu(expanded = filterCategoryExpanded, onDismissRequest = { filterCategoryExpanded = false }) {
                        DropdownMenuItem(
                            text    = { Text("Toutes les catégories", color = DsColors.TextSecondary) },
                            onClick = { filterCategoryId = null; filterCategoryExpanded = false }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text    = { Text(category.name) },
                                onClick = { filterCategoryId = category.id; filterCategoryExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                val sousCategorieEnabled = filterCategoryId != null
                Text(
                    "Sous-catégorie",
                    fontSize = DsTextSize.bodySmall,
                    color    = if (sousCategorieEnabled) DsColors.TextSecondary else DsColors.TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                ExposedDropdownMenuBox(
                    expanded         = sousCategorieEnabled && filterSousCategorieExpanded,
                    onExpandedChange = { if (sousCategorieEnabled) filterSousCategorieExpanded = it }
                ) {
                    OutlinedTextField(
                        value         = sousCategories.find { it.id == filterSousCategorieId }?.name ?: "Toutes les sous-catégories",
                        onValueChange = {},
                        readOnly      = true,
                        enabled       = sousCategorieEnabled,
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterSousCategorieExpanded) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                        shape         = DsShapes.medium,
                        colors        = dsTextFieldColors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary,
                            disabledBorderColor  = DsColors.Border.copy(alpha = 0.5f),
                            disabledTextColor    = DsColors.TextSecondary.copy(alpha = 0.5f)
                        )
                    )
                    ExposedDropdownMenu(expanded = filterSousCategorieExpanded, onDismissRequest = { filterSousCategorieExpanded = false }) {
                        DropdownMenuItem(
                            text    = { Text("Toutes les sous-catégories", color = DsColors.TextSecondary) },
                            onClick = { filterSousCategorieId = null; filterSousCategorieExpanded = false }
                        )
                        sousCategories.filter { it.category_id == filterCategoryId }.forEach { sousCategorie ->
                            DropdownMenuItem(
                                text    = { Text(sousCategorie.name) },
                                onClick = { filterSousCategorieId = sousCategorie.id; filterSousCategorieExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Marque", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                ExposedDropdownMenuBox(
                    expanded         = filterMarqueExpanded,
                    onExpandedChange = { filterMarqueExpanded = it }
                ) {
                    OutlinedTextField(
                        value         = marques.find { it.id == filterMarqueId }?.name ?: "Toutes les marques",
                        onValueChange = {},
                        readOnly      = true,
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterMarqueExpanded) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                        shape         = DsShapes.medium,
                        colors        = dsTextFieldColors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary
                        )
                    )
                    ExposedDropdownMenu(expanded = filterMarqueExpanded, onDismissRequest = { filterMarqueExpanded = false }) {
                        DropdownMenuItem(
                            text    = { Text("Toutes les marques", color = DsColors.TextSecondary) },
                            onClick = { filterMarqueId = null; filterMarqueExpanded = false }
                        )
                        marques.forEach { marque ->
                            DropdownMenuItem(
                                text    = { Text(marque.name) },
                                onClick = { filterMarqueId = marque.id; filterMarqueExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Fournisseur", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                ExposedDropdownMenuBox(
                    expanded         = filterSupplierExpanded,
                    onExpandedChange = { filterSupplierExpanded = it }
                ) {
                    OutlinedTextField(
                        value         = suppliers.find { it.id == filterSupplierId }?.name ?: "Tous les fournisseurs",
                        onValueChange = {},
                        readOnly      = true,
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterSupplierExpanded) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                        shape         = DsShapes.medium,
                        colors        = dsTextFieldColors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary
                        )
                    )
                    ExposedDropdownMenu(expanded = filterSupplierExpanded, onDismissRequest = { filterSupplierExpanded = false }) {
                        DropdownMenuItem(
                            text    = { Text("Tous les fournisseurs", color = DsColors.TextSecondary) },
                            onClick = { filterSupplierId = null; filterSupplierExpanded = false }
                        )
                        suppliers.forEach { supplier ->
                            DropdownMenuItem(
                                text    = { Text(supplier.name) },
                                onClick = { filterSupplierId = supplier.id; filterSupplierExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Unité de stockage", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Pair<String?, String>>(null to "Toutes", "carton" to "Carton", "pièce" to "Pièce").forEach { (value, label) ->
                        val active = filterUnitType == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(DsShapes.medium)
                                .background(if (active) DsColors.Primary else DsColors.Surface)
                                .border(1.dp, if (active) DsColors.Primary else DsColors.Border, DsShapes.medium)
                                .clickable { filterUnitType = value }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                fontSize   = DsTextSize.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color      = if (active) Color.White else DsColors.TextPrimary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Niveau de stock", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                ExposedDropdownMenuBox(
                    expanded         = filterStockLevelExpanded,
                    onExpandedChange = { filterStockLevelExpanded = it }
                ) {
                    OutlinedTextField(
                        value = when (filterStockLevel) {
                            "in_stock"     -> "En stock"
                            "low_stock"    -> "Stock faible"
                            "out_of_stock" -> "Rupture de stock"
                            else           -> "Tous"
                        },
                        onValueChange = {},
                        readOnly      = true,
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterStockLevelExpanded) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                        shape         = DsShapes.medium,
                        colors        = dsTextFieldColors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary
                        )
                    )
                    ExposedDropdownMenu(expanded = filterStockLevelExpanded, onDismissRequest = { filterStockLevelExpanded = false }) {
                        listOf<Pair<String?, String>>(
                            null to "Tous",
                            "in_stock" to "En stock",
                            "low_stock" to "Stock faible",
                            "out_of_stock" to "Rupture de stock"
                        ).forEach { (value, label) ->
                            DropdownMenuItem(
                                text    = { Text(label) },
                                onClick = { filterStockLevel = value; filterStockLevelExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Fourchette de prix", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value           = filterPriceMin,
                        onValueChange   = { filterPriceMin = it },
                        placeholder     = { Text("Min") },
                        singleLine      = true,
                        modifier        = Modifier.weight(1f),
                        shape           = DsShapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors          = dsTextFieldColors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary
                        )
                    )
                    OutlinedTextField(
                        value           = filterPriceMax,
                        onValueChange   = { filterPriceMax = it },
                        placeholder     = { Text("Max") },
                        singleLine      = true,
                        modifier        = Modifier.weight(1f),
                        shape           = DsShapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors          = dsTextFieldColors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary
                        )
                    )
                }
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Bientôt périmé", fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary)
                        Text("Produits expirant dans les 30 prochains jours", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                    Switch(
                        checked         = filterExpiringSoon,
                        onCheckedChange = { filterExpiringSoon = it },
                        colors          = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DsColors.Primary)
                    )
                }
                Spacer(Modifier.height(DsSpacing.lg))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick  = { clearAllFilters(); showFilterSheet = false },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = DsShapes.medium,
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = DsColors.TextPrimary),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                    ) {
                        Text("Réinitialiser", fontSize = DsTextSize.body, fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick  = { showFilterSheet = false },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = DsShapes.medium,
                        colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                    ) {
                        Text("Appliquer (${filtered.size})", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DsColors.Surface)
        ) {
            // The tab roots are the only screens with no back arrow, so the menu takes the
            // leading slot and the global controls take the trailing one.
            DsTopAppBar(
                title   = "Produits",
                leading = onOpenMenu?.let { DsTopBarLeading.Menu(it) } ?: DsTopBarLeading.None,
                size    = DsTopBarSize.Large
            ) {
                DsTopBarRootActions(
                    onNotificationsClick = onNotificationsClick,
                    onProfileClick       = onProfileClick
                )
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Rechercher par nom ou code-barres…", fontSize = DsTextSize.caption) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(DsShapes.large),
                shape = DsShapes.large,
                singleLine = true,
                colors = dsTextFieldColors(
                    unfocusedBorderColor = DsColors.Border,
                    focusedBorderColor = DsColors.Primary,
                )
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${sorted.size} produit(s)", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .clip(DsShapes.medium)
                            .background(DsColors.SurfaceSunken)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(DsShapes.small)
                                .background(if (!isGridView) DsColors.Primary else Color.Transparent)
                                .clickable { isGridView = false }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ViewList,
                                contentDescription = "Liste",
                                tint = if (!isGridView) Color.White else DsColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(DsShapes.small)
                                .background(if (isGridView) DsColors.Primary else Color.Transparent)
                                .clickable { isGridView = true }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.GridView,
                                contentDescription = "Grille",
                                tint = if (isGridView) Color.White else DsColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(DsShapes.medium)
                            .background(DsColors.SurfaceSunken)
                            .clickable { showSortSheet = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Sort,
                                contentDescription = "Trier",
                                tint = if (sortOption != SortOption.NAME_ASC) DsColors.Primary else DsColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Trier",
                                fontSize = DsTextSize.caption,
                                color = if (sortOption != SortOption.NAME_ASC) DsColors.Primary else DsColors.TextSecondary
                            )
                        }
                    }

                    Box {
                        Box(
                            modifier = Modifier
                                .clip(DsShapes.medium)
                                .background(DsColors.SurfaceSunken)
                                .clickable { showFilterSheet = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = "Filtres",
                                    tint = if (hasActiveFilters) DsColors.Primary else DsColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Filtres",
                                    fontSize = DsTextSize.caption,
                                    color = if (hasActiveFilters) DsColors.Primary else DsColors.TextSecondary
                                )
                            }
                        }
                        if (hasActiveFilters) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(DsColors.Primary)
                            )
                        }
                    }
                }
            }

            if (hasActiveFilters) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(activeFilters) { entry ->
                        Row(
                            modifier = Modifier
                                .clip(DsShapes.pill)
                                .background(DsColors.PrimaryLight)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(entry.label, fontSize = DsTextSize.bodySmall, color = DsColors.Primary)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Retirer",
                                tint = DsColors.Primary,
                                modifier = Modifier.size(14.dp).clickable { entry.onClear() }
                            )
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .clip(DsShapes.pill)
                                .clickable { clearAllFilters() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Tout effacer", fontSize = DsTextSize.bodySmall, color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
            } else if (error != null) {
                val errorMessage = error
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = errorMessage ?: "", color = DsColors.Danger)
                }
            } else if (sorted.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = DsColors.Primary.copy(alpha = 0.3f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Aucun produit trouvé", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                if (!isGridView) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = DsSpacing.fabBottomClearance + 56.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sorted) { product ->
                            ProductCard(product = product,
                                onClick = { onProductClick(product.id) },
                                onLongClick = { longPressProduct = product }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns             = GridCells.Fixed(2),
                        contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = DsSpacing.fabBottomClearance + 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sorted) { product ->
                            ProductGridCard(product = product,
                                onClick = { onProductClick(product.id) },
                                onLongClick = { longPressProduct = product })
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick        = { onAddProduct() },
            containerColor = DsColors.Primary,
            contentColor   = Color.White,
            modifier       = Modifier.align(Alignment.BottomEnd).padding(end = DsSpacing.lg, bottom = DsSpacing.fabBottomClearance)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Ajouter un produit")
        }
    }
}

@Composable
fun ProductCard(product: Product, onClick: () -> Unit,    onLongClick : () -> Unit = {}
) {
    val isLow = product.stock < product.min_stock

    Card(
        modifier  = Modifier.fillMaxWidth().combinedClickable(
            onClick     = { onClick() },
            onLongClick = { onLongClick() }
        ),
        shape     = DsShapes.large,
        colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, if (isLow) DsColors.DangerLight else DsColors.Border)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier         = Modifier.size(36.dp).clip(DsShapes.small).background(DsColors.PrimaryLight),
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
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // ── السطر 1 : الاسم لوحده ──
                Text(
                    product.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = DsTextSize.bodySmall,
                    color      = DsColors.TextPrimary,
                    maxLines   = 1,
                    overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(3.dp))

                // ── السطر 2 : Catégorie · Fournisseur ──
                Text(
                    "${product.category_name ?: "—"} · ${product.supplier_name ?: "—"}",
                    fontSize = DsTextSize.caption,
                    color    = DsColors.TextSecondary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                // ── السطر 3 : Prix ──
                Text(
                    "${product.selling_price} DA",
                    fontWeight = FontWeight.Bold,
                    fontSize   = DsTextSize.body,
                    color      = DsColors.Primary
                )
            }

            Spacer(Modifier.width(8.dp))

            // ── Stock : عمود منفصل على اليمين ──
            Text(
                "${formatQty(product.stock)} ${product.unit_type}",
                fontSize   = DsTextSize.caption,
                fontWeight = if (isLow) FontWeight.SemiBold else FontWeight.Medium,
                color      = if (isLow) DsColors.Danger else DsColors.TextSecondary,
                textAlign  = androidx.compose.ui.text.style.TextAlign.End,
                maxLines   = 1,
                softWrap   = false
            )

            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun ProductGridCard(product: Product, onClick: () -> Unit,    onLongClick : () -> Unit = {}
) {
    val isLow = product.stock < product.min_stock

    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onClick() },
        shape     = DsShapes.large,
        colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, if (isLow) DsColors.DangerLight else DsColors.Border)
    ) {
        Column {
            // ── Image ──
            Box(
                modifier         = Modifier.fillMaxWidth().height(110.dp).background(DsColors.SurfaceMuted),
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
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = DsColors.Primary.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                }
                // Stock badge
                if (isLow) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(DsShapes.small)
                            .background(DsColors.Danger)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("!", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Info ──
            Column(modifier = Modifier.padding(10.dp)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text("${product.selling_price} DA", fontWeight = FontWeight.Bold, fontSize = DsTextSize.body, color = DsColors.Primary)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "${formatQty(product.stock)} ${product.unit_type}",
                        fontSize   = DsTextSize.caption,
                        fontWeight = if (isLow) FontWeight.SemiBold else FontWeight.Normal,
                        color      = if (isLow) DsColors.Danger else DsColors.TextSecondary
                    )
                    Text(
                        text     = product.category_name ?: "—",
                        fontSize = 10.sp,
                        color    = DsColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}