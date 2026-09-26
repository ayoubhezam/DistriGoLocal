package com.distrigo.app.ui.inventory

import com.distrigo.app.data.time.BusinessDates
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.drop
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.InventorySessionHistory
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.PendingActions
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.common.DsCompactSearchField
@Composable

fun InventoryHistoryScreen(
    viewModel      : InventoryViewModel,
    onBack         : () -> Unit,
    onSessionClick : (InventorySessionHistory) -> Unit,
    onAddNew       : () -> Unit
) {
    // Paged, each page's totals summed in SQL - see InventoryViewModel.history. It is reloaded where
    // it changes (leaving or finishing a count), not on every opening as the full list was.
    val history = viewModel.history.collectAsLazyPagingItems()
    val listState = rememberLazyListState()

    // A new search starts its pages at the top again: send the list there, or it would open part-way
    // down the new results. Not on opening, where the list keeps the position it had.
    LaunchedEffect(Unit) {
        snapshotFlow { viewModel.historySearch }.drop(1).collect { listState.scrollToItem(0) }
    }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(
            title   = "Historique des inventaires",
            leading = DsTopBarLeading.Back(onBack)
        ) {
            // The bar's end inset assumes a 48dp icon button; this pill is 40dp, so it supplies the
            // difference itself to land on the same margin as every other action.
            Box(
                modifier         = Modifier.padding(end = DsSpacing.md).size(40.dp).clip(DsShapes.pill).background(DsColors.Primary).clickable { onAddNew() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un inventaire", tint = Color.White)
            }
        }

        Spacer(Modifier.height(DsSpacing.md))

        DsCompactSearchField(
            value         = viewModel.historySearch,
            onValueChange = { viewModel.historySearch = it },
            placeholder   = "Rechercher un inventaire",
            modifier      = Modifier.padding(horizontal = DsSpacing.lg)
        )
        Spacer(Modifier.height(DsSpacing.sm))

        val refresh = history.loadState.refresh
        when {
            history.itemCount == 0 && refresh is LoadState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
            }
            history.itemCount == 0 -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text("Aucun inventaire terminé pour l'instant", color = DsColors.TextSecondary)
                    }
                }
            }
            else -> {
                LazyColumn(
                    state               = listState,
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(
                        count       = history.itemCount,
                        key         = history.itemKey { row ->
                            when (row) {
                                is InventoryHistoryItem.DayHeader -> "header_${row.day}"
                                is InventoryHistoryItem.Row       -> row.entry.session.id
                            }
                        },
                        contentType = history.itemContentType { row -> row is InventoryHistoryItem.DayHeader }
                    ) { index ->
                        when (val row = history[index]) {
                            is InventoryHistoryItem.DayHeader -> Text(
                                text       = formatOrderDate(row.day),
                                fontSize   = DsTextSize.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = DsColors.TextTertiary,
                                modifier   = Modifier.padding(vertical = DsSpacing.sm)
                            )
                            is InventoryHistoryItem.Row -> InventoryHistoryRow(entry = row.entry, onClick = { onSessionClick(row.entry) })
                            null -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryHistoryRow(entry: InventorySessionHistory, onClick: () -> Unit) {
    val isDraft = entry.session.status == "draft"
    val statusColor = if (isDraft) Color(0xFFF79009) else Color(0xFF12B76A)

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape    = DsShapes.medium,
        color    = DsColors.SurfaceMuted
    ) {
        Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isDraft) Icons.Default.PendingActions else Icons.Default.Inventory2,
                    contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(inventoryNumero(entry.session.id), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                    Spacer(Modifier.width(DsSpacing.xs))
                    Surface(shape = DsShapes.pill, color = statusColor.copy(alpha = 0.15f)) {
                        Text(
                            if (isDraft) "En attente" else "Terminé",
                            fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = statusColor,
                            modifier = Modifier.padding(horizontal = DsSpacing.sm, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    "${entry.summary.total_products} produits · ${entry.summary.total_ecarts} écarts",
                    fontSize = DsTextSize.caption, color = DsColors.TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${"%,.0f".format(entry.summary.total_value_ecarts)} DA",
                    fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = statusColor
                )
                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(12.dp))
            }
        }
    }
}

// ── Détail d'une session terminée (lecture seule) ──
@Composable
fun InventorySessionDetailScreen(
    sessionId : Int,
    viewModel : InventoryViewModel,
    onBack    : () -> Unit
) {
    val items = viewModel.historyItems.collectAsLazyPagingItems()

    LaunchedEffect(sessionId) { viewModel.showSessionDetail(sessionId) }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(
            title   = "Détail de l'inventaire",
            leading = DsTopBarLeading.Back(onBack)
        )

        Spacer(Modifier.height(DsSpacing.md))

        // A session with no lines is shown as such; this used to spin for ever on one.
        when {
            items.itemCount == 0 && items.loadState.refresh is LoadState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
            }
            items.itemCount == 0 -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucun produit compté", color = DsColors.TextSecondary)
                }
            }
            else -> {
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                        val item = items[index] ?: return@items
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
