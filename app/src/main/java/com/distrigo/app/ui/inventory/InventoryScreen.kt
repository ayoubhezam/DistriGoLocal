package com.distrigo.app.ui.inventory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.InventorySessionSummary
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.scanner.BarcodeScannerScreen

fun inventoryNumero(id: Int): String = "N° " + id.toString().padStart(5, '0')

internal fun formatQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

private sealed class InvStep {
    data object Scan : InvStep()
    data class Quantity(val product: Product) : InvStep()
    data class Confirmed(val product: Product, val qteSysteme: Double, val qtePhysique: Double, val ecart: Double, val valeurEcart: Double) : InvStep()
    data object Review : InvStep()
    data object ReadyToFinish : InvStep()
    data object Summary : InvStep()
}

// ══════════════════════════════════════════════
// ── Point d'entrée : Historique (accueil) ──


// ══════════════════════════════════════════════
// ── Flux de création d'un inventaire ──
// ══════════════════════════════════════════════

// ── Étape 1 : Scan ──
@Composable
fun ColumnScope.InventoryScanStep(
    numero            : String,
    sessionItemsCount : Int,
    ecartsCount       : Int,
    totalValueEcarts  : Double,
    scanError         : String,
    canFinish         : Boolean,
    isSaving          : Boolean,
    userName          : String,
    onUserNameChange  : (String) -> Unit,
    onBack            : () -> Unit,
    onScan            : () -> Unit,
    onSearch          : () -> Unit,
    onReview          : () -> Unit,
    onFinish          : () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Column {
            Text("Inventaire", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            if (numero.isNotEmpty()) {
                Text(numero, fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
            }
        }
    }

    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value         = userName,
            onValueChange = onUserNameChange,
            placeholder   = { Text("Effectué par (optionnel)") },
            leadingIcon   = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            shape         = DsShapes.medium
        )

        Spacer(Modifier.height(DsSpacing.xl))
        Box(
            modifier = Modifier.size(140.dp).clip(DsShapes.pill).background(DsColors.PrimaryLight).clickable { onScan() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(DsSpacing.lg))
        Text("Scanner un code-barres", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        Text("Approchez le produit du scanner", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        Spacer(Modifier.height(DsSpacing.md))
        Text("ou", fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary)
        Spacer(Modifier.height(DsSpacing.md))
        OutlinedButton(onClick = onSearch, shape = DsShapes.medium) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DsSpacing.xs))
            Text("Rechercher un produit")
        }

        if (scanError.isNotEmpty()) {
            Spacer(Modifier.height(DsSpacing.md))
            Text(scanError, color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
        }

        Spacer(Modifier.height(DsSpacing.xl))

        Surface(
            shape = DsShapes.large, color = DsColors.SurfaceMuted,
            modifier = Modifier.fillMaxWidth().clickable(enabled = sessionItemsCount > 0) { onReview() }
        ) {
            Column(Modifier.padding(DsSpacing.md)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Progression", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                    if (sessionItemsCount > 0) {
                        Icon(Icons.Default.ArrowForwardIos, contentDescription = "Voir la liste", tint = DsColors.TextTertiary, modifier = Modifier.size(12.dp))
                    }
                }
                Spacer(Modifier.height(DsSpacing.sm))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    InventoryStatChip(Icons.Default.QrCodeScanner, "$sessionItemsCount", "Produits scannés", DsColors.Primary)
                    InventoryStatChip(Icons.Default.Warning, "$ecartsCount", "Écarts détectés", Color(0xFFF79009))
                    InventoryStatChip(Icons.Default.Receipt, "${"%,.0f".format(totalValueEcarts)} DA", "Valeur des écarts", DsColors.Danger)
                }
            }
        }
    }


}

@Composable
private fun InventoryStatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(36.dp).clip(DsShapes.pill).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(DsSpacing.xs))
        Text(value, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
    }
}

