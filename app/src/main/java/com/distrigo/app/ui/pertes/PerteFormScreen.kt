package com.distrigo.app.ui.pertes

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.scanner.BarcodeScannerScreen

internal fun formatQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

@Composable
internal fun ProductPickerDialog(products: List<Product>, onSelect: (Product) -> Unit, onDismiss: () -> Unit) {
    var search      by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }

    // ── Recherche par tokens (ex: "br li 5L" → "Brilex Linge 5L") + code-barres ──
    val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    val filtered = products.filter { product ->
        tokens.isEmpty() || tokens.all { token ->
            product.name.contains(token, ignoreCase = true) ||
                    (product.barcode?.contains(token, ignoreCase = true) == true)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = DsColors.Surface) {

            // ── Scanner de code-barres (plein écran, remplace le contenu du Dialog) ──
            if (showScanner) {
                BarcodeScannerScreen(
                    onBarcodeScanned = { code ->
                        search = code
                        showScanner = false
                    },
                    onClose = { showScanner = false }
                )
                return@Surface
            }

            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fermer", tint = DsColors.TextPrimary)
                    }
                    Text("Sélectionner un produit", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                }
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("Rechercher par nom ou code-barres…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (search.isNotEmpty()) {
                                IconButton(onClick = { search = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Effacer", tint = DsColors.TextTertiary, modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(onClick = { showScanner = true }) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner un code-barres", tint = DsColors.Primary)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
                    shape = DsShapes.medium, singleLine = true
                )
                Spacer(Modifier.height(DsSpacing.sm))

                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Aucun produit trouvé", color = DsColors.TextSecondary)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                    ) {
                        items(filtered, key = { it.id }) { product ->
                            ProductPickerRow(product = product, onClick = { onSelect(product) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductPickerRow(product: Product, onClick: () -> Unit) {
    val isLow = product.stock < product.min_stock

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape    = DsShapes.medium,
        color    = DsColors.SurfaceMuted
    ) {
        Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            // ── صورة المنتج، بنفس نمط ProductCard تماماً ──
            Box(
                modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = remember(product.image_uri) {
                    product.image_uri?.let { uri ->
                        val bytes = Base64.decode(uri.substringAfter("base64,"), Base64.NO_WRAP)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.width(DsSpacing.md))

            Column(Modifier.weight(1f)) {
                Text(product.name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1)
                Text(
                    "${product.category_name ?: "—"} · ${product.supplier_name ?: "—"}",
                    fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1
                )
            }

            Spacer(Modifier.width(DsSpacing.sm))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${formatQty(product.stock)} ${product.unit_type}",
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = if (isLow) FontWeight.SemiBold else FontWeight.Medium,
                    color      = if (isLow) DsColors.Danger else DsColors.TextPrimary
                )
                Text(
                    "${formatQty(product.camion_stock)} en camion",
                    fontSize = DsTextSize.caption, color = DsColors.TextTertiary
                )
            }
        }
    }
}

@Composable
internal fun StepIndicator(currentStep: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = DsSpacing.xxxl), verticalAlignment = Alignment.CenterVertically) {
        StepDot(1, "Détails", currentStep >= 1)
        Box(Modifier.weight(1f).height(2.dp).background(if (currentStep >= 2) DsColors.Primary else DsColors.Border))
        StepDot(2, "Résumé", currentStep >= 2)
    }
}

@Composable
private fun StepDot(number: Int, label: String, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(28.dp).clip(DsShapes.pill).background(if (active) DsColors.Primary else DsColors.Border), contentAlignment = Alignment.Center) {
            Text("$number", color = Color.White, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(DsSpacing.xs))
        Text(label, fontSize = DsTextSize.caption, color = if (active) DsColors.Primary else DsColors.TextTertiary)
    }
}

@Composable
internal fun FixedInfoField(label: String, icon: String, value: String) {
    Column {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
        Surface(shape = DsShapes.medium, color = DsColors.SurfaceSunken, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(PerteIconMapper.iconFor(icon), contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(value, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
            }
        }
    }
}

@Composable
internal fun SummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = DsSpacing.sm), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        Text(value, fontSize = DsTextSize.bodySmall, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium, color = if (highlight) DsColors.Primary else DsColors.TextPrimary)
    }
    HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
}