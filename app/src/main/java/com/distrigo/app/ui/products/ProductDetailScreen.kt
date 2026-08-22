package com.distrigo.app.ui.products

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.data.model.Product
import androidx.compose.foundation.clickable
import com.distrigo.app.ui.suppliers.formatDZD
import androidx.activity.compose.BackHandler
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.hilt.navigation.compose.hiltViewModel
@Composable
fun ProductDetailScreen(
    product          : Product,
    onBack           : () -> Unit,
    onDelete         : () -> Unit,
    onEdit           : () -> Unit,
    onViewMovements  : () -> Unit,
    onInfoGenerales  : () -> Unit,
    onStockPrix      : () -> Unit,
    viewModel: ProductViewModel = hiltViewModel()
) {
    val currentProduct = viewModel.products.collectAsState().value
        .find { it.id == product.id } ?: product

    val isLow  = currentProduct.stock < currentProduct.min_stock
    val margin = if (currentProduct.selling_price > 0)
        ((currentProduct.selling_price - currentProduct.purchase_price) / currentProduct.selling_price * 100)
    else 0.0

    val today        = java.time.LocalDate.now().toString()
    val isExpired    = currentProduct.has_expiry == 1 &&
            !currentProduct.expiry_date.isNullOrEmpty() &&
            currentProduct.expiry_date.take(10) < today
    val isNearExpiry = currentProduct.has_expiry == 1 &&
            !currentProduct.expiry_date.isNullOrEmpty() &&
            !isExpired &&
            java.time.LocalDate.parse(currentProduct.expiry_date.take(10))
                .isBefore(java.time.LocalDate.now().plusDays(30))

    LaunchedEffect(product.id) {
        viewModel.loadPriceHistory(product.id)
    }
    val priceHistory by viewModel.priceHistory.collectAsState()
    var showFullImage by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }


    BackHandler { onBack() }
    BackHandler(enabled = !showFullImage) { onBack() }
    BackHandler(enabled = showFullImage) { showFullImage = false }
    if (showFullImage) {
        currentProduct.image_uri?.let { base64 ->
            val imageBytes = Base64.decode(base64.substringAfter("base64,"), Base64.NO_WRAP)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            bitmap?.let {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .clickable { showFullImage = false },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap             = it.asImageBitmap(),
                        contentDescription = null,
                        modifier           = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentScale       = ContentScale.Fit
                    )
                    // زر الإغلاق
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(36.dp)
                            .clip(DsShapes.pill)
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { showFullImage = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer le produit ?") },
            text  = {
                if (currentProduct.supplier_name != null) {
                    Text("Ce produit est lié au fournisseur ${currentProduct.supplier_name}. Il sera dissocié puis supprimé définitivement.")
                } else {
                    Text("Voulez-vous supprimer ce produit définitivement ?")
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text       = "Détails du produit",
                    fontSize   = DsTextSize.title,
                    fontWeight = FontWeight.Bold,
                    color      = DsColors.TextPrimary
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick  = { showDeleteDialog = true },
                    modifier = Modifier
                        .clip(DsShapes.medium)
                        .background(DsColors.DangerLight)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = DsColors.Danger)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick  = onEdit,
                    modifier = Modifier
                        .clip(DsShapes.medium)
                        .background(DsColors.PrimaryLight)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = DsColors.Primary)
                }
            }
        }

        // ── Image ──
        currentProduct.image_uri?.let { base64 ->
            val imageBytes = Base64.decode(
                base64.substringAfter("base64,"), Base64.NO_WRAP
            )
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            bitmap?.let {
                Image(
                    bitmap             = it.asImageBitmap(),
                    contentDescription = null,
                    modifier           = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = 16.dp)
                        .clip(DsShapes.large)
                        .clickable { showFullImage = true },  // ← أضف هذا

                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {

// ── Name + category card ──
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = DsShapes.large,
                colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                elevation = CardDefaults.cardElevation(1.dp),
                border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            currentProduct.name,
                            fontWeight = FontWeight.Bold,
                            fontSize   = DsTextSize.title,
                            color      = DsColors.TextPrimary,
                            modifier   = Modifier.weight(1f)
                        )
                        if (isLow) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(DsShapes.medium)
                                    .background(DsColors.DangerLight)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Stock faible", fontSize = DsTextSize.caption, color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(DsShapes.pill)
                            .background(DsColors.PrimaryLight)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text       = currentProduct.category_name ?: "Sans catégorie",
                            fontSize   = DsTextSize.caption,
                            color      = DsColors.Primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (!currentProduct.barcode.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(currentProduct.barcode, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))


            // ── Sections navigables ──
            DetailSectionRow(icon = Icons.Default.Info, label = "Informations générales", onClick = onInfoGenerales)

            Spacer(Modifier.height(10.dp))
            DetailSectionRow(icon = Icons.Default.Inventory2, label = "Stock et prix", onClick = onStockPrix)
            Spacer(Modifier.height(10.dp))
            DetailSectionRow(
                icon    = Icons.Default.SwapVert,
                label   = "Mouvements",
                onClick = onViewMovements
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}



@Composable
fun InfoCard(
    modifier   : Modifier = Modifier,
    label      : String,
    value      : String,
    valueColor : Color = DsColors.TextPrimary,
    icon       : androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Card(
        modifier  = modifier,
        shape     = DsShapes.large,
        colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
        elevation = CardDefaults.cardElevation(1.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(value, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = valueColor)
            }
            if (icon != null) {
                Box(
                    modifier         = Modifier.size(36.dp).clip(DsShapes.pill).background(valueColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = valueColor, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ── Composant : carte Dépôt / Camion avec barre de progression ──
@Composable
private fun StockLocationCard(
    modifier   : Modifier,
    icon       : androidx.compose.ui.graphics.vector.ImageVector,
    label      : String,
    value      : String,
    unit       : String,
    color      : Color,
    background : Color,
    progress   : Float
) {
    Column(
        modifier = modifier
            .clip(DsShapes.large)
            .background(background)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier         = Modifier.size(30.dp).clip(DsShapes.pill).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Text(label, fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.5.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(value, fontSize = DsTextSize.display, fontWeight = FontWeight.ExtraBold, color = color)
        Text(unit, fontSize = DsTextSize.caption, fontWeight = FontWeight.Medium, color = color)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress   = { progress },
            modifier   = Modifier.fillMaxWidth().height(4.dp).clip(DsShapes.pill),
            color      = color,
            trackColor = color.copy(alpha = 0.15f)
        )
    }
}

// ── Composant : سطر شرح داخل Dialog "Informations de stock" ──
@Composable
private fun StockInfoExplainRow(label: String, desc: String) {
    Column {
        Text(label, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        Text(desc, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
    }
}

// ── Pluriel simple : 0 et 1 restent au singulier (règle du français) ──
private fun pluralUnit(qty: Double, unit: String): String =
    if (qty <= 1.0 || unit.endsWith("s")) unit else "${unit}s"

// ── Composant réutilisable : ligne de navigation vers une section ──
@Composable
private fun DetailSectionRow(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    label   : String,
    onClick : () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onClick() },
        shape     = DsShapes.large,
        colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
        elevation = CardDefaults.cardElevation(1.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier         = Modifier.size(36.dp).clip(DsShapes.small).background(DsColors.PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                }
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Sous-écran : Informations générales ──
@Composable
fun InfoGeneralesDetailScreen(product: Product, onBack: () -> Unit) {
    val today        = java.time.LocalDate.now().toString()
    val isExpired    = product.has_expiry == 1 &&
            !product.expiry_date.isNullOrEmpty() &&
            product.expiry_date.take(10) < today
    val isNearExpiry = product.has_expiry == 1 &&
            !product.expiry_date.isNullOrEmpty() &&
            !isExpired &&
            java.time.LocalDate.parse(product.expiry_date.take(10))
                .isBefore(java.time.LocalDate.now().plusDays(30))

    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Spacer(Modifier.width(4.dp))
            Text("Informations générales", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = DsShapes.large,
                colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                elevation = CardDefaults.cardElevation(1.dp),
                border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(product.name, fontWeight = FontWeight.Bold, fontSize = DsTextSize.title, color = DsColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.clip(DsShapes.pill).background(DsColors.PrimaryLight).padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            product.category_name ?: "Sans catégorie",
                            fontSize = DsTextSize.caption, color = DsColors.Primary, fontWeight = FontWeight.Medium
                        )
                    }
                    if (!product.barcode.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(product.barcode, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                        }
                    }
                }
            }

            InfoCard(modifier = Modifier.fillMaxWidth(), label = "Fournisseur", value = product.supplier_name ?: "—", valueColor = DsColors.TextPrimary)

            if (product.has_expiry == 1) {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = DsShapes.large,
                    colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isExpired) DsColors.Danger else if (isNearExpiry) DsColors.Warning else DsColors.Border
                    )
                ) {
                    Row(
                        modifier              = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.Default.CalendarToday, contentDescription = null,
                                tint     = if (isExpired) DsColors.Danger else if (isNearExpiry) DsColors.Warning else DsColors.Primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text("Date d'expiration", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                Text(
                                    product.expiry_date?.take(10) ?: "Non définie",
                                    fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold,
                                    color = if (isExpired) DsColors.Danger else if (isNearExpiry) DsColors.Warning else DsColors.TextPrimary
                                )
                            }
                        }
                        if (isExpired) {
                            Box(modifier = Modifier.clip(DsShapes.pill).background(DsColors.DangerLight).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("Expiré", fontSize = DsTextSize.caption, color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                            }
                        } else if (isNearExpiry) {
                            Box(modifier = Modifier.clip(DsShapes.pill).background(DsColors.WarningLight).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("Expire bientôt", fontSize = DsTextSize.caption, color = DsColors.Warning, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Sous-écran : Stock et prix ──
@Composable
fun StockPrixDetailScreen(
    product      : Product,
    priceHistory : List<com.distrigo.app.data.model.PriceHistory>,
    onBack       : () -> Unit,
    onEdit       : () -> Unit
) {
    val isLow  = product.stock < product.min_stock
    val margin = if (product.selling_price > 0)
        ((product.selling_price - product.purchase_price) / product.selling_price * 100)
    else 0.0

    val depotStock     = (product.stock - product.camion_stock)
    val camionStock    = product.camion_stock.coerceAtLeast(0.0)
    val depotFraction  = if (product.stock > 0) (depotStock / product.stock).toFloat().coerceIn(0f, 1f) else 0f
    val camionFraction = if (product.stock > 0) (camionStock / product.stock).toFloat().coerceIn(0f, 1f) else 0f

    var showStockInfoDialog by remember { mutableStateOf(false) }

    if (showStockInfoDialog) {
        AlertDialog(
            onDismissRequest = { showStockInfoDialog = false },
            confirmButton = {
                TextButton(onClick = { showStockInfoDialog = false }) { Text("Compris") }
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

    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Spacer(Modifier.width(4.dp))
            Text("Stock et prix", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard(
                    modifier   = Modifier.weight(1f),
                    label      = "Prix de vente",
                    value      = "${product.selling_price} DA",
                    valueColor = DsColors.Primary,
                    icon       = Icons.Default.LocalOffer
                )
                InfoCard(
                    modifier   = Modifier.weight(1f),
                    label      = "Prix d'achat",
                    value      = "${product.purchase_price} DA",
                    valueColor = DsColors.Success,
                    icon       = Icons.Default.ShoppingCart
                )
            }
            InfoCard(
                modifier   = Modifier.fillMaxWidth(),
                label      = "Marge",
                value      = "${"%.1f".format(margin)}%",
                valueColor = Color(0xFFF57C00),
                icon       = Icons.Default.TrendingUp
            )

            // ── Informations de stock ──
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = DsShapes.large,
                colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                elevation = CardDefaults.cardElevation(1.dp),
                border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Informations de stock", fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary)
                        IconButton(onClick = { showStockInfoDialog = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Info, contentDescription = "En savoir plus", tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // ── Stock total ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.large)
                            .background(DsColors.PrimaryLight)
                            .padding(vertical = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint     = DsColors.Primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(96.dp)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("STOCK TOTAL", fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = DsColors.Primary, letterSpacing = 1.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(formatQty(product.stock), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = DsColors.Primary)
                            Text(pluralUnit(product.stock, product.unit_type), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Dépôt / Camion ──
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StockLocationCard(
                            modifier   = Modifier.weight(1f),
                            icon       = Icons.Default.Warehouse,
                            label      = "DÉPÔT",
                            value      = formatQty(depotStock),
                            unit       = pluralUnit(depotStock, product.unit_type),
                            color      = DsColors.Success,
                            background = DsColors.SuccessLight,
                            progress   = depotFraction
                        )
                        StockLocationCard(
                            modifier   = Modifier.weight(1f),
                            icon       = Icons.Default.LocalShipping,
                            label      = "CAMION",
                            value      = formatQty(camionStock),
                            unit       = pluralUnit(camionStock, product.unit_type),
                            color      = DsColors.Warning,
                            background = DsColors.WarningLight,
                            progress   = camionFraction
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── Seuil minimum ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.medium)
                            .background(DsColors.SurfaceSunken)
                            .clickable { onEdit() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier         = Modifier.size(28.dp).clip(DsShapes.pill).background(Color(0xFFEDE7F6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF6A1B9A), modifier = Modifier.size(14.dp))
                            }
                            Text("SEUIL MINIMUM", fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = Color(0xFF6A1B9A), letterSpacing = 0.5.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${product.min_stock} ${pluralUnit(product.min_stock.toDouble(), product.unit_type)}",
                                fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary
                            )
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── Historique des prix (بدون أي تغيير) ──
            if (priceHistory.isNotEmpty()) {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = DsShapes.large,
                    colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Historique des prix", fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary)
                            val minPrice     = priceHistory.minOf { it.unit_cost }
                            val bestSupplier = priceHistory.find { it.unit_cost == minPrice }
                            bestSupplier?.let {
                                Box(
                                    modifier = Modifier.clip(DsShapes.pill).background(DsColors.SuccessLight).padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("✓ ${it.supplier_name}", fontSize = 10.sp, color = DsColors.Success, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        priceHistory.forEachIndexed { index, history ->
                            val isFirst = index == 0
                            val isMin   = history.unit_cost == priceHistory.minOf { it.unit_cost }
                            val isMax   = history.unit_cost == priceHistory.maxOf { it.unit_cost }

                            Row(
                                modifier              = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier.size(8.dp).clip(DsShapes.pill).background(
                                            when {
                                                isMin   -> DsColors.Success
                                                isMax   -> DsColors.Danger
                                                isFirst -> DsColors.Primary
                                                else    -> DsColors.Border
                                            }
                                        )
                                    )
                                    Column {
                                        Text(history.supplier_name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                                        Text(history.date.take(10), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (isFirst) {
                                        Box(modifier = Modifier.clip(DsShapes.pill).background(DsColors.PrimaryLight).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                            Text("Dernier", fontSize = 9.sp, color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    Text(
                                        "${formatDZD(history.unit_cost)} DA",
                                        fontSize = DsTextSize.body, fontWeight = FontWeight.Bold,
                                        color = when { isMin -> DsColors.Success; isMax -> DsColors.Danger; else -> DsColors.TextPrimary }
                                    )
                                }
                            }
                            if (index < priceHistory.size - 1) {
                                HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}