package com.distrigo.app.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import com.distrigo.app.data.model.Category
import com.distrigo.app.ui.categories.CategoriesScreen
import com.distrigo.app.ui.scanner.BarcodeScannerScreen
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Product
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

val PrimaryBlue    = Color(0xFF1565C0)
val BlueLight      = Color(0xFFE3F2FD)
val RedLight       = Color(0xFFFFEBEE)
val GreenLight     = Color(0xFFE8F5E9)
val AccentGreen    = Color(0xFF2E7D32)
val DestructiveRed = Color(0xFFC62828)
val MutedGray      = Color(0xFFF5F5F5)
val BorderGray     = Color(0xFFE0E0E0)
val TextPrimary    = Color(0xFF1A1A1A)
val TextMuted      = Color(0xFF757575)

enum class SortOption(val label: String) {
    NAME_ASC   ("Nom (A → Z)"),
    NAME_DESC  ("Nom (Z → A)"),
    STOCK_ASC  ("Stock (croissant)"),
    STOCK_DESC ("Stock (décroissant)"),
    PRICE_ASC  ("Prix (croissant)"),
    PRICE_DESC ("Prix (décroissant)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel : ProductViewModel = viewModel(),
    modifier  : Modifier = Modifier,
    onFullScreenChange : (Boolean) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
    val products    by viewModel.products.collectAsState()
    val isLoading   by viewModel.isLoading.collectAsState()
    val error       by viewModel.error.collectAsState()
    val categories  by viewModel.categories.collectAsState()
        var longPressProduct by remember { mutableStateOf<Product?>(null) }

    var showEditScreen       by remember { mutableStateOf<Product?>(null) }
    var search               by remember { mutableStateOf("") }
    var selectedProduct      by remember { mutableStateOf<Product?>(null) }
    var showDeleteDialog     by remember { mutableStateOf<Product?>(null) }
    var showAddScreen        by remember { mutableStateOf(false) }
    var showCategoriesScreen by remember { mutableStateOf(false) }
    var showScanner          by remember { mutableStateOf(false) }
    var isGridView           by remember { mutableStateOf(false) }
    var selectedCategory     by remember { mutableStateOf<String?>(null) }
    var sortOption           by remember { mutableStateOf(SortOption.NAME_ASC) }
    var showSortSheet        by remember { mutableStateOf(false) }
    val sheetState           = rememberModalBottomSheetState()

        LaunchedEffect(Unit) { viewModel.loadProducts() }
    if (showAddScreen) {
        onFullScreenChange(true)
        BackHandler { showAddScreen = false; onFullScreenChange(false) }
        ProductFormScreen(
            onBack  = { showAddScreen = false; onFullScreenChange(false) },
            onSaved = { showAddScreen = false; onFullScreenChange(false); viewModel.loadProducts() }
        )
        return
    }

    if (showCategoriesScreen) {
        BackHandler {
            showCategoriesScreen = false
            viewModel.loadCategories()
        }
        CategoriesScreen()
        return
    }

    if (showScanner) {
        onFullScreenChange(true)
        BackHandler { showScanner = false; onFullScreenChange(false) }
        BarcodeScannerScreen(
            onBarcodeScanned = { code ->
                search = code
                showScanner = false
                onFullScreenChange(false)
            },
            onClose = { showScanner = false; onFullScreenChange(false) }
        )
        return
    }

    val tokens   = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    val filtered = products.filter { product ->
        (tokens.isEmpty() || tokens.all { token ->
            product.name.contains(token, ignoreCase = true) ||
            (product.barcode?.contains(token, ignoreCase = true) == true)
        }) &&
        (selectedCategory == null || product.category_name == selectedCategory)
    }
    val sorted = when (sortOption) {
        SortOption.NAME_ASC   -> filtered.sortedBy { it.name.lowercase() }
        SortOption.NAME_DESC  -> filtered.sortedByDescending { it.name.lowercase() }
        SortOption.STOCK_ASC  -> filtered.sortedBy { it.stock }
        SortOption.STOCK_DESC -> filtered.sortedByDescending { it.stock }
        SortOption.PRICE_ASC  -> filtered.sortedBy { it.selling_price }
        SortOption.PRICE_DESC -> filtered.sortedByDescending { it.selling_price }
    }

    showDeleteDialog?.let { product ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Supprimer le produit") },
            text  = { Text("Voulez-vous supprimer \"${product.name}\" ?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProduct(product.id)
                    showDeleteDialog = null
                    selectedProduct  = null
                }) { Text("Supprimer", color = DestructiveRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Annuler") }
            }
        )
    }

        longPressProduct?.let { product ->
            AlertDialog(
                onDismissRequest = { longPressProduct = null },
                title = { Text(product.name, fontWeight = FontWeight.Bold) },
                confirmButton = {},
                dismissButton = {},
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White,
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BlueLight)
                                .clickable {
                                    showEditScreen   = product
                                    longPressProduct = null
                                }
                                .padding(14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                            Text("Modifier", fontSize = 14.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(RedLight)
                                .clickable {
                                    showDeleteDialog = product
                                    longPressProduct = null
                                }
                                .padding(14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = DestructiveRed, modifier = Modifier.size(20.dp))
                            Text("Supprimer", fontSize = 14.sp, color = DestructiveRed, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            )
        }

    showEditScreen?.let { product ->
        onFullScreenChange(true)
        BackHandler { showEditScreen = null; onFullScreenChange(false) }
        ProductFormScreen(
            product = product,
            onBack  = { showEditScreen = null; onFullScreenChange(false) },
            onSaved = { showEditScreen = null; selectedProduct = null; onFullScreenChange(false); viewModel.loadProducts() }
        )
        return
    }

    selectedProduct?.let { product ->
        BackHandler { showEditScreen = null }
        ProductDetailScreen(
            product  = product,
            onBack   = { selectedProduct = null },
            onDelete = { showDeleteDialog = product },
            onEdit = {
                showEditScreen  = product
                selectedProduct = null
            }        )
        return
    }

    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            sheetState       = sheetState,
            containerColor   = Color.White
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
                Text("Trier par", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                SortOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sortOption == option) BlueLight else Color.Transparent)
                            .clickable { sortOption = option; showSortSheet = false }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text       = option.label,
                            fontSize   = 14.sp,
                            color      = if (sortOption == option) PrimaryBlue else TextPrimary,
                            fontWeight = if (sortOption == option) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (sortOption == option) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ── Header ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Produits", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Toggle List/Grid ──
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MutedGray)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isGridView) PrimaryBlue else Color.Transparent)
                            .clickable { isGridView = false }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ViewList,
                            contentDescription = "Liste",
                            tint = if (!isGridView) Color.White else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isGridView) PrimaryBlue else Color.Transparent)
                            .clickable { isGridView = true }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = "Grille",
                            tint = if (isGridView) Color.White else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // ── Sort button ──
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MutedGray)
                        .clickable { showSortSheet = true }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Sort,
                        contentDescription = "Trier",
                        tint = if (sortOption != SortOption.NAME_ASC) PrimaryBlue else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // ── Add button ──
                FloatingActionButton(
                    onClick = { showAddScreen = true },
                    containerColor = PrimaryBlue,
                    contentColor = Color.White,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter")
                }
            }
        }

        // ── Search bar ──
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Rechercher par nom ou code-barres…", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = { showScanner = true }) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "Scanner un code-barres",
                            tint = PrimaryBlue
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderGray,
                focusedBorderColor = PrimaryBlue,
            )
        )

        // ── Category filter row ──
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selectedCategory == null) PrimaryBlue else Color.Transparent)
                        .border(
                            1.dp,
                            if (selectedCategory == null) PrimaryBlue else BorderGray,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedCategory = null }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Tous",
                        color = if (selectedCategory == null) Color.White else TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
            items(categories) { cat ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selectedCategory == cat.name) PrimaryBlue else Color.Transparent)
                        .border(
                            1.dp,
                            if (selectedCategory == cat.name) PrimaryBlue else BorderGray,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            selectedCategory = if (selectedCategory == cat.name) null else cat.name
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        cat.name,
                        color = if (selectedCategory == cat.name) Color.White else TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MutedGray)
                        .clickable { showCategoriesScreen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Gérer catégories",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Text(
            text = "${sorted.size} produit(s)",
            fontSize = 12.sp,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
            return
        }

        error?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = it, color = DestructiveRed)
            }
            return
        }

        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = PrimaryBlue.copy(alpha = 0.3f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Aucun produit trouvé", color = TextMuted, fontWeight = FontWeight.Medium)
                }
            }
            return
        }

        // ── List View ──
        if (!isGridView) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sorted) { product ->
                    ProductCard(product = product,
                        onClick = { selectedProduct = product },
                        onLongClick = { longPressProduct = product }
                    )
                }
            }
        }

        // ── Grid View ──
        else {
            LazyVerticalGrid(
                columns             = GridCells.Fixed(2),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sorted) { product ->
                    ProductGridCard(product = product,
                        onClick = { selectedProduct = product },
                        onLongClick = { longPressProduct = product })
                }
            }
        }
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
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, if (isLow) RedLight else BorderGray)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier         = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(BlueLight),
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
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary, maxLines = 1,    modifier   = Modifier.basicMarquee()
                )
                Text(
                    "${product.category_name ?: "—"} · ${product.supplier_name ?: "—"}",
                    fontSize = 12.sp,
                    color = TextMuted
                )            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("${product.selling_price} DA", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PrimaryBlue)
                Text("${product.stock} ${product.unit_type}", fontSize = 12.sp,
                    fontWeight = if (isLow) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (isLow) DestructiveRed else TextMuted)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun ProductGridCard(product: Product, onClick: () -> Unit,    onLongClick : () -> Unit = {}
) {
    val isLow = product.stock < product.min_stock

    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onClick() },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, if (isLow) RedLight else BorderGray)
    ) {
        Column {
            // ── Image ──
            Box(
                modifier         = Modifier.fillMaxWidth().height(110.dp).background(Color(0xFFF7F7F8)),
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
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = PrimaryBlue.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                }
                // Stock badge
                if (isLow) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DestructiveRed)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("!", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Info ──
            Column(modifier = Modifier.padding(10.dp)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text("${product.selling_price} DA", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PrimaryBlue)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "${product.stock} ${product.unit_type}",
                        fontSize   = 11.sp,
                        fontWeight = if (isLow) FontWeight.SemiBold else FontWeight.Normal,
                        color      = if (isLow) DestructiveRed else TextMuted
                    )
                    Text(
                        text     = product.category_name ?: "—",
                        fontSize = 10.sp,
                        color    = TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
