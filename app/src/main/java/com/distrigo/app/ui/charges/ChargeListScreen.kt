package com.distrigo.app.ui.charges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Charge
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime
import androidx.compose.material.icons.filled.Schedule


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChargeListScreen(
    subtypeId    : Int,
    viewModel    : ChargeViewModel = viewModel(),
    onBack       : () -> Unit,
    onAddCharge  : () -> Unit,
    onEditCharge : (Charge) -> Unit
) {
    val charges  by viewModel.charges.collectAsState()
    val subTypes by viewModel.subTypes.collectAsState()
    val subType = subTypes.find { it.id == subtypeId }

    var longPressCharge   by remember { mutableStateOf<Charge?>(null) }
    var showDeleteDialog  by remember { mutableStateOf(false) }

    LaunchedEffect(subtypeId) { viewModel.loadCharges(subtypeId) }

    val monthTotal = charges.sumOf { it.montant }
    val avgPerCharge = if (charges.isNotEmpty()) monthTotal / charges.size else 0.0

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.xs))
            subType?.let {
                Box(
                    modifier         = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(ChargeIconMapper.iconFor(it.icon), contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(DsSpacing.md))
            Text(subType?.name ?: "", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }

        // ── Carte statistique ──
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
            shape    = DsShapes.large,
            colors   = CardDefaults.cardColors(containerColor = DsColors.Primary)
        ) {
            Column(Modifier.padding(DsSpacing.lg)) {
                Text("Total ce mois", fontSize = DsTextSize.bodySmall, color = Color.White.copy(alpha = 0.8f))
                Text("${"%,.0f".format(monthTotal)} DA", fontSize = DsTextSize.display, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(Modifier.height(DsSpacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.xxl)) {
                    Column {
                        Text("${charges.size}", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Nombre de dépenses", fontSize = DsTextSize.caption, color = Color.White.copy(alpha = 0.7f))
                    }
                    Column {
                        Text("${"%,.0f".format(avgPerCharge)} DA", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Moyenne par dépense", fontSize = DsTextSize.caption, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }

        Spacer(Modifier.height(DsSpacing.md))

        if (charges.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(DsSpacing.sm))
                    Text("Aucune dépense ce mois-ci", color = DsColors.TextSecondary)
                }
            }
        }  else {
        val groupedCharges = remember(charges) { charges.groupBy { it.date_time.take(10) } }
        LazyColumn(
            modifier            = Modifier.fillMaxWidth().weight(1f),
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            groupedCharges.forEach { (date, dayCharges) ->
                item {
                    Text(
                        text       = formatOrderDate(date),
                        fontSize   = DsTextSize.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = DsColors.TextTertiary,
                        modifier   = Modifier.padding(vertical = DsSpacing.sm)
                    )
                }
                items(dayCharges, key = { it.id }) { charge ->
                    ChargeRow(
                        charge        = charge,
                        onClick       = { onEditCharge(charge) },
                        onLongClick   = { longPressCharge = charge; showDeleteDialog = true }
                    )
                }
            }
        }
    }

        Button(
            onClick  = onAddCharge,
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
            shape    = DsShapes.medium,
            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(DsSpacing.xs))
            Text("Ajouter une dépense", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showDeleteDialog && longPressCharge != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title            = { Text("Supprimer la dépense ?") },
            text             = { Text("Cette action est irréversible.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCharge(
                        id = longPressCharge!!.id, subtypeId = subtypeId,
                        onSuccess = { showDeleteDialog = false; longPressCharge = null },
                        onError   = { showDeleteDialog = false }
                    )
                }) { Text("Supprimer", color = DsColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Annuler") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChargeRow(charge: Charge, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape    = DsShapes.medium,
        color    = DsColors.SurfaceMuted
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(DsSpacing.md),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // ── Heure dans un badge, remplace la date supprimée ──
                Box(
                    modifier         = Modifier.size(38.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(DsSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(formatOrderTime(charge.date_time), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                    val subtitle = charge.fournisseur ?: charge.note
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1)
                    }
                }
            }
            Text("${"%,.0f".format(charge.montant)} DA", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.Primary)
        }
    }
}