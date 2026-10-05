package com.distrigo.app.ui.pertes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.distrigo.app.data.model.Category
import com.distrigo.app.data.model.Marque
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.SousCategorie
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.ui.common.ActiveProductFilterChips
import com.distrigo.app.ui.common.CartStatusLine
import com.distrigo.app.ui.common.CartStatusTone
import com.distrigo.app.ui.common.DsCompactSearchAction
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.FitText
import com.distrigo.app.ui.common.ProductCountAndFilters
import com.distrigo.app.ui.common.SelectionCartCard
import com.distrigo.app.ui.common.formatQty
import com.distrigo.app.ui.common.productFilterChips
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.inventory.InventoryDateField
import com.distrigo.app.ui.products.ProductCard
import com.distrigo.app.ui.purchases.PurchaseProductFilterSheet
import com.distrigo.app.ui.scanner.BarcodeScannerScreen
import kotlinx.coroutines.launch

/** The dépôt's share of a product's stock: what a dépôt perte is taken from. */
private val Product.depotStock: Double get() = stock - camion_stock

/**
 * Nouvelle perte: the products in stock, the way the Inventaire lists them — Produits' search bar
 * and scanner, the count and "Filtres", Produits' rows with the dépôt stock on the right. A tap, or a
 * scanned code (which skips the list), opens [PerteDialog]; once saved the product leaves the list for
 * the selection the bar at the bottom opens.
 */
@Composable
fun NewPerteListScreen(
    viewModel      : NewPerteViewModel,
    categories     : List<Category>,
    sousCategories : List<SousCategorie>,
    marques        : List<Marque>,
    suppliers      : List<Supplier>,
    onBack         : () -> Unit,
    onOpenCart     : () -> Unit,
) {
    val money = LocalMoneyFormatter.current
    val products = viewModel.productList.items.collectAsLazyPagingItems()
    val count by viewModel.productList.count.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val types by viewModel.types.collectAsState()
    val scope = rememberCoroutineScope()
    var showScanner by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Product?>(null) }
    var message by remember { mutableStateOf("") }

    if (showScanner) {
        BackHandler { showScanner = false }
        BarcodeScannerScreen(
            onBarcodeScanned = { code ->
                showScanner = false
                scope.launch {
                    val product = viewModel.productByBarcode(code)
                    if (product == null) message = "Aucun produit trouvé pour ce code-barres"
                    else { message = ""; editing = product }
                }
            },
            onClose = { showScanner = false }
        )
        return
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = "Nouvelle perte", subtitle = "Produits perdus au dépôt", leading = DsTopBarLeading.Back(onBack))
        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

        DsCompactSearchField(
            value         = viewModel.search,
            onValueChange = { viewModel.search = it },
            placeholder   = "Rechercher un produit",
            modifier      = Modifier.padding(horizontal = DsSpacing.lg).padding(top = DsSpacing.md)
        ) {
            DsCompactSearchAction(
                icon = Icons.Default.QrCodeScanner, contentDescription = "Scanner un code-barres",
                tint = DsColors.Primary, onClick = { showScanner = true }
            )
        }
        ProductCountAndFilters(count = count, filtersActive = viewModel.filters.isActive, onOpenFilters = { showFilters = true })
        ActiveProductFilterChips(
            productFilterChips(viewModel.filters, categories, sousCategories, marques, suppliers, "Prix de vente", money),
            onChange = { viewModel.filters = it }
        )
        if (message.isNotEmpty()) {
            Text(message, fontSize = DsTextSize.bodySmall, color = DsColors.Danger, modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xs))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (products.itemCount == 0) {
                Text(
                    "Aucun produit ne correspond.", fontSize = DsTextSize.body, color = DsColors.TextSecondary,
                    textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(DsSpacing.xl)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs, bottom = DsSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(count = products.itemCount, key = products.itemKey { it.id }) { index ->
                        val product = products[index] ?: return@items
                        ProductCard(product = product, onClick = { message = ""; editing = product }, stock = product.depotStock)
                    }
                }
            }
        }

        val tint = if (cart.isNotEmpty()) DsColors.Primary else DsColors.TextTertiary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.xl, vertical = DsSpacing.sm)
                .clip(DsShapes.large)
                .background(if (cart.isNotEmpty()) DsColors.PrimaryLight else DsColors.SurfaceSunken)
                .clickable(enabled = cart.isNotEmpty(), onClick = onOpenCart)
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            Box(Modifier.size(20.dp).clip(DsShapes.pill).background(tint), contentAlignment = Alignment.Center) {
                Text("${cart.size}", color = Color.White, fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold)
            }
            Text("Ma sélection", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = tint)
            Spacer(Modifier.weight(1f))
            Text(money.da(cart.sumOf { it.value }), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = tint)
        }
    }

    editing?.let { product ->
        val existing = viewModel.inCart(product.id)
        PerteDialog(
            productName = product.name, unit = product.unit_type,
            stock = product.depotStock, cap = viewModel.capFor(product),
            types = types, initialType = existing?.typeId, initialQty = existing?.quantity, initialMotif = existing?.motif,
            onSave = { typeId, qty, motif -> viewModel.put(product, typeId, qty, motif); editing = null },
            onAddType = viewModel::addType,
            onDismiss = { editing = null }
        )
    }

    if (showFilters) {
        PurchaseProductFilterSheet(
            filters = viewModel.filters, categories = categories, sousCategories = sousCategories,
            marques = marques, suppliers = suppliers, resultCount = count ?: 0,
            onChange = { viewModel.filters = it }, onDismiss = { showFilters = false },
            priceLabel = "Prix de vente", showStockLevel = false,
        )
    }
}

