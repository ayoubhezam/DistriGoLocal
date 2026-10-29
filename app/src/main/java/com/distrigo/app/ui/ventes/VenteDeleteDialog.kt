package com.distrigo.app.ui.ventes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.Vente
import com.distrigo.app.data.model.numberLabel
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.core.format.MoneyFormatter
import androidx.compose.ui.res.stringResource
import com.distrigo.app.core.text.UiText
import com.distrigo.app.ui.common.asString
import com.distrigo.app.R

/**
 * The one confirmation for deleting a sale — from the Dépôt Vente list, a tournée's list, or a
 * sale's details. A sale is corrected by deleting it and entering it again, so this says exactly
 * what the delete takes back: the stock it took out, and what it did to the client's solde.
 */
@Composable
internal fun VenteDeleteDialog(
    vente     : Vente,
    deleting  : Boolean,
    error     : String?,
    onConfirm : () -> Unit,
    onDismiss : () -> Unit
) {
    val money = LocalMoneyFormatter.current
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        icon             = { Icon(Icons.Default.Warning, contentDescription = null, tint = DsColors.Danger) },
        title            = { Text(stringResource(R.string.ventes_delete_title)) },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                Text(
                    stringResource(R.string.ventes_delete_question, vente.numberLabel, money.da(vente.total)),
                    color = DsColors.TextPrimary
                )
                Text(stringResource(R.string.ventes_delete_explain), color = DsColors.TextSecondary)
                venteDeleteEffects(vente, money).forEach { effect ->
                    Row {
                        Text("•  ", color = DsColors.TextSecondary)
                        Text(effect.asString(), color = DsColors.TextSecondary)
                    }
                }
                error?.takeIf { it.isNotBlank() }?.let { Text(it, color = DsColors.Danger, fontSize = DsTextSize.bodySmall) }
            }
        },
        confirmButton    = {
            TextButton(onClick = onConfirm, enabled = !deleting) {
                if (deleting) {
                    CircularProgressIndicator(color = DsColors.Danger, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.common_action_delete), color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton    = { TextButton(onClick = onDismiss, enabled = !deleting) { Text(stringResource(R.string.common_action_cancel)) } },
        containerColor   = DsColors.Surface
    )
}

/**
 * What deleting [vente] changes, one line each: its stock movements go (the goods are back where
 * they were sold from), and the client's solde loses the sale's total and what was paid with it
 * (ClientDao.recomputeBalance counts a sale as total − montant_paye).
 */
internal fun venteDeleteEffects(vente: Vente, money: MoneyFormatter): List<UiText> {
    val place = UiText.Res(if (vente.source == "camion") R.string.common_stock_truck_name else R.string.common_stock_depot_name)
    val count = vente.items_count ?: vente.items?.size
    val paid  = vente.montant_paye ?: 0.0
    val rest  = vente.total - paid
    val client = vente.client_name.orEmpty()
    return listOfNotNull(
        when (count) {
            null -> UiText.Res(R.string.ventes_delete_stock_unknown, place)
            else -> UiText.Plural(R.plurals.ventes_delete_stock, count, listOf(count, place))
        },
        when {
            rest > 0.005  -> UiText.Res(R.string.ventes_delete_balance_down, client, money.da(rest))
            rest < -0.005 -> UiText.Res(R.string.ventes_delete_balance_up, client, money.da(-rest))
            else          -> UiText.Res(R.string.ventes_delete_balance_same, client)
        },
        UiText.Res(R.string.ventes_delete_payment, money.da(paid)).takeIf { paid > 0.005 }
    )
}
