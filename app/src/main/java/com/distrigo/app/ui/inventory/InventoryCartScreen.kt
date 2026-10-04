package com.distrigo.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.distrigo.app.data.model.InventoryItem
import com.distrigo.app.data.model.Quantity
import com.distrigo.app.ui.common.CartStatusLine
import com.distrigo.app.ui.common.CartStatusTone
import com.distrigo.app.ui.common.SelectionCartCard
import com.distrigo.app.ui.common.formatQty
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.format.LocalMoneyFormatter
import kotlin.math.abs

/**
 * The count's selection, laid out as the Ventes and Achats carts are: one card per product counted —
 * system and physical quantities, the écart in colour — unfolding to correct the count or remove it.
 * Removing a line takes its adjustment back and puts the product back on the list. "Suivant" goes on
 * to the summary.
 */
@Composable
fun InventoryCartScreen(
    items    : LazyPagingItems<InventoryItem>,
    count    : Int,
    error    : String,
    onEdit   : (InventoryItem, Double) -> Unit,
    onDelete : (InventoryItem) -> Unit,
    onBack   : () -> Unit,
    onNext   : () -> Unit,
) {
    val money = LocalMoneyFormatter.current
    var expandedId by remember { mutableStateOf<Int?>(null) }
    var deleting by remember { mutableStateOf<InventoryItem?>(null) }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = "Ma sélection", subtitle = "$count produit(s) inventorié(s)", leading = DsTopBarLeading.Back(onBack))
        Spacer(Modifier.height(DsSpacing.sm))

        if (error.isNotEmpty()) {
            Text(error, fontSize = DsTextSize.bodySmall, color = DsColors.Danger, modifier = Modifier.padding(horizontal = DsSpacing.lg))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                items.itemCount == 0 && items.loadState.refresh is LoadState.Loading ->
                    CircularProgressIndicator(color = DsColors.Primary, modifier = Modifier.align(Alignment.Center))
                items.itemCount == 0 ->
                    Text("Aucun produit inventorié", color = DsColors.TextSecondary, modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
                ) {
                    items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                        val item = items[index] ?: return@items
                        val expanded = expandedId == item.id
                        val (tone, icon, status) = when {
                            item.ecart < 0 -> Triple(CartStatusTone.DANGER, Icons.Default.TrendingDown,
                                "Manque ${formatQty(abs(item.ecart))} · ${money.da(abs(item.valeur_ecart))}")
                            item.ecart > 0 -> Triple(CartStatusTone.WARNING, Icons.Default.TrendingUp,
                                "Surplus ${formatQty(item.ecart)} · ${money.da(item.valeur_ecart)}")
                            else -> Triple(CartStatusTone.OK, Icons.Default.CheckCircle, "Aucun écart")
                        }
                        SelectionCartCard(
                            avatarIcon      = Icons.Default.Inventory2,
                            title           = item.product_name,
                            metaLine        = "Système ${formatQty(item.qte_systeme)} → Physique ${formatQty(item.qte_physique)}",
                            totalPriceLabel = (if (item.ecart > 0) "+" else "") + formatQty(item.ecart),
                            isExpanded      = expanded,
                            onToggleExpand  = { expandedId = if (expanded) null else item.id },
                            statusLine      = { CartStatusLine(icon = icon, text = status, tone = tone) },
                            expandedContent = {
                                var text by remember(item.id, item.qte_physique) { mutableStateOf(formatQty(item.qte_physique)) }
                                OutlinedTextField(
                                    value = text,
                                    onValueChange = { text = Quantity.sanitizeInput(it) },
                                    label = { Text("Qté physique") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = DsShapes.medium,
                                    colors = dsTextFieldColors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(DsSpacing.sm))
                                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { deleting = item }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Retirer", color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    val parsed = Quantity.parse(text)
                                    Button(
                                        onClick = { parsed?.let { onEdit(item, it); expandedId = null } },
                                        enabled = parsed != null && parsed >= 0 && parsed != item.qte_physique,
                                        shape = DsShapes.medium,
                                        colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                                    ) { Text("Enregistrer") }
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
                onClick  = onNext,
                enabled  = count > 0,
                modifier = Modifier.weight(1f).height(52.dp),
                shape    = DsShapes.medium,
                colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
            ) { Text("Suivant →", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold) }
        }
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Retirer « ${item.product_name} » ?") },
            text  = { Text("Son ajustement est annulé et le produit revient dans la liste à inventorier.") },
            confirmButton = { TextButton(onClick = { onDelete(item); deleting = null }) { Text("Retirer", color = DsColors.Danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
            containerColor = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor = DsColors.TextSecondary
        )
    }
}