/**
 * The new perte's selection, as the Inventaire's and the sales' carts show theirs: a card per product
 * — its type and quantity, its value — unfolding to change it (the same dialog) or remove it, which
 * puts the product back on the list.
 */
@Composable
fun NewPerteCartScreen(viewModel: NewPerteViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val cart by viewModel.cart.collectAsState()
    val types by viewModel.types.collectAsState()
    var expandedId by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<PerteCartLine?>(null) }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = "Ma sélection", subtitle = "${cart.size} produit(s)", leading = DsTopBarLeading.Back(onBack))
        Spacer(Modifier.height(DsSpacing.sm))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (cart.isEmpty()) {
                Text("Aucun produit", color = DsColors.TextSecondary, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
                ) {
                    items(cart, key = { it.product.id }) { line ->
                        val expanded = expandedId == line.product.id
                        SelectionCartCard(
                            avatarIcon      = Icons.Default.RemoveShoppingCart,
                            title           = line.product.name,
                            metaLine        = "${formatQty(line.quantity)} ${line.product.unit_type} · ${line.typeName}" + (line.motif?.let { " · $it" } ?: ""),
                            totalPriceLabel = money.da(line.value),
                            isExpanded      = expanded,
                            onToggleExpand  = { expandedId = if (expanded) null else line.product.id },
                            statusLine      = {
                                CartStatusLine(
                                    icon = Icons.Default.Inventory2,
                                    text = "Dépôt : ${formatQty(line.product.depotStock)} → ${formatQty(line.product.depotStock - line.quantity)} ${line.product.unit_type}",
                                    tone = if (line.product.depotStock - line.quantity < 0) CartStatusTone.WARNING else CartStatusTone.NEUTRAL
                                )
                            },
                            expandedContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { viewModel.remove(line.product.id); expandedId = null }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Retirer", color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Button(
                                        onClick = { editing = line }, shape = DsShapes.medium,
                                        colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(DsSpacing.xs))
                                        Text("Modifier")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(52.dp), shape = DsShapes.medium) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text("Retour", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onNext, enabled = cart.isNotEmpty(),
                modifier = Modifier.weight(1f).height(52.dp), shape = DsShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
            ) { Text("Suivant →", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold) }
        }
    }

    editing?.let { line ->
        PerteDialog(
            productName = line.product.name, unit = line.product.unit_type,
            stock = line.product.depotStock, cap = viewModel.capFor(line.product),
            types = types, initialType = line.typeId, initialQty = line.quantity, initialMotif = line.motif,
            onSave = { typeId, qty, motif -> viewModel.put(line.product, typeId, qty, motif); editing = null; expandedId = null },
            onAddType = viewModel::addType,
            onDismiss = { editing = null }
        )
    }
}

/**
 * Résumé de la perte: its date — "Aujourd'hui" unless another day is picked — what it adds up to, and
 * every product lost. "Confirmer" records them all at once; then the summary stays, confirmed.
 */
@Composable
fun NewPerteSummaryScreen(viewModel: NewPerteViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val live by viewModel.cart.collectAsState()
    // What was confirmed stays on screen after the selection is cleared.
    var confirmed by remember { mutableStateOf<List<PerteCartLine>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val lines = confirmed ?: live
    BackHandler { if (confirmed != null) onDone() else onBack() }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(
            title = "Résumé de la perte",
            leading = DsTopBarLeading.Back { if (confirmed != null) onDone() else onBack() }
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            item { InventoryDateField(date = viewModel.date, enabled = confirmed == null, onDateChange = { viewModel.date = it }) }
            item {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    StatCard(Icons.Default.Inventory2, "${lines.size}", "Produits perdus", DsColors.Primary, Modifier.weight(1f))
                    StatCard(Icons.Default.Receipt, money.da(lines.sumOf { it.value }), "Valeur totale", DsColors.Danger, Modifier.weight(1f))
                }
            }
            if (confirmed != null) {
                item {
                    Surface(shape = DsShapes.medium, color = DsColors.SuccessLight, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DsColors.Success)
                            Spacer(Modifier.width(DsSpacing.sm))
                            Text("Perte enregistrée · le stock a été mis à jour", fontSize = DsTextSize.bodySmall,
                                fontWeight = FontWeight.SemiBold, color = DsColors.Success)
                        }
                    }
                }
            }
            items(lines, key = { it.product.id }) { line ->
                Row(
                    Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.SurfaceMuted).padding(DsSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(line.product.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                        Text("${line.typeName} · ${formatQty(line.quantity)} ${line.product.unit_type}" + (line.motif?.let { " · $it" } ?: ""), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                    Text(money.da(line.value), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.Danger)
                }
            }
            if (error.isNotEmpty()) item { Text(error, color = DsColors.Danger, fontSize = DsTextSize.bodySmall) }
        }
        Button(
            onClick = {
                if (confirmed != null) onDone() else {
                    saving = true; error = ""
                    val snapshot = live
                    viewModel.confirm(
                        onSuccess = { saving = false; confirmed = snapshot },
                        onError = { saving = false; error = it }
                    )
                }
            },
            enabled = !saving && lines.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
            shape = DsShapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            if (saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            else Text(if (confirmed != null) "Retour à l'historique" else "Confirmer", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, value: String, label: String, color: Color, modifier: Modifier) {
    Surface(shape = DsShapes.medium, color = color.copy(alpha = 0.08f), modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(DsSpacing.md)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(DsSpacing.xs))
            FitText(value, fontSize = DsTextSize.title, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
    }
}
