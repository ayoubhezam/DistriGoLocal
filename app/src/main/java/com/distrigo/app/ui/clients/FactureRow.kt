package com.distrigo.app.ui.clients

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.ClientTransaction
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.purchases.formatOrderTime

/**
 * Single row rendering for one client ledger entry (vente or paiement), shared
 * between the truncated inline list (ClientDetailScreen) and the full-screen
 * paginated history (PagedHistoryScreen) so the design never diverges.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FactureRow(
    transaction: ClientTransaction,
    onLongPressPaiement: (ClientTransaction) -> Unit = {}
) {
    when (transaction.type) {
        "vente" -> {
            val montantPaye = transaction.montant_paye ?: 0.0
            val total = transaction.total ?: 0.0
            val statut = when {
                montantPaye >= total && total > 0 -> "Payé"
                montantPaye > 0 && montantPaye < total -> "Partiel"
                else -> "Impayé"
            }
            val statusColor = when (statut) {
                "Payé" -> DsColors.Success
                "Partiel" -> DsColors.Warning
                else -> DsColors.Danger
            }
            val statusColorLight = when (statut) {
                "Payé" -> DsColors.SuccessLight
                "Partiel" -> DsColors.WarningLight
                else -> DsColors.DangerLight
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DsShapes.large)
                    .background(DsColors.Surface)
                    .border(1.dp, DsColors.Border, DsShapes.large)
                    .padding(DsSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(38.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PointOfSale, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(DsSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Vente #${transaction.id} · ${formatOrderTime(transaction.created_at)}",
                        fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary
                    )
                    if (statut == "Partiel") {
                        Text("Payé: ${"%.2f".format(montantPaye)} DA", fontSize = DsTextSize.caption, color = DsColors.Warning)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${"%.2f".format(total)} DA", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Primary)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.clip(DsShapes.pill).background(statusColorLight).padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(statut, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = statusColor)
                    }
                }
            }
        }
        "paiement" -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DsShapes.large)
                    .background(DsColors.Surface)
                    .border(1.dp, DsColors.Border, DsShapes.large)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { onLongPressPaiement(transaction) }
                    )
                    .padding(DsSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(38.dp).clip(DsShapes.medium).background(DsColors.SuccessLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(DsSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Paiement · ${formatOrderTime(transaction.created_at)}",
                        fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary
                    )
                    transaction.note?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                }
                Text(
                    "+${"%.2f".format(transaction.amount ?: 0.0)} DA",
                    fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Success
                )
            }
        }
    }
}