// ── Étape 2 : Saisie de la quantité ──
@Composable
fun ColumnScope.InventoryQuantityStep(
    product           : Product,
    qtePhysiqueText   : String,
    onQuantityChange  : (String) -> Unit,
    saveError         : String,
    isSaving          : Boolean,
    onCancel          : () -> Unit,
    onSave            : () -> Unit
) {
    val qtePhysique = qtePhysiqueText.toDoubleOrNull()
    val ecart = qtePhysique?.let { it - product.stock }

    Row(
        Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Text("Inventaire", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Delete, contentDescription = "Annuler", tint = DsColors.TextTertiary)
        }
    }

    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(DsSpacing.md))
            Column {
                Text(product.name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Text("Code-barres", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
                Text(product.barcode ?: "—", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
            }
        }

        Surface(shape = DsShapes.medium, color = DsColors.SurfaceMuted, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(DsSpacing.md)) {
                Text("Qté système", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                Text(formatQty(product.stock), fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.TextPrimary)
                Text(product.unit_type, fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
            }
        }

        Column {
            Text("Qté physique *", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
            OutlinedTextField(
                value = qtePhysiqueText, onValueChange = onQuantityChange,
                placeholder = { Text("0") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(), shape = DsShapes.medium,
                trailingIcon = { Text(product.unit_type, fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary, modifier = Modifier.padding(end = DsSpacing.sm)) }
            )
        }

        if (qtePhysiqueText.isNotEmpty() && ecart != null) {
            val ecartColor = when {
                ecart < 0 -> DsColors.Danger
                ecart > 0 -> Color(0xFF12B76A)
                else      -> DsColors.TextSecondary
            }
            Surface(shape = DsShapes.medium, color = ecartColor.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(DsSpacing.md)) {
                    Text("Écart", fontSize = DsTextSize.bodySmall, color = ecartColor)
                    Text(
                        (if (ecart > 0) "+" else "") + formatQty(ecart),
                        fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = ecartColor
                    )
                    Text(product.unit_type, fontSize = DsTextSize.caption, color = ecartColor.copy(alpha = 0.7f))
                }
            }
        }

        if (saveError.isNotEmpty()) {
            Text(saveError, color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
        }
        Spacer(Modifier.height(DsSpacing.sm))
    }

    Button(
        onClick  = onSave,
        enabled  = qtePhysiqueText.isNotEmpty() && !isSaving,
        modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
        shape    = DsShapes.medium,
        colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
    ) {
        if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
        else {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DsSpacing.xs))
            Text("Enregistrer", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Étape 3 : Confirmation d'un scan individuel ──
@Composable
fun ColumnScope.InventoryConfirmedStep(
    product     : Product,
    qteSysteme  : Double,
    qtePhysique : Double,
    ecart       : Double,
    onScanNext  : () -> Unit
) {
    val ecartColor = when {
        ecart < 0 -> DsColors.Danger
        ecart > 0 -> Color(0xFF12B76A)
        else      -> DsColors.TextSecondary
    }

    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(DsSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(88.dp).clip(DsShapes.pill).background(Color(0xFF12B76A).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF12B76A), modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(DsSpacing.md))
        Text("Enregistré !", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.TextPrimary)
        Text(product.name, fontSize = DsTextSize.bodyLarge, color = DsColors.TextSecondary)

        Spacer(Modifier.height(DsSpacing.lg))

        Surface(shape = DsShapes.large, color = DsColors.SurfaceMuted, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(DsSpacing.lg)) {
                InventorySummaryRow("Qté système", formatQty(qteSysteme))
                InventorySummaryRow("Qté physique", formatQty(qtePhysique))
                InventorySummaryRow("Écart", (if (ecart > 0) "+" else "") + formatQty(ecart), ecartColor)
            }
        }

        Spacer(Modifier.height(DsSpacing.lg))

        Surface(shape = DsShapes.medium, color = Color(0xFF12B76A).copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF12B76A), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Column {
                    Text("Prêt pour le prochain scan", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF12B76A))
                    Text("Scannez un autre produit", fontSize = DsTextSize.caption, color = Color(0xFF12B76A).copy(alpha = 0.8f))
                }
            }
        }
    }

    Button(
        onClick  = onScanNext,
        modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
        shape    = DsShapes.medium,
        colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
    ) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(DsSpacing.xs))
        Text("Scanner un autre produit", color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InventorySummaryRow(label: String, value: String, valueColor: Color = DsColors.TextPrimary) {
    Row(Modifier.fillMaxWidth().padding(vertical = DsSpacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        Text(value, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

// ── Étape : Liste des produits scannés (type panier, modifiable) ──
@Composable
fun ColumnScope.InventoryReviewStep(
    items      : List<com.distrigo.app.data.model.InventoryItem>,
    isSaving   : Boolean,
    onBack     : () -> Unit,
    onEdit     : (com.distrigo.app.data.model.InventoryItem, Double) -> Unit,
    onDelete   : (com.distrigo.app.data.model.InventoryItem) -> Unit,
    onFinish   : () -> Unit
) {
    var editingItem  by remember { mutableStateOf<com.distrigo.app.data.model.InventoryItem?>(null) }
    var editQtyText  by remember { mutableStateOf("") }
    var deletingItem by remember { mutableStateOf<com.distrigo.app.data.model.InventoryItem?>(null) }

    Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Text("Produits scannés (${items.size})", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
    }

    if (items.isEmpty()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Aucun produit scanné", color = DsColors.TextSecondary)
        }
    } else {
        LazyColumn(
            modifier            = Modifier.weight(1f).fillMaxWidth(),
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            items(items, key = { it.id }) { item ->
                val ecartColor = when {
                    item.ecart < 0 -> DsColors.Danger
                    item.ecart > 0 -> Color(0xFF12B76A)
                    else           -> DsColors.TextSecondary
                }
                Surface(shape = DsShapes.medium, color = DsColors.SurfaceMuted, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.product_name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                            Text("Système: ${formatQty(item.qte_systeme)} → Physique: ${formatQty(item.qte_physique)}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                        }
                        Text(
                            (if (item.ecart > 0) "+" else "") + formatQty(item.ecart),
                            fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = ecartColor,
                            modifier = Modifier.padding(end = DsSpacing.sm)
                        )
                        IconButton(onClick = { editingItem = item; editQtyText = formatQty(item.qte_physique) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { deletingItem = item }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = DsColors.Danger, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    Button(
        onClick  = onFinish,
        enabled  = items.isNotEmpty() && !isSaving,
        modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
        shape    = DsShapes.medium,
        colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
    ) {
        if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
        else {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DsSpacing.xs))
            Text("Ok", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }

    editingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text(item.product_name) },
            text = {
                OutlinedTextField(
                    value = editQtyText,
                    onValueChange = { raw ->
                        val filtered = raw.filter { c -> c.isDigit() || c == '.' }
                        editQtyText = if (filtered.count { it == '.' } > 1) editQtyText else filtered
                    },
                    label = { Text("Qté physique") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = DsShapes.medium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editQtyText.toDoubleOrNull()?.let { onEdit(item, it) }
                    editingItem = null
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { editingItem = null }) { Text("Annuler") } },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("Supprimer \"${item.product_name}\" ?") },
            text  = { Text("Le stock sera restauré à ${formatQty(item.qte_systeme)}.") },
            confirmButton = {
                TextButton(onClick = { onDelete(item); deletingItem = null }) {
                    Text("Supprimer", color = DsColors.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { deletingItem = null }) { Text("Annuler") } },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }
}

// ── Étape : Scan terminé, avant le résumé ──
@Composable
fun ColumnScope.InventoryReadyToFinishStep(
    itemsCount    : Int,
    onBack        : () -> Unit,
    onShowSummary : () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Text("Inventaire", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
    }

    Column(
        modifier = Modifier.weight(1f).fillMaxSize().padding(DsSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(88.dp).clip(DsShapes.pill).background(Color(0xFF12B76A).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF12B76A), modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(DsSpacing.md))
        Text("Scan terminé !", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.TextPrimary)
        Text("$itemsCount produit(s) scanné(s)", fontSize = DsTextSize.bodyLarge, color = DsColors.TextSecondary)

        Spacer(Modifier.height(DsSpacing.xl))

        Surface(
            shape = DsShapes.medium, color = DsColors.Primary,
            modifier = Modifier.fillMaxWidth().clickable { onShowSummary() }
        ) {
            Row(
                Modifier.padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Assignment, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text("Afficher le résumé de l'inventaire", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── Étape : Résumé + Confirmation finale ──
@Composable
fun ColumnScope.InventorySummaryStep(
    summary         : InventorySessionSummary,
    isConfirmed     : Boolean,
    isConfirming    : Boolean,
    confirmError    : String,
    onBack          : () -> Unit,
    onConfirm       : () -> Unit,
    onViewDetail    : () -> Unit,
    onReturnHistory : () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = !isConfirmed) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = if (isConfirmed) DsColors.TextTertiary else DsColors.TextPrimary)
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Text("Résumé de l'inventaire", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
    }

    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            InventorySummaryStatCard(Icons.Default.Inventory2, "${summary.total_products}", "Total produits", DsColors.Primary, Modifier.weight(1f))
            InventorySummaryStatCard(Icons.Default.Warning, "${summary.total_ecarts}", "Écarts détectés", Color(0xFFF79009), Modifier.weight(1f))
        }
        InventorySummaryStatCard(
            Icons.Default.Receipt, "${"%,.0f".format(summary.total_value_ecarts)} DA",
            "Valeur totale des écarts", DsColors.Danger, Modifier.fillMaxWidth()
        )

        if (isConfirmed) {
            Surface(shape = DsShapes.medium, color = Color(0xFF12B76A).copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF12B76A))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Column {
                        Text("Inventaire complété avec succès", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF12B76A))
                        Text("Le stock a été mis à jour", fontSize = DsTextSize.caption, color = Color(0xFF12B76A).copy(alpha = 0.8f))
                    }
                }
            }
        }

        Surface(shape = DsShapes.medium, color = DsColors.SurfaceMuted, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(DsSpacing.md)) {
                Text("Informations", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Spacer(Modifier.height(DsSpacing.sm))
                InventorySummaryRow("Date", formatOrderDate(java.time.LocalDate.now().toString()))
                InventorySummaryRow("Emplacement", "Entrepôt principal")
                InventorySummaryRow("Méthode", "Scan / Recherche")
            }
        }

        OutlinedButton(onClick = onViewDetail, modifier = Modifier.fillMaxWidth().height(48.dp), shape = DsShapes.medium) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DsSpacing.xs))
            Text("Voir le détail des écarts")
        }

        if (confirmError.isNotEmpty()) {
            Text(confirmError, color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
        }
    }

    if (isConfirmed) {
        Button(
            onClick  = onReturnHistory,
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
            shape    = DsShapes.medium,
            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            Text("Retour à l'historique", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Button(
            onClick  = onConfirm,
            enabled  = !isConfirming,
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
            shape    = DsShapes.medium,
            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            if (isConfirming) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            else {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.xs))
                Text("Confirmer l'inventaire", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun InventorySummaryStatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = DsShapes.medium, color = color.copy(alpha = 0.08f), modifier = modifier) {
        Column(Modifier.padding(DsSpacing.md)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(DsSpacing.xs))
            Text(value, fontSize = DsTextSize.title, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
    }
}

// ── Dialog : Recherche de produit ──
@Composable
fun InventoryProductSearchDialog(products: List<Product>, onSelect: (Product) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    val filtered = products.filter { product ->
        tokens.isEmpty() || tokens.all { token ->
            product.name.contains(token, ignoreCase = true) || (product.barcode?.contains(token, ignoreCase = true) == true)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = DsColors.Surface) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fermer", tint = DsColors.TextPrimary)
                    }
                    Text("Rechercher un produit", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                }
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("Rechercher par nom ou code-barres…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
                    shape = DsShapes.medium, singleLine = true
                )
                Spacer(Modifier.height(DsSpacing.sm))
                LazyColumn(contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    items(filtered, key = { it.id }) { product ->
                        Surface(modifier = Modifier.fillMaxWidth().clickable { onSelect(product) }, shape = DsShapes.medium, color = DsColors.SurfaceMuted) {
                            Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(product.name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                                    Text("Stock: ${formatQty(product.stock)} ${product.unit_type}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Dialog : Détail des écarts ──
@Composable
fun InventoryDetailDialog(items: List<com.distrigo.app.data.model.InventoryItem>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = DsColors.Surface) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fermer", tint = DsColors.TextPrimary)
                    }
                    Text("Détail des écarts", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                }
                LazyColumn(contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    items(items, key = { it.id }) { item ->
                        val ecartColor = when {
                            item.ecart < 0 -> DsColors.Danger
                            item.ecart > 0 -> Color(0xFF12B76A)
                            else           -> DsColors.TextSecondary
                        }
                        Surface(shape = DsShapes.medium, color = DsColors.SurfaceMuted, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.product_name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                                    Text("Système: ${formatQty(item.qte_systeme)} → Physique: ${formatQty(item.qte_physique)}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                }
                                Text((if (item.ecart > 0) "+" else "") + formatQty(item.ecart), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = ecartColor)
                            }
                        }
                    }
                }
            }
        }
    }
}