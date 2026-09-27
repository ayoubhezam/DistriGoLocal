package com.distrigo.app.ui.chargements

import com.distrigo.app.ui.common.QuantityStepper
import com.distrigo.app.data.model.Quantity
import com.distrigo.app.ui.common.formatQty
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
import java.util.Locale
import com.distrigo.app.ui.common.EntityImage


data class ChargementCartItem(
    val product      : Product,
    val targetCamion : Double  // desired final quantity in camion (not a delta)
)

@Composable
internal fun ChargementCartRow(
    item              : ChargementCartItem,
    onQuantityChange  : (Double) -> Unit,
    onRemove          : () -> Unit,
    initiallyExpanded : Boolean = false,
    /**
     * Strict stock: the most the camion may end up holding (StockPolicy.camionTargetCap), which leaves
     * the dépôt at zero. Null when negative stock is allowed.
     */
    camionCap         : Double? = null
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val delta         = item.targetCamion - item.product.camion_stock
    val depotPreview  = item.product.stock - item.targetCamion   // stock = total désormais
    // Above the cap only when the stock moved after the target was set: the save is refused, so the
    // card says what to bring it back to.
    val overCap       = camionCap != null && Quantity.exceeds(item.targetCamion, camionCap)
    val atCap         = camionCap != null && !overCap && !Quantity.isBelow(item.targetCamion, camionCap)
    val subtitle = when {
        Quantity.isZero(delta) -> "Aucun changement"
        delta > 0              -> "+${formatQty(delta)} vers le camion"
        else                   -> "${formatQty(-delta)} vers le dépôt"
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
            Box(
                modifier         = Modifier.size(36.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                EntityImage(
                    ref                = item.product.image_uri,
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize().clip(DsShapes.medium)
                ) {
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
                // Typed too: half a carton for a carton product, whole units for a pièce one. The
                // floor is 0 — an empty camion — and strict stock caps it at the whole stock.
                QuantityStepper(
                    label          = "Quantité actuellement dans le camion",
                    value          = item.targetCamion,
                    onValueChange  = onQuantityChange,
                    min            = 0.0,
                    max            = camionCap,
                    formatValue    = ::formatQty,
                    allowFractions = Quantity.allowsFractions(item.product.unit_type)
                )

                // Strict stock: the "+" stops when the dépôt is empty, and says why.
                if (overCap || (atCap && delta > 0)) {
                    Spacer(Modifier.height(DsSpacing.xs))
                    Text(
                        if (overCap) "Stock dépôt insuffisant — maximum ${formatQty(camionCap!!)} dans le camion"
                        else "Tout le stock du dépôt est chargé",
                        fontSize  = DsTextSize.caption,
                        color     = if (overCap) DsColors.Danger else DsColors.Warning,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth()
                    )
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
