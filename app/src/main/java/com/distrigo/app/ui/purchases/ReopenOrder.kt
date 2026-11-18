package com.distrigo.app.ui.purchases

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.distrigo.app.R
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsTextSize

/** What « change this bon » means for a bon: a received one is reopened first, never edited in place. */
internal enum class BonChange { EDIT, REOPEN }

/**
 * A received bon has moved stock and purchase prices: rewriting its lines would leave them as they were, so the
 * repository refuses it (FIN-06). Every place that offers to change a bon asks here, so none offers an edit that
 * cannot be saved (the Achats list's long-press did, until 2026-11-18).
 */
internal fun bonChange(status: String?): BonChange = if (status == "received") BonChange.REOPEN else BonChange.EDIT

/** « Rouvrir le bon ? »: the reception is undone (stock, purchase price), then the bon can be edited. */
@Composable
internal fun ReopenOrderDialog(onConfirm: () -> Unit, onDismiss: () -> Unit, dismissLabel: String = stringResource(R.string.common_action_cancel)) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.purchases_reopen_title)) },
        text  = { Text(stringResource(R.string.purchases_reopen_body), fontSize = DsTextSize.bodySmall) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.purchases_reopen), color = DsColors.Warning, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
        containerColor    = DsColors.Surface,
        titleContentColor = DsColors.TextPrimary,
        textContentColor  = DsColors.TextSecondary
    )
}
