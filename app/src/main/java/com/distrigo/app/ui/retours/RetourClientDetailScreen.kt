package com.distrigo.app.ui.retours

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.RetourClient
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime

@Composable
fun RetourClientDetailScreen(
    retourSummary : RetourClient,
    viewModel     : RetourClientViewModel,
    onBack        : () -> Unit,
    onDeleted     : () -> Unit
) {
    val detail by viewModel.retourDetail.collectAsState()
    val display = detail?.takeIf { it.id == retourSummary.id } ?: retourSummary
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(retourSummary.id) { viewModel.loadRetourDetail(retourSummary.id) }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.xs))
            Column(Modifier.weight(1f)) {
                Text("Retour #${display.id}", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Text(display.client_name, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = DsColors.Danger)
            }
        }
        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            item {
                Column(Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.SurfaceMuted).padding(DsSpacing.lg)) {
                    RetourDetailSummaryRow("Date", "${formatOrderDate(display.created_at)} · ${formatOrderTime(display.created_at)}")
                    RetourDetailSummaryRow("Motif", display.motif ?: "—")
                    RetourDetailSummaryRow("Valeur totale (DA)", "${"%,.2f".format(display.total)} DA", highlight = true)
                }
            }
            item { Spacer(Modifier.height(DsSpacing.sm)); Text("Produits", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary) }
            items(display.items ?: emptyList(), key = { it.id }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.SurfaceMuted).padding(DsSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(item.product_name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                        Text("${formatRetourQty(item.quantity)} ${item.unit_type} × ${"%.2f".format(item.unit_price)} DA", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                    Text("${"%.2f".format(item.total_price)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                }
            }
            if (detail == null) {
                item { Box(Modifier.fillMaxWidth().padding(DsSpacing.lg), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DsColors.Primary) } }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer ce retour ?") },
            text  = { Text("Le stock sera ajusté automatiquement pour refléter l'annulation de ce retour.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRetour(
                        id = display.id,
                        onSuccess = { showDeleteDialog = false; onDeleted() },
                        onError   = { showDeleteDialog = false }
                    )
                }) { Text("Supprimer", color = DsColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun RetourDetailSummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = DsSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        Text(
            value,
            fontSize = DsTextSize.bodySmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) DsColors.Danger else DsColors.TextPrimary
        )
    }
}
