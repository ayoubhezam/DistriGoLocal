package com.distrigo.app.ui.pertes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Perte
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime
import android.util.Base64
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Inventory2

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PerteListScreen(
    typeId     : Int,
    viewModel  : PerteViewModel = viewModel(),
    onBack     : () -> Unit,
    onAddPerte : () -> Unit,
    onEditPerte: (Perte) -> Unit
) {
    val pertes     by viewModel.pertes.collectAsState()
    val perteTypes by viewModel.perteTypes.collectAsState()
    val type = perteTypes.find { it.id == typeId }

    var longPressPerte  by remember { mutableStateOf<Perte?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(typeId) { viewModel.loadPertes(typeId) }

    val totalValue = pertes.sumOf { it.valeur_totale }
    val totalQty   = pertes.sumOf { it.quantity }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.xs))
            type?.let {
                Box(
                    modifier         = Modifier.size(40.dp).clip(DsShapes.medium).background(PerteIconMapper.colorFor(it.color_hex).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(PerteIconMapper.iconFor(it.icon), contentDescription = null, tint = PerteIconMapper.colorFor(it.color_hex), modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(DsSpacing.md))
            Column {
                Text(type?.name ?: "", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Text(type?.description ?: "", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
        }

        // ── Carte résumé ──
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
            shape    = DsShapes.large,
            colors   = CardDefaults.cardColors(containerColor = type?.let { PerteIconMapper.colorFor(it.color_hex) } ?: DsColors.Primary)
        ) {
            Column(Modifier.padding(DsSpacing.lg)) {
                Text("Résumé ce mois", fontSize = DsTextSize.bodySmall, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(DsSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.xxl)) {
                    Column {
                        Text("${"%,.0f".format(totalValue)} DA", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("Valeur totale", fontSize = DsTextSize.caption, color = Color.White.copy(alpha = 0.75f))
                    }
                    Column {
                        Text(formatQty(totalQty), fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("Qté totale", fontSize = DsTextSize.caption, color = Color.White.copy(alpha = 0.75f))
                    }
                    Column {
                        Text("${pertes.size}", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("Nombre de pertes", fontSize = DsTextSize.caption, color = Color.White.copy(alpha = 0.75f))
                    }
                }
            }
        }

        Spacer(Modifier.height(DsSpacing.md))

        if (pertes.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inbox, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(DsSpacing.sm))
                    Text("Aucune perte ce mois-ci", color = DsColors.TextSecondary)
                }
            }
        }  else {
            val groupedPertes = remember(pertes) { pertes.groupBy { it.date_time.take(10) } }
            LazyColumn(
                modifier            = Modifier.fillMaxWidth().weight(1f),
                contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                groupedPertes.forEach { (date, dayPertes) ->
                    item {
                        Text(
                            text       = formatOrderDate(date),
                            fontSize   = DsTextSize.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = DsColors.TextTertiary,
                            modifier   = Modifier.padding(vertical = DsSpacing.sm)
                        )
                    }
                    items(dayPertes, key = { it.id }) { perte ->
                        PerteRow(
                            perte       = perte,
                            onClick     = { onEditPerte(perte) },
                            onLongClick = { longPressPerte = perte; showDeleteDialog = true }
                        )
                    }
                }
            }
        }

        Button(
            onClick  = onAddPerte,
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
            shape    = DsShapes.medium,
            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(DsSpacing.xs))
            Text("Ajouter une perte", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showDeleteDialog && longPressPerte != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title            = { Text("Supprimer cette perte ?") },
            text             = { Text("La quantité (${formatQty(longPressPerte!!.quantity)} ${longPressPerte!!.unit}) sera restaurée au stock.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePerte(
                        id = longPressPerte!!.id, typeId = typeId,
                        onSuccess = { showDeleteDialog = false; longPressPerte = null },
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
private fun PerteRow(perte: Perte, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape    = DsShapes.medium,
        color    = DsColors.SurfaceMuted
    ) {
        Row(Modifier.fillMaxWidth().padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            // ── صورة المنتج (نفس نمط ProductCard) ──
            Box(
                modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = remember(perte.product_image_uri) {
                    perte.product_image_uri?.let { uri ->
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
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.width(DsSpacing.md))

            Column(Modifier.weight(1f)) {
                Text(perte.product_name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                if (!perte.motif.isNullOrBlank()) {
                    Text(perte.motif, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1)
                }
                // ── لو Motif فارغ، لا نعرض شيئاً بدله (مساحة فارغة كما طلبت) ──
            }

            Spacer(Modifier.width(DsSpacing.sm))

            Column(horizontalAlignment = Alignment.End) {
                Text("${"%,.0f".format(perte.valeur_totale)} DA", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.Danger)
                Text("${formatQty(perte.quantity)} ${perte.unit}", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
            }
        }
    }
}