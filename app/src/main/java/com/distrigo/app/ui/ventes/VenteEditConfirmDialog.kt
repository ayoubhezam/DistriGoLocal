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
import com.distrigo.app.data.model.Quantity
import com.distrigo.app.data.model.Vente
import com.distrigo.app.data.model.numberLabel
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.core.format.MoneyFormatter
import androidx.compose.ui.res.stringResource
import com.distrigo.app.core.text.UiText
import com.distrigo.app.ui.common.asString
import com.distrigo.app.ui.common.pluralCount
import com.distrigo.app.ui.common.quantityText
import com.distrigo.app.R

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
    effects   : List<UiText>,
    onConfirm : () -> Unit,
    onDismiss : () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon             = { Icon(Icons.Default.Edit, contentDescription = null, tint = DsColors.Primary) },
        title            = { Text(stringResource(R.string.ventes_edit_title)) },
        text             = {
            // A sale of many lines can list many changes: the dialog scrolls rather than pushing
            // its buttons off the screen.
            Column(
                modifier            = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                Text(stringResource(R.string.ventes_edit_question, original.numberLabel), color = DsColors.TextPrimary)
                Text(stringResource(R.string.ventes_edit_what_changes), color = DsColors.TextSecondary)
                effects.forEach { effect ->
                    Row {
                        Text("•  ", color = DsColors.TextSecondary)
                        Text(effect.asString(), color = DsColors.TextSecondary)
                    }
                }
            }
        },
        confirmButton    = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_action_save), color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton    = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_action_cancel)) } },
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
): List<UiText> {
    val stockName = UiText.Res(if (original.source == "camion") R.string.common_stock_truck_name else R.string.common_stock_depot_name)
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
        val moved = quantityText(magnitude, unit)
        // The verb agrees with the quantity, as the unit does: "1 carton sortira", "2 cartons sortiront".
        val count = pluralCount(magnitude)
        when {
            before[id] == null -> UiText.Plural(R.plurals.ventes_effect_added, count, listOf(name, moved, stockName))
            after[id] == null  -> UiText.Plural(R.plurals.ventes_effect_removed, count, listOf(name, moved, stockName))
            delta > 0          -> UiText.Plural(R.plurals.ventes_effect_more, count, listOf(name, moved, stockName, Quantity.format(old), Quantity.format(new)))
            else               -> UiText.Plural(R.plurals.ventes_effect_less, count, listOf(name, moved, stockName, Quantity.format(old), Quantity.format(new)))
        }
    }

    val oldPaid = original.montant_paye ?: 0.0
    val restDelta = (newTotal - newPaid) - (original.total - oldPaid)
    val client = original.client_name.orEmpty()

    return buildList {
        if (stock.size > MAX_STOCK_LINES) {
            addAll(stock.take(MAX_STOCK_LINES - 1))
            add((stock.size - (MAX_STOCK_LINES - 1)).let { UiText.Plural(R.plurals.ventes_effect_others, it) })
        } else {
            addAll(stock)
        }
        if (changed(original.total, newTotal)) {
            add(UiText.Res(R.string.ventes_effect_total, money.da(original.total), money.da(newTotal)))
        }
        if (changed(oldPaid, newPaid)) {
            add(
                if (newPaid < oldPaid) UiText.Res(R.string.ventes_effect_payment_lowered, money.da(oldPaid), money.da(newPaid), money.da(oldPaid - newPaid))
                else UiText.Res(R.string.ventes_effect_payment, money.da(oldPaid), money.da(newPaid))
            )
        }
        when {
            restDelta > MONEY_EPSILON  -> add(UiText.Res(R.string.ventes_effect_balance_up, client, money.da(restDelta)))
            restDelta < -MONEY_EPSILON -> add(UiText.Res(R.string.ventes_effect_balance_down, client, money.da(-restDelta)))
            isNotEmpty()               -> add(UiText.Res(R.string.ventes_effect_balance_same, client))
        }
    }
}

private fun changed(a: Double, b: Double) = kotlin.math.abs(a - b) > MONEY_EPSILON

private const val MONEY_EPSILON = 0.005

/** Past this many changed products the list is cut, so the dialog stays readable. */
private const val MAX_STOCK_LINES = 6
