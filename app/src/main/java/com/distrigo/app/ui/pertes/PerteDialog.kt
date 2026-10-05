package com.distrigo.app.ui.pertes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.border
import com.distrigo.app.data.model.PerteType
import com.distrigo.app.data.model.Quantity
import com.distrigo.app.ui.common.formatQty
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors

/**
 * Why a quantity cannot be recorded as lost, or null when it can: nothing, or more than [cap] — the
 * dépôt stock under strict stock ("Autoriser le stock négatif" off). A null [cap] is no limit.
 */
fun perteQuantityError(quantity: Double?, cap: Double?, unit: String): String? = when {
    quantity == null || quantity <= 0 -> "Quantité invalide"
    cap != null && Quantity.exceeds(quantity, cap) -> "Stock insuffisant : ${formatQty(cap)} $unit au dépôt"
    else -> null
}

/**
 * A product's loss, in a dialog centred on the screen: the type of perte, the stock it is taken from
 * (read-only), and the quantity lost. [cap] is the most it may take — the dépôt stock when negative
 * stock is not allowed, null when it is — and "Enregistrer" stays off past it, saying why.
 */
@Composable
fun PerteDialog(
    productName : String,
    unit        : String,
    stock       : Double,
    cap         : Double?,
    types       : List<PerteType>,
    initialType : Int?,
    initialQty  : Double?,
    initialMotif: String? = null,
    isSaving    : Boolean = false,
    error       : String = "",
    onSave      : (typeId: Int, quantity: Double, motif: String?) -> Unit,
    onDismiss   : () -> Unit,
) {
    var typeId by remember { mutableStateOf(initialType ?: types.firstOrNull()?.id) }
    var text by remember { mutableStateOf(initialQty?.let { formatQty(it) } ?: "") }
    var motif by remember { mutableStateOf(initialMotif ?: "") }
    var typeMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // A new line opens on the quantity, keyboard up. The dialog's window is not there on the first
    // frame — focus asked for then is taken without raising the keyboard — so wait for it.
    LaunchedEffect(Unit) {
        if (initialQty == null) {
            kotlinx.coroutines.delay(150)
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }

    val quantity = Quantity.parse(text)
    val problem = if (text.isBlank()) null else perteQuantityError(quantity, cap, unit)
    val type = types.find { it.id == typeId }

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface).padding(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.DangerLight),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.RemoveShoppingCart, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(DsSpacing.sm))
                Text(productName, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            }

            // The type, as a dropdown.
            Box {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.medium)
                        .border(1.dp, DsColors.Border, DsShapes.medium)
                        .clickable(role = Role.DropdownList) { typeMenu = true }
                        .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm)
                ) {
                    Text("Type de perte", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(type?.name ?: "Choisir", fontSize = DsTextSize.body, fontWeight = FontWeight.Medium,
                            color = if (type != null) DsColors.TextPrimary else DsColors.TextTertiary, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DsColors.TextSecondary)
                    }
                }
                DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }, modifier = Modifier.background(DsColors.Surface)) {
                    types.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.name, fontWeight = if (t.id == typeId) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (t.id == typeId) DsColors.Primary else DsColors.TextPrimary) },
                            onClick = { typeId = t.id; typeMenu = false }
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.SurfaceMuted).padding(DsSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Qté système (dépôt)", fontSize = DsTextSize.body, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
                Text("${formatQty(stock)} $unit", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold,
                    color = if (stock < 0) DsColors.Danger else DsColors.TextPrimary)
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = Quantity.sanitizeInput(it, Quantity.allowsFractions(unit)) },
                label = { Text("Quantité perdue") },
                suffix = { Text(unit) },
                singleLine = true,
                isError = problem != null || error.isNotEmpty(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = DsShapes.medium,
                colors = dsTextFieldColors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            OutlinedTextField(
                value = motif,
                onValueChange = { motif = it },
                label = { Text("Motif (optionnel)") },
                singleLine = true,
                shape = DsShapes.medium,
                colors = dsTextFieldColors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary),
                modifier = Modifier.fillMaxWidth()
            )
            val shown = problem ?: error
            if (shown.isNotEmpty()) Text(shown, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)

            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                OutlinedButton(onClick = onDismiss, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = DsShapes.medium) {
                    Text("Annuler")
                }
                Button(
                    onClick = { val t = typeId; if (t != null && quantity != null) onSave(t, quantity, motif.trim().takeIf { it.isNotEmpty() }) },
                    enabled = !isSaving && typeId != null && text.isNotBlank() && problem == null,
                    modifier = Modifier.weight(1f).height(48.dp), shape = DsShapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                    else Text("Enregistrer", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
