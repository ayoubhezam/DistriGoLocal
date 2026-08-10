package com.distrigo.app.ui.chargements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale


data class ChargementCartItem(
    val product      : Product,
    val targetCamion : Double  // desired final quantity in camion (not a delta)
)

internal fun formatQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

@Composable
internal fun ChargementCartRow(
    item              : ChargementCartItem,
    onQuantityChange  : (Double) -> Unit,
    onRemove          : () -> Unit,
    initiallyExpanded : Boolean = false
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val delta         = item.targetCamion - item.product.camion_stock
    val depotPreview  = item.product.stock - item.targetCamion   // stock = total désormais
    val subtitle = when {
        delta > 0 -> "+$delta vers le camion"
        delta < 0 -> "${-delta} vers le dépôt"
        else      -> "Aucun changement"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
    ) {
        // ── Collapsed header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(DsSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bitmap = remember(item.product.image_uri) {
                item.product.image_uri?.let { uri ->
                    val imageBytes = Base64.decode(uri.substringAfter("base64,"), Base64.NO_WRAP)
                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                }
            }
            Box(
                modifier         = Modifier.size(36.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap             = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize().clip(DsShapes.medium),
                        contentScale       = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.product.name,
                    fontSize   = DsTextSize.body,
                    fontWeight = FontWeight.Medium,
                    color      = DsColors.TextPrimary,
                    maxLines   = 1
                )
                Text(subtitle, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = DsColors.TextSecondary
            )
        }

        // ── Expanded panel ──
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .padding(horizontal = DsSpacing.md)
                    .padding(bottom = DsSpacing.md)
            ) {
                HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
                Spacer(Modifier.height(DsSpacing.md))

                // ── Stepper ──
                Text(
                    "Quantité actuellement dans le camion",
                    fontSize  = DsTextSize.caption,
                    color     = DsColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(DsSpacing.sm))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick  = { onQuantityChange(item.targetCamion - 1) },
                        enabled  = item.targetCamion > 0.0,
                        modifier = Modifier.size(36.dp).clip(DsShapes.pill).background(DsColors.SurfaceMuted)
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = null,
                            tint = if (item.targetCamion > 0.0) DsColors.TextPrimary else DsColors.TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        formatQty(item.targetCamion),
                        fontSize   = DsTextSize.display,
                        fontWeight = FontWeight.Medium,
                        color      = DsColors.Primary,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier.widthIn(min = 48.dp).padding(horizontal = DsSpacing.md)
                    )
                    IconButton(
                        onClick  = { onQuantityChange(item.targetCamion + 1) },
                        modifier = Modifier.size(36.dp).clip(DsShapes.pill).background(DsColors.SurfaceMuted)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = DsColors.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(DsSpacing.md))

                // ── Preview boxes ──
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(DsShapes.medium)
                            .background(if (depotPreview < 0) DsColors.DangerLight else DsColors.SurfaceMuted)
                            .padding(DsSpacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Dépôt",
                            fontSize = DsTextSize.caption,
                            color    = if (depotPreview < 0) DsColors.Danger else DsColors.TextSecondary
                        )
                        Text(
                            formatQty(depotPreview),
                            fontSize   = DsTextSize.headline,
                            fontWeight = FontWeight.Medium,
                            color      = if (depotPreview < 0) DsColors.Danger else DsColors.TextPrimary
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(DsShapes.medium)
                            .background(DsColors.PrimaryLight)
                            .padding(DsSpacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Camion", fontSize = DsTextSize.caption, color = DsColors.Primary)
                        Text(formatQty(item.targetCamion), fontSize = DsTextSize.headline, fontWeight = FontWeight.Medium, color = DsColors.Primary)
                    }
                }

                Spacer(Modifier.height(DsSpacing.md))

                // ── Delta banner ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.medium)
                        .background(
                            when {
                                delta > 0 -> DsColors.PrimaryLight
                                delta < 0 -> DsColors.WarningLight
                                else      -> DsColors.SurfaceMuted
                            }
                        )
                        .padding(DsSpacing.md),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    when {
                        delta > 0 -> {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(DsSpacing.xs))
                            Text(
                                "${formatQty(delta)} unité(s) envoyée(s) vers le camion",
                                fontSize = DsTextSize.bodySmall,
                                color    = DsColors.Primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        delta < 0 -> {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = DsColors.Warning, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(DsSpacing.xs))
                            Text(
                                "${formatQty(-delta)} unité(s) ramenée(s) au dépôt",
                                fontSize = DsTextSize.bodySmall,
                                color    = DsColors.Warning,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        else -> {
                            Text("Aucun changement", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(DsSpacing.sm))

                // ── Retirer ──
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retirer", color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                    }
                }
            }
        }
    }
}
