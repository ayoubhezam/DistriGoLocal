package com.distrigo.app.ui.retours

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

internal fun formatRetourQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

internal val RETOUR_FOURNISSEUR_MOTIFS = com.distrigo.app.data.model.RetourFournisseurMotifs.ALL.map { it.id }

data class RetourCartItem(
    val product     : Product,
    val quantity    : Double = 1.0,
    val maxQuantity : Double = Double.MAX_VALUE
)

data class ReturnableProduct(val product: Product, val maxQuantity: Double)

@Composable
internal fun RetourProductRow(
    product          : Product,
    quantity         : Double?,
    maxQuantity      : Double,
    onAdd            : () -> Unit,
    onQuantityChange : (Double) -> Unit   // kept for signature compatibility; no longer called from this row
) {
    val isInCart = quantity != null
    Row(
        modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface)
            .border(1.dp, if (isInCart) DsColors.Success else DsColors.Border, DsShapes.large)
            .padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(if (isInCart) DsColors.SuccessLight else DsColors.SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = remember(product.image_uri) {
                product.image_uri?.let { uri ->
                    val bytes = Base64.decode(uri.substringAfter("base64,"), Base64.NO_WRAP)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
            if (bitmap != null) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = if (isInCart) DsColors.Success else DsColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary, maxLines = 1)
            Text(product.category_name ?: "—", fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1)
            Text("Retour max : ${formatRetourQty(maxQuantity)} ${product.unit_type}", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
        }
        Spacer(Modifier.width(DsSpacing.sm))
        if (isInCart) {
            Row(
                modifier = Modifier
                    .clip(DsShapes.pill)
                    .background(DsColors.SuccessLight)
                    .clickable { onQuantityChange(0.0) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ajouté", fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = DsColors.Success)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.Close, contentDescription = "Retirer de la sélection", tint = DsColors.Success, modifier = Modifier.size(14.dp))
            }
        } else {
            IconButton(onClick = onAdd, modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.SuccessLight)) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter", tint = DsColors.Success, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
internal fun RetourSummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.Success)
        Text(
            value,
            fontSize   = if (highlight) DsTextSize.headline else DsTextSize.bodySmall,
            fontWeight = FontWeight.ExtraBold,
            color      = DsColors.Success
        )
    }
}