package com.distrigo.app.ui.tournees

import com.distrigo.app.ui.common.formatQty
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.navigation.ChargementNavHost
import com.distrigo.app.ui.chargements.ChargementBrouillonsScreen
import com.distrigo.app.ui.chargements.ChargementBrouillonsSheet
import com.distrigo.app.ui.chargements.ChargementProduitScreen
import com.distrigo.app.ui.chargements.ChargementViewModel
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.basicMarquee
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.ui.common.EntityImage
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.format.LocalMoneyFormatter
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import com.distrigo.app.ui.common.DsCompactSearchAction
import com.distrigo.app.ui.common.ActiveProductFilterChips
import com.distrigo.app.ui.common.productFilterChips
import com.distrigo.app.ui.products.ProductCard
import com.distrigo.app.ui.products.ProductGridCard
import com.distrigo.app.ui.products.ProductListControls
import com.distrigo.app.ui.products.ProductSortSheet
import com.distrigo.app.ui.products.SortOption
import com.distrigo.app.ui.purchases.PurchaseProductFilterSheet
import com.distrigo.app.ui.scanner.BarcodeScannerScreen
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StockCamionScreen(
    onBack             : () -> Unit,
    onFullScreenChange : (Boolean) -> Unit = {},
    productViewModel   : com.distrigo.app.ui.products.ProductViewModel =
        hiltViewModel()
) {
    // What the camion carries, paged, searched, filtered and sorted in the database
    // (ProductViewModel.camionProducts) — the Produits controls, on the camion's list.
    val money = LocalMoneyFormatter.current
    val camionProducts = productViewModel.camionProducts.items.collectAsLazyPagingItems()
    val camionCount by productViewModel.camionProducts.count.collectAsState()
    val search  = productViewModel.camionSearch
    val filters = productViewModel.camionFilters
    val categories     by productViewModel.categories.collectAsState()
    val sousCategories by productViewModel.sousCategories.collectAsState()
    val marques        by productViewModel.marques.collectAsState()
    val suppliers      by productViewModel.suppliers.collectAsState()
    var showScanner     by remember { mutableStateOf(false) }
    var showSortSheet   by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    var showNewChargement by remember { mutableStateOf(false) }
    var editingProduct    by remember { mutableStateOf<Product?>(null) }
    var longPressProduct  by remember { mutableStateOf<Product?>(null) }
    var showDraftsSheet   by remember { mutableStateOf(false) }
    var showBrouillons    by remember { mutableStateOf(false) }
    var resumingDraftId   by remember { mutableStateOf<Int?>(null) }

    val chargementViewModel: ChargementViewModel = hiltViewModel()
    val drafts by chargementViewModel.drafts.collectAsState()


    // ── New Chargement Screen (multi-produits) ──
    if (showNewChargement) {
        onFullScreenChange(true)
        ChargementNavHost(
            draftId = resumingDraftId,
            onBack  = {
                showNewChargement = false
                resumingDraftId   = null
                onFullScreenChange(false)
            },
            onSaved = {
                showNewChargement = false
                resumingDraftId   = null
                onFullScreenChange(false)
            }
        )
        return
    }

    // ── Brouillons: the whole list, its own screen ──
    if (showBrouillons) {
        onFullScreenChange(true)
        ChargementBrouillonsScreen(
            viewModel = chargementViewModel,
            onBack    = { showBrouillons = false; onFullScreenChange(false) },
            // Straight through, with no gate: a chargement draft edits no committed record, so
            // there is nothing to check it against before opening it.
            onResume  = { draft ->
                showBrouillons  = false
                resumingDraftId = draft.id
                showNewChargement = true
            }
        )
        return
    }

    // ── Modifier: one product, one card, no wizard ──
    //
    // This used to mount the whole ChargementNavHost for a single row. That host starts at the
    // product catalogue and jumped straight to the cart with popUpTo(products, inclusive = false),
    // deliberately leaving the catalogue on the back stack — so changing one number meant two
    // screens, and Back landed on a list nobody had asked for. ChargementProduitScreen is the card
    // on its own, and it guards Back when there is unsaved work.
    editingProduct?.let { product ->
        onFullScreenChange(true)
        ChargementProduitScreen(
            product = product,
            onBack  = { editingProduct = null; onFullScreenChange(false) },
            onSaved = {
                editingProduct = null
                onFullScreenChange(false)
            }
        )
        return
    }

    // ── Barcode: what it reads goes in the search, which matches barcodes as well as names ──
    if (showScanner) {
        onFullScreenChange(true)
        BarcodeScannerScreen(
            onBarcodeScanned = { code ->
                productViewModel.camionSearch = code
                showScanner = false
                onFullScreenChange(false)
            },
            onClose = { showScanner = false; onFullScreenChange(false) }
        )
        return
    }

    // ── Long Press Dialog ──
    longPressProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { longPressProduct = null },
            title = { Text(product.name, maxLines = 1) },
            confirmButton = {},
            dismissButton = {},
            shape = DsShapes.medium,
            containerColor = DsColors.Surface,
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.medium)
                        .background(DsColors.PrimaryLight)
                        .combinedClickable(
                            onClick = {
                                editingProduct  = product
                                longPressProduct = null
                            }
                        )
                        .padding(DsSpacing.md),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                    Text("Modifier", fontSize = DsTextSize.body, color = DsColors.Primary, fontWeight = FontWeight.Medium)
                }
            },
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DsColors.Surface)
        ) {
            DsTopAppBar(
                title   = "Stock Camion",
                leading = DsTopBarLeading.Back(onBack)
            )
            Spacer(Modifier.height(DsSpacing.md))

            // ── Search: a name, or a barcode typed or scanned ──
            DsCompactSearchField(
                value         = search,
                onValueChange = { productViewModel.camionSearch = it },
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

            // Brouillons live beside the list they belong to, as they do on the other three
            // screens. Absent when there are none, so the row costs nothing in the ordinary case.
            if (drafts.isNotEmpty()) {
                Spacer(Modifier.height(DsSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(DsShapes.medium)
                            .background(DsColors.PrimaryLight)
                            .clickable { showBrouillons = true }
                            .padding(horizontal = DsSpacing.sm, vertical = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(14.dp))
                        Text(
                            "Brouillons (${drafts.size})",
                            fontSize   = DsTextSize.caption,
                            fontWeight = FontWeight.SemiBold,
                            color      = DsColors.Primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(DsSpacing.sm))

            // ── Count · list/grid · Trier · Filtres: the Produits row ──
            ProductListControls(
                count         = camionCount,
                isGrid        = productViewModel.camionGridView,
                onGridChange  = { productViewModel.camionGridView = it },
                sortActive    = productViewModel.camionSort != SortOption.NAME_ASC,
                onSort        = { showSortSheet = true },
                filtersActive = filters.isActive,
                onFilters     = { showFilterSheet = true }
            )
            Spacer(Modifier.height(DsSpacing.sm))
            ActiveProductFilterChips(
                productFilterChips(filters, categories, sousCategories, marques, suppliers, "Prix de vente", money),
                onChange = { productViewModel.camionFilters = it }
            )

            when {
                camionProducts.itemCount == 0 && camionProducts.loadState.refresh is LoadState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DsColors.Primary)
                    }
                }
                camionProducts.itemCount == 0 -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                tint     = DsColors.TextTertiary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(DsSpacing.sm))
                            Text(
                                // An empty camion and a search that found nothing in it are two answers.
                                if (search.isBlank() && !filters.isActive) "Aucun produit dans le camion" else "Aucun produit trouvé",
                                color = DsColors.TextSecondary, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                else -> {
                    val productKey = camionProducts.itemKey { it.id }
                    // Lets the last row scroll clear of the raised FAB (clearance + 56dp FAB).
                    val bottom = DsSpacing.fabBottomClearance + 56.dp
                    // Produits' cards, showing what the camion carries. No "low" warning: a minimum
                    // stock is the dépôt's threshold, not the camion's. A tap opens the same
                    // "Modifier" a long press does — the card's chevron promises something.
                    if (!productViewModel.camionGridView) {
                        LazyColumn(
                            contentPadding      = PaddingValues(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs, bottom = bottom),
                            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                        ) {
                            items(count = camionProducts.itemCount, key = productKey) { index ->
                                val product = camionProducts[index] ?: return@items
                                ProductCard(
                                    product     = product,
                                    onClick     = { longPressProduct = product },
                                    onLongClick = { longPressProduct = product },
                                    stock       = product.camion_stock,
                                    lowStock    = false
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns               = GridCells.Fixed(2),
                            contentPadding        = PaddingValues(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs, bottom = bottom),
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                            verticalArrangement   = Arrangement.spacedBy(DsSpacing.sm)
                        ) {
                            items(count = camionProducts.itemCount, key = productKey) { index ->
                                val product = camionProducts[index] ?: return@items
                                ProductGridCard(
                                    product     = product,
                                    onClick     = { longPressProduct = product },
                                    onLongClick = { longPressProduct = product },
                                    stock       = product.camion_stock,
                                    lowStock    = false
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showSortSheet) {
            ProductSortSheet(
                selected  = productViewModel.camionSort,
                onSelect  = { productViewModel.camionSort = it },
                onDismiss = { showSortSheet = false }
            )
        }
        if (showFilterSheet) {
            PurchaseProductFilterSheet(
                filters        = filters,
                categories     = categories,
                sousCategories = sousCategories,
                marques        = marques,
                suppliers      = suppliers,
                resultCount    = camionCount ?: 0,
                onChange       = { productViewModel.camionFilters = it },
                onDismiss      = { showFilterSheet = false },
                priceLabel     = "Prix de vente",
                // The camion's list: dépôt stock bands would say nothing here.
                showStockLevel = false
            )
        }

        // ── FAB: ajouter plusieurs produits ──
        //
        // With Brouillons waiting, the FAB opens them rather than starting a fifth one straight
        // over the top — the same rule Achats and Dépôt Vente follow. With none, it does what it
        // always did.
        FloatingActionButton(
            onClick         = {
                if (drafts.isNotEmpty()) showDraftsSheet = true else showNewChargement = true
            },
            containerColor  = DsColors.Primary,
            contentColor    = Color.White,
            modifier        = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = DsSpacing.lg, bottom = DsSpacing.fabBottomClearance)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Ajouter des produits")
        }

        if (showDraftsSheet) {
            ChargementBrouillonsSheet(
                drafts     = drafts,
                onResume   = { draft ->
                    showDraftsSheet   = false
                    resumingDraftId   = draft.id
                    showNewChargement = true
                },
                onStartNew = {
                    showDraftsSheet   = false
                    resumingDraftId   = null
                    showNewChargement = true
                },
                onSeeAll   = { showDraftsSheet = false; showBrouillons = true },
                onDismiss  = { showDraftsSheet = false }
            )
        }
    }
}
