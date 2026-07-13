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
    product  : Product,
    onBack   : () -> Unit,
    onDelete : () -> Unit,
    onEdit   : () -> Unit,
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

            Spacer(Modifier.height(12.dp))

            // ── Price grid ──
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard(modifier = Modifier.weight(1f), label = "Prix de vente",  value = "${product.selling_price} DA",  valueColor = PrimaryBlue)
                InfoCard(modifier = Modifier.weight(1f), label = "Prix d'achat",   value = "${product.purchase_price} DA", valueColor = AccentGreen)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard(modifier = Modifier.weight(1f), label = "Marge",       value = "${"%.1f".format(margin)}%", valueColor = Color(0xFFF57C00))
                InfoCard(modifier = Modifier.weight(1f), label = "Fournisseur", value = product.supplier_name ?: "—", valueColor = TextPrimary)            }

            Spacer(Modifier.height(12.dp))

            // ── Stock card ──
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp),
                border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Informations de stock", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Stock actuel", fontSize = 14.sp, color = TextMuted)
                        Text(
                            text       = "${product.stock} ${product.unit_type}",
                            fontSize   = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = if (isLow) DestructiveRed else AccentGreen
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Seuil minimum", fontSize = 13.sp, color = TextMuted)
                        Text("${product.min_stock} ${product.unit_type}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    }
                    Spacer(Modifier.height(10.dp))
                    val progress = if (product.min_stock > 0)
                        (product.stock.toFloat() / (product.min_stock * 3)).coerceIn(0f, 1f)
                    else 0f
                    LinearProgressIndicator(
                        progress   = { progress },
                        modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color      = if (isLow) DestructiveRed else AccentGreen,
                        trackColor = MutedGray,
                    )
                }
            }

            // ── Date d'expiration ──
            if (product.has_expiry == 1) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isExpired) DestructiveRed
                        else if (isNearExpiry) Color(0xFFE65100)
                        else BorderGray
                    )
                ) {
                    Row(
                        modifier              = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null,
                                tint     = if (isExpired) DestructiveRed else if (isNearExpiry) Color(0xFFE65100) else PrimaryBlue,
                                modifier = Modifier.size(18.dp))
                            Column {
                                Text("Date d'expiration", fontSize = 12.sp, color = TextMuted)
                                Text(
                                    product.expiry_date?.take(10) ?: "Non définie",
                                    fontSize   = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = if (isExpired) DestructiveRed else if (isNearExpiry) Color(0xFFE65100) else TextPrimary
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

            // ── Historique des prix ──
            if (priceHistory.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
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
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(GreenLight)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
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
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(
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
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(BlueLight)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Dernier", fontSize = 9.sp, color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    Text(
                                        "${formatDZD(history.unit_cost)} DA",
                                        fontSize   = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = when {
                                            isMin -> AccentGreen
                                            isMax -> DestructiveRed
                                            else  -> TextPrimary
                                        }
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

@Composable
fun InfoCard(
    modifier   : Modifier = Modifier,
    label      : String,
    value      : String,
    valueColor : Color = TextPrimary,
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 12.sp, color = TextMuted)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}