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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.distrigo.app.ui.common.KeepIndexScrollPosition
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.ProductImage
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import java.util.Locale
import com.distrigo.app.ui.common.EntityImage
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.DsCompactSearchAction

internal fun formatQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format(Locale.ROOT, "%.2f", v)

enum class SortOption(val label: String) {
    NAME_ASC   ("Nom (A → Z)"),
    NAME_DESC  ("Nom (Z → A)"),
    STOCK_ASC  ("Stock (croissant)"),
    STOCK_DESC ("Stock (décroissant)"),
    PRICE_ASC  ("Prix (croissant)"),
    PRICE_DESC ("Prix (décroissant)")
}

private data class ActiveFilterEntry(val label: String, val onClear: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel      : ProductViewModel = hiltViewModel(),
    onOpenMenu           : (() -> Unit)? = null,
    onNotificationsClick : () -> Unit = {},
    onProfileClick       : () -> Unit = {},

    modifier       : Modifier = Modifier,
    // Raised while the photo viewer is open, so the host hides the bottom nav and the viewer gets
    // the whole screen. This is the same signal every full-screen destination in the app already
    // uses; the viewer is not a destination, so it has to say so itself.
    onFullScreenChange   : (Boolean) -> Unit = {},
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

    // Tapping a row's photo opens the viewer on that product's gallery rather than the product.
    // The gallery lives in its own table, so it has to be fetched for whichever product was
    // tapped; until it arrives the cover the row was already showing stands in for it.
    var viewerProduct by remember { mutableStateOf<Product?>(null) }
    val galleryImages by viewModel.productImages.collectAsState()
    LaunchedEffect(viewerProduct?.id) {
        viewerProduct?.let { viewModel.observeGalleryFor(it.id) }
    }

    val viewerOpen = viewerProduct != null
    LaunchedEffect(viewerOpen) { onFullScreenChange(viewerOpen) }
    // Leaving the screen with the flag still raised would strand the bottom nav hidden, since
    // nothing else here would ever lower it.
    DisposableEffect(Unit) { onDispose { onFullScreenChange(false) } }

    var showDeleteDialog by remember { mutableStateOf<Product?>(null) }
    var showScanner      by remember { mutableStateOf(false) }
    var showSortSheet    by remember { mutableStateOf(false) }
    val sheetState        = rememberModalBottomSheetState()

    // ── Filter sheet, local UI state only — the filters themselves live in the ViewModel ──
    var showFilterSheet       by remember { mutableStateOf(false) }
    val filterSheetState       = rememberModalBottomSheetState()

    var isInitialFilterCategoryEffect by remember { mutableStateOf(true) }
    LaunchedEffect(viewModel.filterCategoryId) {
        if (isInitialFilterCategoryEffect) {
            isInitialFilterCategoryEffect = false
        } else {
            viewModel.filterSousCategorieId = null
        }
    }

    if (showScanner) {
        BarcodeScannerScreen(
            onBarcodeScanned = { code -> viewModel.searchQuery = code; showScanner = false },
            onClose          = { showScanner = false }
        )
        return
    }

    // Filtered and sorted only when something they read changes. Inline, both re-ran on every
    // recomposition — each keystroke, but also opening the sort sheet, a long press, the photo
    // viewer. Every value the filter reads is a key; ProductListFilters.kt has the logic.
    val filtered = remember(
        products,
        viewModel.searchQuery,
        viewModel.filterCategoryId,
        viewModel.filterSousCategorieId,
        viewModel.filterMarqueId,
        viewModel.filterSupplierId,
        viewModel.filterUnitType,
        viewModel.filterStockLevel,
        viewModel.filterPriceMin,
        viewModel.filterPriceMax,
        viewModel.filterExpiringSoon
    ) {
        filterProducts(
            products = products,
            query    = viewModel.searchQuery,
            filters  = ProductListFilters(
                categoryId      = viewModel.filterCategoryId,
                sousCategorieId = viewModel.filterSousCategorieId,
                marqueId        = viewModel.filterMarqueId,
                supplierId      = viewModel.filterSupplierId,
                unitType        = viewModel.filterUnitType,
                stockLevel      = viewModel.filterStockLevel,
                priceMin        = viewModel.filterPriceMin,
                priceMax        = viewModel.filterPriceMax,
                expiringSoon    = viewModel.filterExpiringSoon
            )
        )
    }
    val sorted = remember(filtered, viewModel.sortOption) { sortProducts(filtered, viewModel.sortOption) }

    val activeFilters = buildList {
        viewModel.filterCategoryId?.let { id ->
            categories.find { it.id == id }?.let {
                add(ActiveFilterEntry("Catégorie: ${it.name}") { viewModel.filterCategoryId = null })
            }
        }
        viewModel.filterSousCategorieId?.let { id ->
            sousCategories.find { it.id == id }?.let {
                add(ActiveFilterEntry("Sous-catégorie: ${it.name}") { viewModel.filterSousCategorieId = null })
            }
        }
        viewModel.filterMarqueId?.let { id ->
            marques.find { it.id == id }?.let {
                add(ActiveFilterEntry("Marque: ${it.name}") { viewModel.filterMarqueId = null })
            }
        }
        viewModel.filterSupplierId?.let { id ->
            suppliers.find { it.id == id }?.let {
                add(ActiveFilterEntry("Fournisseur: ${it.name}") { viewModel.filterSupplierId = null })
            }
        }
        viewModel.filterUnitType?.let { unit ->
            add(ActiveFilterEntry("Unité: ${if (unit == "pièce") "Pièce" else "Carton"}") { viewModel.filterUnitType = null })
        }
        viewModel.filterStockLevel?.let { level ->
            val label = when (level) {
                "in_stock"     -> "En stock"
                "low_stock"    -> "Stock faible"
                "out_of_stock" -> "Rupture de stock"
                else           -> level
            }
            add(ActiveFilterEntry("Stock: $label") { viewModel.filterStockLevel = null })
        }
        if (viewModel.filterPriceMin.isNotBlank()) add(ActiveFilterEntry("Prix min: ${viewModel.filterPriceMin}") { viewModel.filterPriceMin = "" })
        if (viewModel.filterPriceMax.isNotBlank()) add(ActiveFilterEntry("Prix max: ${viewModel.filterPriceMax}") { viewModel.filterPriceMax = "" })
        if (viewModel.filterExpiringSoon) add(ActiveFilterEntry("Bientôt périmé") { viewModel.filterExpiringSoon = false })
    }
    val hasActiveFilters = activeFilters.isNotEmpty()

    showDeleteDialog?.let { product ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Supprimer le produit") },
            text  = { Text("\"${product.name}\" ira dans la corbeille (Paramètres › Corbeille), d'où vous pourrez le restaurer.") },
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
                            .background(if (viewModel.sortOption == option) DsColors.PrimaryLight else Color.Transparent)
                            .clickable { viewModel.sortOption = option; showSortSheet = false }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text       = option.label,
                            fontSize   = DsTextSize.body,
                            color      = if (viewModel.sortOption == option) DsColors.Primary else DsColors.TextPrimary,
                            fontWeight = if (viewModel.sortOption == option) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (viewModel.sortOption == option) {
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
                        value         = categories.find { it.id == viewModel.filterCategoryId }?.name ?: "Toutes les catégories",
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
                            onClick = { viewModel.filterCategoryId = null; filterCategoryExpanded = false }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text    = { Text(category.name) },
                                onClick = { viewModel.filterCategoryId = category.id; filterCategoryExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                val sousCategorieEnabled = viewModel.filterCategoryId != null
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
                        value         = sousCategories.find { it.id == viewModel.filterSousCategorieId }?.name ?: "Toutes les sous-catégories",
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
                            onClick = { viewModel.filterSousCategorieId = null; filterSousCategorieExpanded = false }
                        )
                        sousCategories.filter { it.category_id == viewModel.filterCategoryId }.forEach { sousCategorie ->
                            DropdownMenuItem(
                                text    = { Text(sousCategorie.name) },
                                onClick = { viewModel.filterSousCategorieId = sousCategorie.id; filterSousCategorieExpanded = false }
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
                        value         = marques.find { it.id == viewModel.filterMarqueId }?.name ?: "Toutes les marques",
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
                            onClick = { viewModel.filterMarqueId = null; filterMarqueExpanded = false }
                        )
                        marques.forEach { marque ->
                            DropdownMenuItem(
                                text    = { Text(marque.name) },
                                onClick = { viewModel.filterMarqueId = marque.id; filterMarqueExpanded = false }
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
                        value         = suppliers.find { it.id == viewModel.filterSupplierId }?.name ?: "Tous les fournisseurs",
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
                            onClick = { viewModel.filterSupplierId = null; filterSupplierExpanded = false }
                        )
                        suppliers.forEach { supplier ->
                            DropdownMenuItem(
                                text    = { Text(supplier.name) },
                                onClick = { viewModel.filterSupplierId = supplier.id; filterSupplierExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Unité de stockage", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Pair<String?, String>>(null to "Toutes", "carton" to "Carton", "pièce" to "Pièce").forEach { (value, label) ->
                        val active = viewModel.filterUnitType == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(DsShapes.medium)
                                .background(if (active) DsColors.Primary else DsColors.Surface)
                                .border(1.dp, if (active) DsColors.Primary else DsColors.Border, DsShapes.medium)
                                .clickable { viewModel.filterUnitType = value }
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
                        value = when (viewModel.filterStockLevel) {
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
                                onClick = { viewModel.filterStockLevel = value; filterStockLevelExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Fourchette de prix", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value           = viewModel.filterPriceMin,
                        onValueChange   = { viewModel.filterPriceMin = it },
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
                        value           = viewModel.filterPriceMax,
                        onValueChange   = { viewModel.filterPriceMax = it },
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
                        checked         = viewModel.filterExpiringSoon,
                        onCheckedChange = { viewModel.filterExpiringSoon = it },
                        colors          = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DsColors.Primary)
                    )
                }
                Spacer(Modifier.height(DsSpacing.lg))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick  = { viewModel.clearAllFilters(); showFilterSheet = false },
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

            DsCompactSearchField(
                value         = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                placeholder   = "Rechercher un produit",
                modifier      = Modifier.padding(horizontal = DsSpacing.lg)
            ) {
                DsCompactSearchAction(
                    icon               = Icons.Default.QrCodeScanner,
                    contentDescription = "Scanner un code-barres",
                    tint               = DsColors.Primary,
                    onClick            = { showScanner = true }
                )
            }

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
                                .background(if (!viewModel.isGridView) DsColors.Primary else Color.Transparent)
                                .clickable { viewModel.isGridView = false }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ViewList,
                                contentDescription = "Liste",
                                tint = if (!viewModel.isGridView) Color.White else DsColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(DsShapes.small)
                                .background(if (viewModel.isGridView) DsColors.Primary else Color.Transparent)
                                .clickable { viewModel.isGridView = true }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.GridView,
                                contentDescription = "Grille",
                                tint = if (viewModel.isGridView) Color.White else DsColors.TextSecondary,
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
                                tint = if (viewModel.sortOption != SortOption.NAME_ASC) DsColors.Primary else DsColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Trier",
                                fontSize = DsTextSize.caption,
                                color = if (viewModel.sortOption != SortOption.NAME_ASC) DsColors.Primary else DsColors.TextSecondary
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
                                .clickable { viewModel.clearAllFilters() }
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
                if (!viewModel.isGridView) {
                    // Keyed rows would keep the first visible product in view when a search, filter
                    // or sort changes the list; the list keeps its place by index, as it always has.
                    val listState = rememberLazyListState()
                    KeepIndexScrollPosition(listState, sorted)
                    LazyColumn(
                        state          = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = DsSpacing.fabBottomClearance + 56.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sorted, key = { it.id }) { product ->
                            ProductCard(product = product,
                                onClick = { onProductClick(product.id) },
                                onImageClick = {
                                    // A product with no photo has no gallery to show, so its
                                    // placeholder behaves like the rest of the card.
                                    if (product.image_uri.isNullOrBlank()) onProductClick(product.id)
                                    else viewerProduct = product
                                },
                                onLongClick = { longPressProduct = product }
                            )
                        }
                    }
                } else {
                    val gridState = rememberLazyGridState()
                    KeepIndexScrollPosition(gridState, sorted)
                    LazyVerticalGrid(
                        state               = gridState,
                        columns             = GridCells.Fixed(2),
                        contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = DsSpacing.fabBottomClearance + 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sorted, key = { it.id }) { product ->
                            ProductGridCard(product = product,
                                onClick = { onProductClick(product.id) },
                                onImageClick = {
                                    if (product.image_uri.isNullOrBlank()) onProductClick(product.id)
                                    else viewerProduct = product
                                },
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

        // Last sibling in the Box: siblings stack in emission order, so the viewer has to be
        // emitted after the list and the FAB to draw over them.
        viewerProduct?.let { product ->
            val gallery = galleryImages
                .filter { it.productId == product.id }
                .ifEmpty {
                    // The gallery is one Room emission away. Standing the cover in for it means
                    // the viewer opens on the picture that was tapped instead of on a blank
                    // frame, and the real gallery replaces it as soon as it lands.
                    listOfNotNull(
                        product.image_uri
                            ?.takeIf { it.isNotBlank() }
                            ?.let { ProductImage(id = 0, productId = product.id, ref = it, position = 0) }
                    )
                }
            if (gallery.isNotEmpty()) {
                ProductImageViewer(
                    images       = gallery,
                    initialIndex = 0,
                    onClose      = { viewerProduct = null }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductCard(
    product: Product,
    onClick: () -> Unit,
    onImageClick: () -> Unit = onClick,
    onLongClick : () -> Unit = {}
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
        // The start padding is 8.dp rather than 14.dp because the photo's touch target below is
        // 48.dp around a 36.dp image, which puts 6.dp of slack on each side of it. Taking that
        // 6.dp back here, and another 6.dp off the spacer after it, leaves the image and the text
        // on exactly the pixels they were on when the target was 36.dp.
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 48.dp of touch around 36.dp of picture. The visible square is the inner Box; this
            // outer one is invisible and exists only so the target clears the 48.dp minimum —
            // a 36.dp target is small enough to miss, and missing it opens the product instead,
            // which is the wrong screen and costs a trip back.
            Box(
                modifier         = Modifier
                    .size(48.dp)
                    // Nested inside the card's own click: a child sees the event first, so a tap
                    // here opens the photo and a tap anywhere else on the row opens the product.
                    // combinedClickable rather than clickable, so the long press that opens the
                    // row's menu still works when it lands on the thumbnail.
                    .combinedClickable(
                        onClick     = { onImageClick() },
                        onLongClick = { onLongClick() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier         = Modifier
                        .size(36.dp)
                        .clip(DsShapes.small)
                        .background(DsColors.PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    EntityImage(
                        ref                = product.image_uri,
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize()
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.width(6.dp))

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
fun ProductGridCard(
    product: Product,
    onClick: () -> Unit,
    onImageClick: () -> Unit = onClick,
    onLongClick : () -> Unit = {}
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
                modifier         = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(DsColors.SurfaceMuted)
                    // Only this panel opens the photo. The name, the price and every other part
                    // of the card fall through to the card's own click and open the product.
                    .clickable { onImageClick() },
                contentAlignment = Alignment.Center
            ) {
                EntityImage(
                    ref                = product.image_uri,
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize().padding(10.dp),
                    contentScale       = androidx.compose.ui.layout.ContentScale.Fit
                ) {
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