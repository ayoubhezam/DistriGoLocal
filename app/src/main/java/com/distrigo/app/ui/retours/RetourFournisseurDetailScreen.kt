package com.distrigo.app.ui.retours

import androidx.compose.runtime.*
import com.distrigo.app.data.model.RetourFournisseur
import com.distrigo.app.data.model.RetourFournisseurMotifs
import com.distrigo.app.data.model.numberLabel
import com.distrigo.app.ui.format.LocalMoneyFormatter
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

/**
 * A supplier return, read-only (RetourDetailContent). Deleting it takes back what it did: the goods
 * it sent out of the dépôt come back into it, the pertes a refused return recorded go away, and its
 * value is owed to the supplier again.
 */
@Composable
fun RetourFournisseurDetailScreen(
    retourSummary : RetourFournisseur,
    viewModel     : RetourFournisseurViewModel,
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

    // "… refusé (perte)": the goods left the dépôt and were recorded as pertes as well.
    // Each line's motif decides its own effect; a return from before v61 has one for all its lines.
    val motifs = loaded?.items?.map { it.motif ?: retour.motif } ?: listOf(retour.motif)
    val perteLines = motifs.count { RetourFournisseurMotifs.resolve(it).perteType != null }
    val withPertes = perteLines == motifs.size
    val mixedEffects = perteLines in 1 until motifs.size

    RetourDetailContent(
        numberLabel   = retour.numberLabel,
        partyLabel    = stringResource(R.string.common_label_supplier),
        partyName     = retour.supplier_name,
        total         = retour.total,
        date          = retour.date,
        createdAt     = retour.created_at,
        motif         = retour.motif,
        stockEffect   = when {
            mixedEffects -> stringResource(R.string.retours_supplier_effect_mixed)
            withPertes   -> stringResource(R.string.retours_supplier_effect_pertes)
            else         -> stringResource(R.string.retours_supplier_effect_stock)
        },
        note          = retour.note,
        lines         = loaded?.items?.map { RetourLine(it.id, it.product_name, it.unit_type, it.quantity, it.unit_price, it.total_price, it.motif ?: retour.motif) },
        deleteEffects = listOfNotNull(
            stringResource(R.string.retours_supplier_delete_stock),
            stringResource(R.string.retours_supplier_delete_pertes).takeIf { withPertes || mixedEffects },
            stringResource(R.string.retours_supplier_delete_balance, money.da(retour.total), retour.supplier_name)
        ),
        deleting       = deleting,
        deleteError    = deleteError,
        onDelete       = {
            deleting = true
            viewModel.deleteRetour(
                id         = retour.id,
                supplierId = retour.supplier_id,
                onSuccess  = { deleting = false; onDeleted() },
                onError    = { deleting = false; deleteError = it }
            )
        },
        onDismissError = { deleteError = null },
        onBack         = onBack
    )
}
