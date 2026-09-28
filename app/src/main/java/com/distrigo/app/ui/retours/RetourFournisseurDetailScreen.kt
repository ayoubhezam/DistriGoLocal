package com.distrigo.app.ui.retours

import androidx.compose.runtime.*
import com.distrigo.app.data.model.Amount
import com.distrigo.app.data.model.RetourFournisseur
import com.distrigo.app.data.model.RetourFournisseurMotifs
import com.distrigo.app.data.model.numberLabel

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
    val detail by viewModel.retourDetail.collectAsState()
    val loaded = detail?.takeIf { it.id == retourSummary.id }
    val retour = loaded ?: retourSummary
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(retourSummary.id) { viewModel.loadRetourDetail(retourSummary.id) }

    // "… refusé (perte)": the goods left the dépôt and were recorded as pertes as well.
    val withPertes = RetourFournisseurMotifs.resolve(retour.motif).perteType != null

    RetourDetailContent(
        numberLabel   = retour.numberLabel,
        partyLabel    = "Fournisseur",
        partyName     = retour.supplier_name,
        total         = retour.total,
        date          = retour.date,
        createdAt     = retour.created_at,
        motif         = retour.motif,
        stockEffect   = if (withPertes) "Sorti du stock dépôt, enregistré en perte" else "Sorti du stock dépôt",
        note          = retour.note,
        lines         = loaded?.items?.map { RetourLine(it.id, it.product_name, it.unit_type, it.quantity, it.unit_price, it.total_price) },
        deleteEffects = listOfNotNull(
            "Stock : les produits retournés seront remis dans le stock dépôt.",
            "Pertes : les pertes liées à ce retour seront supprimées.".takeIf { withPertes },
            "Solde : ${Amount.format(retour.total)} DA seront rajoutés à ce que vous devez au fournisseur « ${retour.supplier_name} »."
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
