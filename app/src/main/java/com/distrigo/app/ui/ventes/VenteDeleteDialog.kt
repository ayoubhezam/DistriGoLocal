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
import com.distrigo.app.data.model.Amount
import com.distrigo.app.data.model.Vente
import com.distrigo.app.data.model.numberLabel
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

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
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        icon             = { Icon(Icons.Default.Warning, contentDescription = null, tint = DsColors.Danger) },
        title            = { Text("Supprimer la vente") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                Text(
                    "Êtes-vous sûr de vouloir supprimer la vente ${vente.numberLabel} de ${Amount.format(vente.total)} DA ?",
                    color = DsColors.TextPrimary
                )
                Text("La suppression annulera ses mouvements de stock et mettra à jour le solde :", color = DsColors.TextSecondary)
                venteDeleteEffects(vente).forEach { effect ->
                    Row {
                        Text("•  ", color = DsColors.TextSecondary)
                        Text(effect, color = DsColors.TextSecondary)
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
                    Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton    = { TextButton(onClick = onDismiss, enabled = !deleting) { Text("Annuler") } },
        containerColor   = DsColors.Surface
    )
}

/**
 * What deleting [vente] changes, one line each: its stock movements go (the goods are back where
 * they were sold from), and the client's solde loses the sale's total and what was paid with it
 * (ClientDao.recomputeBalance counts a sale as total − montant_paye).
 */
internal fun venteDeleteEffects(vente: Vente): List<String> {
    val place = if (vente.source == "camion") "le stock camion" else "le stock dépôt"
    val count = vente.items_count ?: vente.items?.size
    val paid  = vente.montant_paye ?: 0.0
    val rest  = vente.total - paid
    val client = "« ${vente.client_name} »"
    return listOfNotNull(
        when (count) {
            1    -> "Stock : le produit vendu sera remis dans $place."
            null -> "Stock : les produits vendus seront remis dans $place."
            else -> "Stock : les $count produits vendus seront remis dans $place."
        },
        when {
            rest > 0.005  -> "Solde : ce que doit le client $client baissera de ${Amount.format(rest)} DA (le reste à payer de cette vente)."
            rest < -0.005 -> "Solde : ce que doit le client $client augmentera de ${Amount.format(-rest)} DA (l'avance payée avec cette vente)."
            else          -> "Solde : inchangé pour le client $client, la vente était entièrement payée."
        },
        "Paiement : les ${Amount.format(paid)} DA encaissés avec cette vente seront effacés avec elle.".takeIf { paid > 0.005 }
    )
}
