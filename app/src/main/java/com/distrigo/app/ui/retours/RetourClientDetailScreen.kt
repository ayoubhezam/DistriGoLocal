package com.distrigo.app.ui.retours

import androidx.compose.runtime.*
import com.distrigo.app.data.model.RetourClient
import com.distrigo.app.data.model.RetourClientMotifs
import com.distrigo.app.data.model.numberLabel
import com.distrigo.app.ui.format.LocalMoneyFormatter
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

/**
 * A client return, read-only (RetourDetailContent). Deleting it takes back what it did: the goods it
 * put back in the camion leave it again — or, for a defective or expired product, the pertes it
 * recorded go away and the camion is unchanged — and its value is owed by the client again.
 */
@Composable
fun RetourClientDetailScreen(
    retourSummary : RetourClient,
    viewModel     : RetourClientViewModel,
    onBack        : () -> Unit,
    onDeleted     : () -> Unit
) {
    val money = LocalMoneyFormatter.current
    val detail by viewModel.retourDetail.collectAsState()
    val loaded = detail?.takeIf { it.id == retourSummary.id }
    val retour = loaded ?: retourSummary
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(retourSummary.id) { viewModel.loadRetourDetail(retourSummary.id) }

    // "Produit défectueux" and "Produit périmé" come back and go straight out again as pertes.
    // Each line's motif decides its own effect; a return from before v61 has one for all its lines.
    val motifs = loaded?.items?.map { it.motif ?: retour.motif } ?: listOf(retour.motif)
    val perteLines = motifs.count { RetourClientMotifs.resolve(it).perteType != null }
    val asPertes = perteLines == motifs.size
    val mixedEffects = perteLines in 1 until motifs.size

    RetourDetailContent(
        numberLabel   = retour.numberLabel,
        partyLabel    = stringResource(R.string.common_label_client),
        partyName     = retour.client_name,
        total         = retour.total,
        date          = retour.date,
        createdAt     = retour.created_at,
        motif         = retour.motif,
        stockEffect   = when {
            mixedEffects -> stringResource(R.string.retours_client_effect_mixed)
            asPertes     -> stringResource(R.string.retours_client_effect_pertes)
            else         -> stringResource(R.string.retours_client_effect_stock)
        },
        note          = retour.note,
        lines         = loaded?.items?.map { RetourLine(it.id, it.product_name, it.unit_type, it.quantity, it.unit_price, it.total_price, it.motif ?: retour.motif) },
        deleteEffects = listOf(
            if (mixedEffects) stringResource(R.string.retours_client_delete_mixed)
            else if (asPertes) stringResource(R.string.retours_client_delete_pertes)
            else stringResource(R.string.retours_client_delete_stock),
            stringResource(R.string.retours_client_delete_balance, money.da(retour.total), retour.client_name)
        ),
        deleting       = deleting,
        deleteError    = deleteError,
        onDelete       = {
            deleting = true
            viewModel.deleteRetour(
                id        = retour.id,
                onSuccess = { deleting = false; onDeleted() },
                onError   = { deleting = false; deleteError = it }
            )
        },
        onDismissError = { deleteError = null },
        onBack         = onBack
    )
}
