package com.distrigo.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.distrigo.app.R
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.format.LocalMoneyFormatter

/**
 * « Avance » and the amount paid above the total, under the « Montant payé » field of a vente, a tournée sale or a
 * bon. In the blue the party's solde shows an advance in (SoldeCell). It takes the place of « Réglé ✓ », which an
 * overpayment used to show: a typing slip such as 150 050 DA for 1 500,50 looked settled (audit 2026-11-09, FIN-01).
 */
@Composable
fun PaidAdvanceRow(surplus: Double, labelSize: TextUnit = DsTextSize.caption) {
    val money = LocalMoneyFormatter.current
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.common_paid_advance), fontSize = labelSize, color = DsColors.Primary)
        Text(money.da(surplus), fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Primary)
    }
}

/**
 * Asked before saving a document whose amount paid is above its total. Paying ahead is allowed — the surplus is an
 * advance on the party's solde — but it is also what a slip of the keyboard looks like, so it is said once more
 * before it is recorded. « Corriger » goes back to the field; the other button saves as typed.
 */
@Composable
fun PaidAdvanceDialog(surplus: Double, onKeep: () -> Unit, onFix: () -> Unit) {
    val money = LocalMoneyFormatter.current
    AlertDialog(
        onDismissRequest = onFix,
        icon             = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = DsColors.Warning) },
        title            = { Text(stringResource(R.string.common_paid_advance_title)) },
        text             = { Text(stringResource(R.string.common_paid_advance_body, money.da(surplus)), color = DsColors.TextPrimary) },
        confirmButton    = {
            TextButton(onClick = onKeep) {
                Text(stringResource(R.string.common_paid_advance_keep), color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton    = { TextButton(onClick = onFix) { Text(stringResource(R.string.common_action_fix)) } },
        containerColor   = DsColors.Surface
    )
}
