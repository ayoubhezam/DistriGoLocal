package com.distrigo.app.ui.products

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.PriceMovement
import com.distrigo.app.data.model.PriceMovementKind
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.ProductImage
import com.distrigo.app.data.model.StockMovement
import com.distrigo.app.data.time.BusinessDates
import com.distrigo.app.ui.common.EntityImage
import com.distrigo.app.ui.common.rememberPhotoPicker
import com.distrigo.app.ui.common.bidiIsolate
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.mouvements.movementTypeDisplay
import com.distrigo.app.ui.suppliers.formatDZD
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** The margin's colour: neither the purchase price's green nor the selling price's blue. */
private val MarginColor = Color(0xFF7C3AED)

/** Price-history rows shown before « Voir tout ». */
private const val HISTORY_PREVIEW = 3

@Composable
fun ProductDetailScreen(
    product          : Product,
    onBack           : () -> Unit,
    onDelete         : () -> Unit,
    onEdit           : () -> Unit,
    onViewMovements  : () -> Unit,
    onOpenMovement   : (Int) -> Unit,
    onPriceHistory   : () -> Unit,
    viewModel: ProductViewModel = hiltViewModel()
) {
    // Already live: the route observes this one product (ProductViewModel.observeProduct).
    val currentProduct = product

    LaunchedEffect(product.id) {
        viewModel.loadPriceMovements(product.id)
    }
    val priceMovements by viewModel.priceMovements.collectAsState()

    // Reloaded when the stock moves, so a sale made elsewhere shows here when the user comes back.
    LaunchedEffect(product.id, currentProduct.stock, currentProduct.camion_stock) {
        viewModel.loadRecentMovements(product.id)
    }
    val recentMovements by viewModel.recentMovements.collectAsState()

    // ── Gallery ──
    // The photos are observed from their own table rather than read off the product, so adding,
    // deleting or re-covering one lands on screen the moment the transaction commits.
    val images by viewModel.productImages.collectAsState()
    LaunchedEffect(product.id) { viewModel.observeGalleryFor(product.id) }

    var manageMode    by remember { mutableStateOf(false) }
    var viewerIndex   by remember { mutableStateOf<Int?>(null) }
    var pendingDelete by remember { mutableStateOf<ProductImage?>(null) }
    var galleryError  by remember { mutableStateOf<String?>(null) }

    // Kept across a trip to the movements or the form, so Back returns to the tab that was open.
    var selectedTab   by rememberSaveable { mutableIntStateOf(0) }
    var showStockInfo by remember { mutableStateOf(false) }

    // Camera or gallery, the same choice the form offers — see PhotoPicker.
    val photoPicker = rememberPhotoPicker(
        onPicked = { ref -> viewModel.addProductImage(product.id, ref) { message -> galleryError = message } },
        onError  = { message -> galleryError = message }
    )

    var showDeleteDialog by remember { mutableStateOf(false) }

    // Back unwinds one layer at a time: the viewer, then manage mode, then the screen. Without
    // this, leaving manage mode needed a deliberate tap and Back would drop the whole screen.
    BackHandler(enabled = viewerIndex == null && manageMode) { manageMode = false }
    BackHandler(enabled = viewerIndex == null && !manageMode) { onBack() }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer le produit ?") },
            text  = {
                // Naming the product is the point of the confirmation, and it is what the second
                // dialog behind this one used to contribute before it was removed as redundant.
                // Isolated because most names in this catalogue are Arabic and this is a sentence.
                val name = bidiIsolate(currentProduct.name)
                if (currentProduct.supplier_name != null) {
                    Text("« $name » est lié au fournisseur ${bidiIsolate(currentProduct.supplier_name)}. " +
                         "Il en sera dissocié puis ira dans la corbeille (Paramètres › Corbeille), d'où vous pourrez le restaurer.")
                } else {
                    Text("« $name » ira dans la corbeille (Paramètres › Corbeille), d'où vous pourrez le restaurer.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annuler")
                }
            },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    if (showStockInfo) {
        AlertDialog(
            onDismissRequest = { showStockInfo = false },
            confirmButton = {
                TextButton(onClick = { showStockInfo = false }) { Text("Compris") }
            },
            title = { Text("Informations de stock", fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StockInfoExplainRow("Stock total", "Quantité totale du produit, dépôt et camion confondus.")
                    StockInfoExplainRow("Dépôt", "Quantité disponible en dépôt (stock total − stock camion).")
                    StockInfoExplainRow("Camion", "Quantité actuellement chargée dans le camion de tournée.")
                    StockInfoExplainRow("Seuil minimum", "En dessous de cette quantité, le produit est considéré en stock faible.")
                }
            },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ──
        // Delete and edit used to sit on a second row under the title; on the bar they ride
        // beside it, the way the client and supplier details already did.
        DsTopAppBar(
            title   = "Détails du produit",
            leading = DsTopBarLeading.Back(onBack)
        ) {
            IconButton(
                onClick  = { showDeleteDialog = true },
                modifier = Modifier
                    .clip(DsShapes.medium)
                    .background(DsColors.DangerLight)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = DsColors.Danger)
            }
            Spacer(Modifier.width(DsSpacing.sm))
            IconButton(
                onClick  = onEdit,
                modifier = Modifier
                    .clip(DsShapes.medium)
                    .background(DsColors.PrimaryLight)
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = DsColors.Primary)
            }
            // The tinted square is the button's full 48dp, so it needs the difference to the
            // bar's standard end margin — matching the client and supplier details.
            Spacer(Modifier.width(DsSpacing.md))
        }

        // ── Galerie photos ──
        ProductGallery(
            images        = images,
            manageMode    = manageMode,
            onOpenViewer  = { index -> viewerIndex = index },
            onAddPhoto    = { photoPicker.choose() },
            onDeletePhoto = { pendingDelete = it },
            onSetCover    = { viewModel.setPrimaryProductImage(it.id) { m -> galleryError = m } },
            modifier      = Modifier.padding(horizontal = 16.dp)
        )

        // The mode toggle sits with the gallery rather than in the app bar: a third action up
        // there would squeeze the title, and the control belongs next to what it changes.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = DsSpacing.sm),
            horizontalArrangement = Arrangement.End,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(DsShapes.pill)
                    .background(if (manageMode) DsColors.Primary else DsColors.PrimaryLight)
                    .clickable { manageMode = !manageMode }
                    .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (manageMode) Icons.Default.Check else Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint     = if (manageMode) DsColors.Surface else DsColors.Primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(DsSpacing.xs))
                Text(
                    if (manageMode) "Terminer" else "Gérer les photos",
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (manageMode) DsColors.Surface else DsColors.Primary
                )
            }
        }

        // ── Onglets ──
        // Both tabs live in this one scrolling column, under the gallery, so they switch in place
        // rather than paging: a pager inside a vertical scroll would need a fixed height.
        DetailTabs(
            titles   = listOf("Informations produit", "Stock & mouvements"),
            selected = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Column(
            modifier            = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    ProductInfoCard(currentProduct)
                    PriceHistoryCard(priceMovements, onSeeAll = onPriceHistory)
                }
                else -> {
                    CurrentStockCard(
                        product    = currentProduct,
                        onInfo     = { showStockInfo = true },
                        onEditMin  = onEdit
                    )
                    RecentMovementsCard(
                        movements  = recentMovements,
                        unit       = currentProduct.unit_type,
                        onSeeAll   = onViewMovements,
                        onOpen     = onOpenMovement
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    // Emitted after the scrolling Column: siblings are stacked by the host in emission order, so
    // anything meant to sit over the screen has to come last.

    viewerIndex?.let { index ->
        ProductImageViewer(
            images       = images,
            initialIndex = index,
            onClose      = { viewerIndex = null }
        )
    }

    pendingDelete?.let { image ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Supprimer cette photo ?") },
            text  = {
                // The thumbnail is the point of the confirmation: a strip of near-identical
                // product shots gives the user nothing else to check the choice against.
                Column {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(DsShapes.medium)
                            .background(DsColors.SurfaceMuted),
                        contentAlignment = Alignment.Center
                    ) {
                        EntityImage(
                            ref                = image.ref,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize().clip(DsShapes.medium)
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = DsColors.TextTertiary)
                        }
                    }
                    Spacer(Modifier.height(DsSpacing.md))
                    Text(
                        if (image.isCover && images.size > 1)
                            "C'est la photo principale. La suivante prendra sa place."
                        else
                            "Cette photo sera retirée du produit."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProductImage(image.id) { m -> galleryError = m }
                    // The viewer cannot stay open over a gallery that may now be empty.
                    if (images.size <= 1) viewerIndex = null
                    pendingDelete = null
                }) {
                    Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Annuler") }
            },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    galleryError?.let { message ->
        AlertDialog(
            onDismissRequest = { galleryError = null },
            title   = { Text("Photo non ajoutée") },
            text    = { Text(message) },
            confirmButton = {
                TextButton(onClick = { galleryError = null }) { Text("OK") }
            },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

}

// ── Onglets ──

/** Two equal tabs over an underline that slides to the selected one. */
@Composable
private fun DetailTabs(
    titles   : List<String>,
    selected : Int,
    onSelect : (Int) -> Unit,
    modifier : Modifier = Modifier
) {
    val position by animateFloatAsState(selected.toFloat(), label = "tab underline")
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val tabWidth = maxWidth / titles.size
        Row(Modifier.fillMaxWidth()) {
            titles.forEachIndexed { index, title ->
                Text(
                    text       = title,
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (index == selected) DsColors.Primary else DsColors.TextSecondary,
                    textAlign  = TextAlign.Center,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = DsSpacing.md)
                )
            }
        }
        HorizontalDivider(
            modifier  = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
            color     = DsColors.Border,
            thickness = 1.dp
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = tabWidth * position)
                .width(tabWidth)
                .height(2.5.dp)
                .clip(DsShapes.pill)
                .background(DsColors.Primary)
        )
    }
}

// ── Cartes ──

/** A white card with a tinted icon, a title and an optional action at its right. */
@Composable
private fun DetailCard(
    icon    : ImageVector,
    title   : String,
    action  : (@Composable RowScope.() -> Unit)? = null,
    content : @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .padding(DsSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier         = Modifier.size(36.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Text(title, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, modifier = Modifier.weight(1f))
            action?.invoke(this)
        }
        content()
    }
}

/** A text action for a card's header: « Voir tout », « Réduire ». */
@Composable
private fun CardLink(label: String, onClick: () -> Unit, chevron: Boolean = false) {
    Row(
        modifier          = Modifier.clip(DsShapes.pill).clickable(onClick = onClick).padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
        if (chevron) Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
    }
}

/** One labelled line of the information card, with a divider above all but the first. */
@Composable
private fun InfoLine(icon: ImageVector, label: String, first: Boolean = false, value: @Composable () -> Unit) {
    if (!first) HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(DsSpacing.sm))
        Text(label, fontSize = DsTextSize.body, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
        value()
    }
}

