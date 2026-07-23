package com.distrigo.app.ui.products

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.data.model.Product
import androidx.compose.foundation.clickable
import com.distrigo.app.ui.suppliers.formatDZD
import androidx.activity.compose.BackHandler
@Composable
fun ProductDetailScreen(
    product          : Product,
    onBack           : () -> Unit,
    onDelete         : () -> Unit,
    onEdit           : () -> Unit,
    onViewMovements  : () -> Unit,
    viewModel: ProductViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val isLow  = product.stock < product.min_stock
    val margin = if (product.selling_price > 0)
        ((product.selling_price - product.purchase_price) / product.selling_price * 100)
    else 0.0

    val today        = java.time.LocalDate.now().toString()
    val isExpired    = product.has_expiry == 1 &&
            !product.expiry_date.isNullOrEmpty() &&
            product.expiry_date.take(10) < today
    val isNearExpiry = product.has_expiry == 1 &&
            !product.expiry_date.isNullOrEmpty() &&
            !isExpired &&
            java.time.LocalDate.parse(product.expiry_date.take(10))
                .isBefore(java.time.LocalDate.now().plusDays(30))

    LaunchedEffect(product.id) {
        viewModel.loadPriceHistory(product.id)
    }
    val priceHistory by viewModel.priceHistory.collectAsState()
    var showFullImage by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInfoGenerales by remember { mutableStateOf(false) }
    var showStockPrix by remember { mutableStateOf(false) }


    BackHandler { onBack() }
    BackHandler(enabled = !showFullImage) { onBack() }
    BackHandler(enabled = showFullImage) { showFullImage = false }
    if (showFullImage) {
        product.image_uri?.let { base64 ->
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
                            .clip(RoundedCornerShape(50))
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

    if (showInfoGenerales) {
        BackHandler { showInfoGenerales = false }
        InfoGeneralesDetailScreen(product = product, onBack = { showInfoGenerales = false })
        return
    }

    if (showStockPrix) {
        BackHandler { showStockPrix = false }
        StockPrixDetailScreen(product = product, priceHistory = priceHistory, onBack = { showStockPrix = false }, onEdit = onEdit)
        return
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer le produit ?") },
            text  = {
                if (product.supplier_name != null) {
                    Text("Ce produit est lié au fournisseur ${product.supplier_name}. Il sera dissocié puis supprimé définitivement.")
                } else {
                    Text("Voulez-vous supprimer ce produit définitivement ?")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text("Supprimer", color = DestructiveRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
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
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(RedLight)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = DestructiveRed)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick  = onEdit,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BlueLight)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = PrimaryBlue)
                }
            }
        }

        // ── Image ──
        product.image_uri?.let { base64 ->
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
                        .clip(RoundedCornerShape(16.dp))
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
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp),
                border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            product.name,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 18.sp,
                            color      = TextPrimary,
                            modifier   = Modifier.weight(1f)
                        )
                        if (isLow) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(RedLight)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Stock faible", fontSize = 11.sp, color = DestructiveRed, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(BlueLight)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text       = product.category_name ?: "Sans catégorie",
                            fontSize   = 11.sp,
                            color      = PrimaryBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (!product.barcode.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(product.barcode, fontSize = 13.sp, color = TextMuted)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))


            // ── Sections navigables ──
            DetailSectionRow(
                icon    = Icons.Default.Info,
                label   = "Informations générales",
                onClick = { showInfoGenerales = true }
            )
            Spacer(Modifier.height(10.dp))
            DetailSectionRow(
                icon    = Icons.Default.Inventory2,
                label   = "Stock et prix",
                onClick = { showStockPrix = true }
            )
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
    valueColor : Color = TextPrimary,
    icon       : androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text(label, fontSize = 12.sp, color = TextMuted)
                Spacer(Modifier.height(2.dp))
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueColor)
            }
            if (icon != null) {
                Box(
                    modifier         = Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(valueColor.copy(alpha = 0.12f)),
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
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier         = Modifier.size(30.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.5.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(unit, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress   = { progress },
            modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
            color      = color,
            trackColor = color.copy(alpha = 0.15f)
        )
    }
}

// ── Composant : سطر شرح داخل Dialog "Informations de stock" ──
@Composable
private fun StockInfoExplainRow(label: String, desc: String) {
    Column {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(desc, fontSize = 12.sp, color = TextMuted)
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
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier         = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(BlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                }
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Sous-écran : Informations générales ──
@Composable
private fun InfoGeneralesDetailScreen(product: Product, onBack: () -> Unit) {
    val today        = java.time.LocalDate.now().toString()
    val isExpired    = product.has_expiry == 1 &&
            !product.expiry_date.isNullOrEmpty() &&
            product.expiry_date.take(10) < today
    val isNearExpiry = product.has_expiry == 1 &&
            !product.expiry_date.isNullOrEmpty() &&
            !isExpired &&
            java.time.LocalDate.parse(product.expiry_date.take(10))
                .isBefore(java.time.LocalDate.now().plusDays(30))

    Column(modifier = Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Spacer(Modifier.width(4.dp))
            Text("Informations générales", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp),
                border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(product.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(BlueLight).padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            product.category_name ?: "Sans catégorie",
                            fontSize = 11.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium
                        )
                    }
                    if (!product.barcode.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(product.barcode, fontSize = 13.sp, color = TextMuted)
                        }
                    }
                }
            }

            InfoCard(modifier = Modifier.fillMaxWidth(), label = "Fournisseur", value = product.supplier_name ?: "—", valueColor = TextPrimary)

            if (product.has_expiry == 1) {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isExpired) DestructiveRed else if (isNearExpiry) Color(0xFFE65100) else BorderGray
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
                                tint     = if (isExpired) DestructiveRed else if (isNearExpiry) Color(0xFFE65100) else PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text("Date d'expiration", fontSize = 12.sp, color = TextMuted)
                                Text(
                                    product.expiry_date?.take(10) ?: "Non définie",
                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (isExpired) DestructiveRed else if (isNearExpiry) Color(0xFFE65100) else TextPrimary
                                )
                            }
                        }
                        if (isExpired) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(RedLight).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("Expiré", fontSize = 11.sp, color = DestructiveRed, fontWeight = FontWeight.SemiBold)
                            }
                        } else if (isNearExpiry) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFF3E0)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("Expire bientôt", fontSize = 11.sp, color = Color(0xFFE65100), fontWeight = FontWeight.SemiBold)
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
private fun StockPrixDetailScreen(
    product      : Product,
    priceHistory : List<com.distrigo.app.data.model.PriceHistory>,
    onBack       : () -> Unit,
    onEdit       : () -> Unit
) {
    val isLow  = product.stock < product.min_stock
    val margin = if (product.selling_price > 0)
        ((product.selling_price - product.purchase_price) / product.selling_price * 100)
    else 0.0

    val depotStock     = (product.stock - product.camion_stock).coerceAtLeast(0.0)
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
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Spacer(Modifier.width(4.dp))
            Text("Stock et prix", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard(
                    modifier   = Modifier.weight(1f),
                    label      = "Prix de vente",
                    value      = "${product.selling_price} DA",
                    valueColor = PrimaryBlue,
                    icon       = Icons.Default.LocalOffer
                )
                InfoCard(
                    modifier   = Modifier.weight(1f),
                    label      = "Prix d'achat",
                    value      = "${product.purchase_price} DA",
                    valueColor = AccentGreen,
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
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp),
                border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Informations de stock", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        IconButton(onClick = { showStockInfoDialog = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Info, contentDescription = "En savoir plus", tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // ── Stock total ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BlueLight)
                            .padding(vertical = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint     = PrimaryBlue.copy(alpha = 0.12f),
                            modifier = Modifier.size(96.dp)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("STOCK TOTAL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue, letterSpacing = 1.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(formatQty(product.stock), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryBlue)
                            Text(pluralUnit(product.stock, product.unit_type), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
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
                            color      = AccentGreen,
                            background = GreenLight,
                            progress   = depotFraction
                        )
                        StockLocationCard(
                            modifier   = Modifier.weight(1f),
                            icon       = Icons.Default.LocalShipping,
                            label      = "CAMION",
                            value      = formatQty(camionStock),
                            unit       = pluralUnit(camionStock, product.unit_type),
                            color      = Color(0xFFE65100),
                            background = Color(0xFFFFF3E0),
                            progress   = camionFraction
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── Seuil minimum ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MutedGray)
                            .clickable { onEdit() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier         = Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(Color(0xFFEDE7F6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF6A1B9A), modifier = Modifier.size(14.dp))
                            }
                            Text("SEUIL MINIMUM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6A1B9A), letterSpacing = 0.5.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${product.min_stock} ${pluralUnit(product.min_stock.toDouble(), product.unit_type)}",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                            )
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── Historique des prix (بدون أي تغيير) ──
            if (priceHistory.isNotEmpty()) {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Historique des prix", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                            val minPrice     = priceHistory.minOf { it.unit_cost }
                            val bestSupplier = priceHistory.find { it.unit_cost == minPrice }
                            bestSupplier?.let {
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(GreenLight).padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("✓ ${it.supplier_name}", fontSize = 10.sp, color = AccentGreen, fontWeight = FontWeight.SemiBold)
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
                                        modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(
                                            when {
                                                isMin   -> AccentGreen
                                                isMax   -> DestructiveRed
                                                isFirst -> PrimaryBlue
                                                else    -> BorderGray
                                            }
                                        )
                                    )
                                    Column {
                                        Text(history.supplier_name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                        Text(history.date.take(10), fontSize = 11.sp, color = TextMuted)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (isFirst) {
                                        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(BlueLight).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                            Text("Dernier", fontSize = 9.sp, color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    Text(
                                        "${formatDZD(history.unit_cost)} DA",
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                        color = when { isMin -> AccentGreen; isMax -> DestructiveRed; else -> TextPrimary }
                                    )
                                }
                            }
                            if (index < priceHistory.size - 1) {
                                HorizontalDivider(color = BorderGray, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}