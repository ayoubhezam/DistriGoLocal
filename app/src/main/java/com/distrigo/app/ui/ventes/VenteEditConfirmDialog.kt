package com.distrigo.app.ui.ventes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.ProductUnit
import com.distrigo.app.data.model.Quantity
import com.distrigo.app.data.model.Vente
import com.distrigo.app.data.model.numberLabel
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.core.format.MoneyFormatter

/** One line of the edited sale, as the form holds it. */
internal data class EditedLine(val productId: Int, val name: String, val unit: String, val quantity: Double)

/**
 * Asked before an edited sale is saved: saving rewrites its stock movements, its total and its
 * paid amount, and a paid amount lowered here is cash the app stops counting. So, like
 * [VenteDeleteDialog], it says what will change before anything does.
 */
@Composable
internal fun VenteEditConfirmDialog(
    original  : Vente,
    effects   : List<String>,
    onConfirm : () -> Unit,
    onDismiss : () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon             = { Icon(Icons.Default.Edit, contentDescription = null, tint = DsColors.Primary) },
        title            = { Text("Enregistrer les modifications") },
        text             = {
            // A sale of many lines can list many changes: the dialog scrolls rather than pushing
            // its buttons off the screen.
            Column(
                modifier            = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                Text("Voulez-vous modifier la vente ${original.numberLabel} ?", color = DsColors.TextPrimary)
                Text("Ce qui va changer :", color = DsColors.TextSecondary)
                effects.forEach { effect ->
                    Row {
                        Text("•  ", color = DsColors.TextSecondary)
                        Text(effect, color = DsColors.TextSecondary)
                    }
                }
            }
        },
        confirmButton    = {
            TextButton(onClick = onConfirm) {
                Text("Enregistrer", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton    = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        containerColor   = DsColors.Surface
    )
}

/**
 * What saving [lines], [newTotal] and [newPaid] over [original] changes — empty when nothing does.
 *
 * Stock is compared per product (a product on two lines counts once): more sold leaves the stock,
 * less sold comes back. The solde moves by the change in what is left to pay, the way
 * ClientDao.recomputeBalance counts a sale (total − montant_paye).
 */
internal fun venteEditEffects(
    original : Vente,
    lines    : List<EditedLine>,
    newTotal : Double,
    newPaid  : Double,
    money    : MoneyFormatter
): List<String> {
    val stockName = if (original.source == "camion") "stock camion" else "stock dépôt"
    val before = original.items.orEmpty().groupBy { it.product_id }
    val after  = lines.groupBy { it.productId }

    val stock = (before.keys + after.keys).distinct().mapNotNull { id ->
        val old = before[id]?.sumOf { it.quantity } ?: 0.0
        val new = after[id]?.sumOf { it.quantity } ?: 0.0
        val delta = new - old
        if (!Quantity.isBelow(delta, 0.0) && !Quantity.isBelow(0.0, delta)) return@mapNotNull null
        val magnitude = kotlin.math.abs(delta)
        val name = after[id]?.first()?.name ?: before.getValue(id).first().product_name
        val unit = after[id]?.first()?.unit ?: before.getValue(id).first().unit_type
        val moved = "${Quantity.format(magnitude)} ${ProductUnit.plural(unit, magnitude)}"
        // The verb agrees with the quantity, as the unit does: "1 carton sortira", "2 cartons sortiront".
        val leaves  = if (magnitude <= 1.0) "sortira" else "sortiront"
        val returns = if (magnitude <= 1.0) "sera remis" else "seront remis"
        when {
            before[id] == null -> "« $name » ajouté : $moved $leaves du $stockName."
            after[id] == null  -> "« $name » retiré : $moved $returns dans le $stockName."
            delta > 0          -> "« $name » : ${Quantity.format(old)} → ${Quantity.format(new)}, $moved de plus $leaves du $stockName."
            else               -> "« $name » : ${Quantity.format(old)} → ${Quantity.format(new)}, $moved $returns dans le $stockName."
        }
    }

    val oldPaid = original.montant_paye ?: 0.0
    val restDelta = (newTotal - newPaid) - (original.total - oldPaid)
    val client = "« ${original.client_name} »"

    return buildList {
        if (stock.size > MAX_STOCK_LINES) {
            addAll(stock.take(MAX_STOCK_LINES - 1))
            add("… et ${stock.size - (MAX_STOCK_LINES - 1)} autres produits modifiés.")
        } else {
            addAll(stock)
        }
        if (changed(original.total, newTotal)) {
            add("Total : ${money.da(original.total)} → ${money.da(newTotal)}.")
        }
        if (changed(oldPaid, newPaid)) {
            add(
                if (newPaid < oldPaid) "Paiement : ${money.da(oldPaid)} → ${money.da(newPaid)} — ${money.da(oldPaid - newPaid)} encaissés seront effacés."
                else "Paiement : ${money.da(oldPaid)} → ${money.da(newPaid)} encaissés."
            )
        }
        when {
            restDelta > MONEY_EPSILON  -> add("Solde : ce que doit le client $client augmentera de ${money.da(restDelta)}.")
            restDelta < -MONEY_EPSILON -> add("Solde : ce que doit le client $client baissera de ${money.da(-restDelta)}.")
            isNotEmpty()               -> add("Solde : inchangé pour le client $client.")
        }
    }
}

private fun changed(a: Double, b: Double) = kotlin.math.abs(a - b) > MONEY_EPSILON

private const val MONEY_EPSILON = 0.005

/** Past this many changed products the list is cut, so the dialog stays readable. */
private const val MAX_STOCK_LINES = 6