@Composable
private fun InfoValue(text: String?) {
    Text(
        text ?: "—",
        fontSize   = DsTextSize.body,
        fontWeight = FontWeight.SemiBold,
        color      = if (text == null) DsColors.TextTertiary else DsColors.TextPrimary,
        textAlign  = TextAlign.End,
        modifier   = Modifier.widthIn(max = 200.dp)
    )
}

@Composable
private fun ProductInfoCard(product: Product) {
    // Margin on the selling price, as the app has always shown it.
    val marginAmount  = product.selling_price - product.purchase_price
    val marginPercent = if (product.selling_price > 0) marginAmount / product.selling_price * 100 else 0.0

    DetailCard(icon = Icons.Default.Inventory2, title = "Informations du produit") {
        Spacer(Modifier.height(DsSpacing.xs))
        InfoLine(Icons.Default.LocalShipping, "Fournisseur", first = true) { InfoValue(product.supplier_name) }
        InfoLine(Icons.Default.Inventory2, "Produit") { InfoValue(product.name) }
        InfoLine(Icons.Default.LocalOffer, "Catégorie") { InfoValue(product.category_name) }
        InfoLine(Icons.Default.GridView, "Sous-catégorie") { InfoValue(product.sous_categorie_name) }
        InfoLine(Icons.Default.WorkspacePremium, "Marque") { InfoValue(product.marque_name) }
        InfoLine(Icons.Default.QrCode, if (product.barcodes.size > 1) "Codes-barres" else "Code-barres") {
            if (product.barcodes.isEmpty()) {
                InfoValue(null)
            } else {
                // The primary first and marked, the others under it.
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    product.barcodes.forEachIndexed { index, code ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (index == 0 && product.barcodes.size > 1) {
                                Text(
                                    "Principal",
                                    fontSize   = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = DsColors.Primary,
                                    modifier   = Modifier
                                        .clip(DsShapes.pill)
                                        .background(DsColors.PrimaryLight)
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                code,
                                fontSize   = DsTextSize.body,
                                fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                                color      = if (index == 0) DsColors.TextPrimary else DsColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(DsSpacing.sm))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DsShapes.medium)
                .background(DsColors.PrimaryLight.copy(alpha = 0.6f))
                .padding(horizontal = DsSpacing.md, vertical = DsSpacing.xs)
        ) {
            PriceLine(Icons.Default.ShoppingCart, "Prix d'achat", "${formatDZD(product.purchase_price)} DA", DsColors.Success)
            PriceLine(Icons.Default.LocalOffer, "Prix de vente", "${formatDZD(product.selling_price)} DA", DsColors.Primary)
            PriceLine(
                Icons.Default.Percent, "Marge",
                "${formatDZD(marginAmount)} DA (${String.format(java.util.Locale.FRANCE, "%.1f", marginPercent)} %)",
                if (marginAmount < 0) DsColors.Danger else MarginColor
            )
        }
    }
}

@Composable
private fun PriceLine(icon: ImageVector, label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(DsShapes.pill).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = DsColors.Surface, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Text(label, fontSize = DsTextSize.body, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun PriceHistoryCard(movements: List<PriceMovement>, onSeeAll: () -> Unit) {
    DetailCard(
        icon   = Icons.Default.History,
        title  = "Historique des prix",
        action = if (movements.isNotEmpty()) {
            { CardLink("Voir tout", onClick = onSeeAll, chevron = true) }
        } else null
    ) {
        if (movements.isEmpty()) {
            EmptyCardText("Aucun historique disponible")
            return@DetailCard
        }
        Spacer(Modifier.height(DsSpacing.xs))
        // The latest few of both kinds; the whole history, with its chart and filters, is a screen
        // of its own behind « Voir tout ».
        movements.take(HISTORY_PREVIEW).forEach { PriceHistoryRow(it) }
    }
}

@Composable
private fun PriceHistoryRow(movement: PriceMovement) {
    val achat = movement.kind == PriceMovementKind.ACHAT
    val delta = movement.delta
    // What the change means for the business: buying cheaper is good, selling dearer is good.
    val good = delta != null && (if (achat) delta < 0 else delta > 0)
    val (tint, background, icon, label) = when {
        delta == null      -> Trend(DsColors.TextSecondary, DsColors.SurfaceSunken, Icons.Default.Remove,
                                    if (achat) "Premier achat" else "Première vente")
        abs(delta) < 0.005 -> Trend(DsColors.TextSecondary, DsColors.SurfaceSunken, Icons.Default.Remove, "Stable")
        else -> Trend(
            if (good) DsColors.Success else DsColors.Danger,
            if (good) DsColors.SuccessLight else DsColors.DangerLight,
            if (delta > 0) Icons.Default.NorthEast else Icons.Default.SouthEast,
            (if (delta > 0) "+" else "−") + "${formatDZD(abs(delta))} DA"
        )
    }
    HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(DsShapes.pill).background(background), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(movement.party, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row {
                Text(
                    movement.kind.label,
                    fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold,
                    color = if (achat) DsColors.Success else DsColors.Primary
                )
                Text(" · ${displayDay(movement.date)}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${formatDZD(movement.unitPrice)} DA", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text(
                label,
                fontSize   = DsTextSize.caption,
                fontWeight = FontWeight.SemiBold,
                color      = tint,
                modifier   = Modifier
                    .padding(top = 2.dp)
                    .clip(DsShapes.pill)
                    .background(background)
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun CurrentStockCard(product: Product, onInfo: () -> Unit, onEditMin: () -> Unit) {
    val camion = product.camion_stock.coerceAtLeast(0.0)
    val depot  = product.stock - camion
    val isLow  = product.stock < product.min_stock

    DetailCard(
        icon   = Icons.Default.Inventory2,
        title  = "Stock actuel",
        action = {
            IconButton(onClick = onInfo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Info, contentDescription = "Informations de stock", tint = DsColors.Primary, modifier = Modifier.size(20.dp))
            }
            if (isLow) {
                Text(
                    "Stock faible",
                    fontSize   = DsTextSize.caption,
                    fontWeight = FontWeight.SemiBold,
                    color      = DsColors.Danger,
                    modifier   = Modifier
                        .clip(DsShapes.pill)
                        .background(DsColors.DangerLight)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = DsSpacing.sm, start = 4.dp)) {
            Text(
                formatQty(product.stock),
                fontSize   = 30.sp,
                fontWeight = FontWeight.Bold,
                color      = if (isLow) DsColors.Danger else DsColors.TextPrimary
            )
            Spacer(Modifier.width(6.dp))
            Text(pluralUnit(product.stock, product.unit_type), fontSize = DsTextSize.body, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        }

        Spacer(Modifier.height(DsSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StockShareBox(
                modifier   = Modifier.weight(1f),
                icon       = Icons.Default.LocalShipping,
                label      = "Stock camion",
                qty        = camion,
                unit       = product.unit_type,
                color      = DsColors.Primary,
                background = DsColors.PrimaryLight
            )
            Text("+", fontSize = DsTextSize.bodyLarge, color = DsColors.Primary, modifier = Modifier.padding(horizontal = 4.dp))
            StockShareBox(
                modifier   = Modifier.weight(1f),
                icon       = Icons.Default.Warehouse,
                label      = "Stock dépôt",
                qty        = depot,
                unit       = product.unit_type,
                color      = DsColors.Success,
                background = DsColors.SuccessLight
            )
        }
        Row(modifier = Modifier.padding(top = DsSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Le stock actuel est la somme du stock camion et du stock dépôt.", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }

        // ── Seuil et péremption ──
        Spacer(Modifier.height(DsSpacing.sm))
        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
        Row(
            modifier          = Modifier.fillMaxWidth().clickable(onClick = onEditMin).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = MarginColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(DsSpacing.sm))
            Text("Seuil minimum", fontSize = DsTextSize.body, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
            Text(
                "${product.min_stock} ${pluralUnit(product.min_stock.toDouble(), product.unit_type)}",
                fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary
            )
            Icon(Icons.Default.ChevronRight, contentDescription = "Modifier le seuil", tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
        if (product.has_expiry == 1) ExpiryLine(product.expiry_date)
    }
}

@Composable
private fun StockShareBox(
    modifier   : Modifier,
    icon       : ImageVector,
    label      : String,
    qty        : Double,
    unit       : String,
    color      : Color,
    background : Color
) {
    Column(
        modifier = modifier
            .clip(DsShapes.large)
            .background(background)
            .border(1.dp, color.copy(alpha = 0.2f), DsShapes.large)
            .padding(DsSpacing.md),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatQty(qty), fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.width(4.dp))
            Text(pluralUnit(qty, unit), fontSize = DsTextSize.caption, color = color, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun ExpiryLine(expiryDate: String?) {
    val day = expiryDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val today = LocalDate.now()
    val expired = day != null && day.isBefore(today)
    val soon    = day != null && !expired && day.isBefore(today.plusDays(30))
    val color   = when { expired -> DsColors.Danger; soon -> DsColors.Warning; else -> DsColors.TextPrimary }

    HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = if (expired || soon) color else DsColors.Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(DsSpacing.sm))
        Text("Date d'expiration", fontSize = DsTextSize.body, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
        if (expired || soon) {
            Text(
                if (expired) "Expiré" else "Expire bientôt",
                fontSize   = DsTextSize.caption,
                fontWeight = FontWeight.SemiBold,
                color      = color,
                modifier   = Modifier
                    .clip(DsShapes.pill)
                    .background(if (expired) DsColors.DangerLight else DsColors.WarningLight)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(DsSpacing.sm))
        }
        Text(day?.let { displayDay(it.toString()) } ?: "Non définie", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun RecentMovementsCard(
    movements : List<StockMovement>,
    unit      : String,
    onSeeAll  : () -> Unit,
    onOpen    : (Int) -> Unit
) {
    DetailCard(
        icon   = Icons.Default.SwapVert,
        title  = "Mouvements récents",
        action = { CardLink("Voir tout", onClick = onSeeAll, chevron = true) }
    ) {
        if (movements.isEmpty()) {
            EmptyCardText("Aucun mouvement pour ce produit")
            return@DetailCard
        }
        Spacer(Modifier.height(DsSpacing.xs))
        movements.forEach { movement ->
            val entree = movement.direction == "entree"
            val color  = if (entree) DsColors.Success else DsColors.Danger
            HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
            Row(
                modifier          = Modifier.fillMaxWidth().clickable { onOpen(movement.id) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(28.dp).clip(DsShapes.pill).background(color), contentAlignment = Alignment.Center) {
                    Icon(if (entree) Icons.Default.Add else Icons.Default.Remove, contentDescription = null, tint = DsColors.Surface, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(DsSpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(movementTypeDisplay(movement.type).second, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                    Text(
                        listOfNotNull(movement.source_label.takeIf { it.isNotBlank() }, if (movement.emplacement == "camion") "Camion" else "Dépôt").joinToString(" · "),
                        fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${if (entree) "+" else "−"}${formatQty(movement.quantity)}",
                    fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = color,
                    modifier = Modifier.padding(horizontal = DsSpacing.sm)
                )
                Text(displayDay(movement.created_at), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptyCardText(text: String) {
    Text(
        text,
        fontSize  = DsTextSize.body,
        color     = DsColors.TextSecondary,
        textAlign = TextAlign.Center,
        modifier  = Modifier.fillMaxWidth().padding(top = DsSpacing.md, bottom = DsSpacing.xs)
    )
}

// ── Composant : sa ligne d'explication dans le dialogue « Informations de stock » ──
@Composable
private fun StockInfoExplainRow(label: String, desc: String) {
    Column {
        Text(label, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        Text(desc, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
    }
}

// ── Helpers ──

/** How a price-history row shows its change from the purchase before. */
private data class Trend(val tint: Color, val background: Color, val icon: ImageVector, val label: String)

/** A stored instant or calendar date as the local day, `dd/MM/yyyy`. */
private fun displayDay(value: String?): String {
    val day = BusinessDates.localDay(value)
    return runCatching { LocalDate.parse(day).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(day)
}

// ── Pluriel simple : 0 et 1 restent au singulier (règle du français) ──
private fun pluralUnit(qty: Double, unit: String): String =
    if (qty <= 1.0 || unit.endsWith("s")) unit else "${unit}s"
