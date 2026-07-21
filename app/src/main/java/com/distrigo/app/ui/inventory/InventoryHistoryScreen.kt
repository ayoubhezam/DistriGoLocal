package com.distrigo.app.ui.inventory

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
@Composable

fun InventoryHistoryScreen(
    viewModel      : InventoryViewModel,
    onBack         : () -> Unit,
    onSessionClick : (InventorySessionHistory) -> Unit,
    onAddNew       : () -> Unit
) {
    val history by viewModel.history.collectAsState()
    var search   by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    val filteredHistory = remember(history, search) {
        if (search.isBlank()) history
        else history.filter {
            inventoryNumero(it.session.id).contains(search, ignoreCase = true) ||
                    it.session.id.toString().contains(search)
        }
    }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
                }
                Spacer(Modifier.width(DsSpacing.sm))
                Text("Historique des inventaires", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            }
            Box(
                modifier         = Modifier.size(40.dp).clip(DsShapes.pill).background(DsColors.Primary).clickable { onAddNew() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un inventaire", tint = Color.White)
            }
        }

        OutlinedTextField(
            value = search, onValueChange = { search = it },
            placeholder = { Text("Rechercher par N° d'inventaire…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
            shape = DsShapes.medium, singleLine = true
        )
        Spacer(Modifier.height(DsSpacing.sm))

        if (filteredHistory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(DsSpacing.sm))
                    Text("Aucun inventaire terminé pour l'instant", color = DsColors.TextSecondary)
                }
            }
        } else {
            val grouped = remember(filteredHistory) {
                filteredHistory.groupBy { (it.session.completed_at ?: it.session.started_at).take(10) }
            }
            LazyColumn(
                contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                grouped.forEach { (date, sessions) ->
                    item {
                        Text(
                            text       = formatOrderDate(date),
                            fontSize   = DsTextSize.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = DsColors.TextTertiary,
                            modifier   = Modifier.padding(vertical = DsSpacing.sm)
                        )
                    }
                    items(sessions, key = { it.session.id }) { entry ->
                        InventoryHistoryRow(entry = entry, onClick = { onSessionClick(entry) })
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
    val items by viewModel.historyItems.collectAsState()

    LaunchedEffect(sessionId) { viewModel.loadHistoryItems(sessionId) }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Text("Détail de l'inventaire", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.Primary)
            }
        } else {
            LazyColumn(
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