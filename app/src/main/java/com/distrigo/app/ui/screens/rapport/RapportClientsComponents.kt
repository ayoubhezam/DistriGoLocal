package com.distrigo.app.ui.screens.rapport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import kotlin.math.roundToInt
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Receipt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientKpiCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    value: String,
    infoText: String,
    modifier: Modifier = Modifier
) {
    var showInfoSheet by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .background(DsColors.Surface, DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .padding(DsSpacing.lg)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            Box(modifier = Modifier.size(36.dp).background(iconBg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Text(
                text = title,
                fontSize = DsTextSize.bodySmall,
                fontWeight = FontWeight.Medium,
                color = DsColors.TextSecondary,
                modifier = Modifier.padding(end = 20.dp) // مساحة تفادي تراكب النص مع ⓘ
            )
            Text(value, fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }

        IconButton(
            onClick = { showInfoSheet = true },
            modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(15.dp))
        }
    }

    if (showInfoSheet) {
        ModalBottomSheet(onDismissRequest = { showInfoSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).padding(bottom = DsSpacing.xl)) {
                Text(title, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Spacer(Modifier.height(DsSpacing.sm))
                Text(infoText, fontSize = DsTextSize.body, color = DsColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun ClientRankRow(
    item: com.distrigo.app.data.model.report.ClientRankItem,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val avatarColors = listOf(DsColors.Primary, DsColors.Success, DsColors.Warning, DsColors.Danger)
    val avatarColor = avatarColors[(item.rank - 1).coerceAtLeast(0) % avatarColors.size]
    val initials = item.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            Text("${item.rank}", fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = DsColors.TextTertiary)
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Box(modifier = Modifier.size(36.dp).background(avatarColor.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Text(initials, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = avatarColor)
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
            }
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatCurrency(item.amount.roundToInt()), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text("${item.facturesCount} factures", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
        }
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun TopClientsCard(
    clients: List<com.distrigo.app.data.model.report.ClientRankItem>,
    onVoirTout: () -> Unit,
    onClientClick: (com.distrigo.app.data.model.report.ClientRankItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().background(DsColors.Surface, DsShapes.large)
            .border(1.dp, DsColors.Border, DsShapes.large).padding(DsSpacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = DsColors.Warning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Top 5 clients", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary, modifier = Modifier.weight(1f))
            Row(modifier = Modifier.clickable(onClick = onVoirTout), verticalAlignment = Alignment.CenterVertically) {
                Text("Voir tout", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.Primary)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(DsSpacing.md))
        if (clients.isEmpty()) {
            Text("Aucune donnée à afficher", fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary)
        } else {
            clients.take(5).forEachIndexed { index, item ->
                ClientRankRow(item = item, onClick = { onClientClick(item) })
                if (index != clients.take(5).lastIndex) HorizontalDivider(color = DsColors.Border)
            }
        }
    }
}

@Composable
fun ClientInvoicesScreen(
    clientName: String,
    invoices: List<com.distrigo.app.data.model.report.ClientInvoiceItem>,
    onBack: () -> Unit,
    onViewReceipt: (Int) -> Unit,   // ← جديد
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(DsColors.Surface).padding(DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Factures", fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Text(clientName, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = DsSpacing.lg),
            contentPadding = PaddingValues(vertical = DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            if (invoices.isEmpty()) {
                item { Text("Aucune facture pour ce client", fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary) }
            } else {
                items(invoices, key = { it.venteId }) { invoice ->
                    ClientInvoiceRow(invoice = invoice, onViewReceipt = { onViewReceipt(invoice.venteId) })
                }
            }
        }
    }
}

@Composable
private fun ClientInvoiceRow(
    invoice: com.distrigo.app.data.model.report.ClientInvoiceItem,
    onViewReceipt: () -> Unit   // ← جديد
) {
    val (statusLabel, statusColor, statusBg) = when (invoice.status) {
        "payee" -> Triple("Payée", DsColors.Success, DsColors.SuccessLight)
        "partielle" -> Triple("Partielle", DsColors.Warning, DsColors.WarningLight)
        else -> Triple("Impayée", DsColors.Danger, DsColors.DangerLight)
    }
    Column(
        modifier = Modifier.fillMaxWidth().background(DsColors.Surface, DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium).padding(DsSpacing.md)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Facture #${invoice.venteId}", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Box(modifier = Modifier.background(statusBg, DsShapes.pill).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(statusLabel, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = statusColor)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("${formatOrderDate(invoice.createdAt)} · ${formatOrderTime(invoice.createdAt)}", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(formatCurrency(invoice.total.roundToInt()), fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                if (invoice.reste > 0.0) {
                    Text("Reste: ${formatCurrency(invoice.reste.roundToInt())}", fontSize = DsTextSize.caption, color = DsColors.Danger)
                }
            }
            Row(
                modifier = Modifier
                    .clickable(onClick = onViewReceipt)
                    .background(DsColors.PrimaryLight, DsShapes.pill)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Receipt, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Voir la facture", fontSize = DsTextSize.caption, fontWeight = FontWeight.Medium, color = DsColors.Primary)
            }
        }
    }
}

@Composable
fun TopClientsFullListScreen(
    clients: List<com.distrigo.app.data.model.report.ClientRankItem>,
    onBack: () -> Unit,
    onClientClick: (com.distrigo.app.data.model.report.ClientRankItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(DsColors.Surface).padding(DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text("Tous les clients", fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = DsSpacing.lg),
            contentPadding = PaddingValues(vertical = DsSpacing.lg)
        ) {
            if (clients.isEmpty()) {
                item { Text("Aucune donnée à afficher", fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary) }
            } else {
                itemsIndexed(clients) { index, item ->
                    ClientRankRow(item = item, onClick = { onClientClick(item) })
                    if (index != clients.lastIndex) HorizontalDivider(color = DsColors.Border)
                }
            }
        }
    }
}