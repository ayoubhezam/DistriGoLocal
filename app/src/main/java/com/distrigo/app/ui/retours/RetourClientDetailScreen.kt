package com.distrigo.app.ui.retours

import androidx.compose.runtime.*
import com.distrigo.app.data.model.Amount
import com.distrigo.app.data.model.RetourClient
import com.distrigo.app.data.model.RetourClientMotifs
import com.distrigo.app.data.model.numberLabel

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
    val detail by viewModel.retourDetail.collectAsState()
    val loaded = detail?.takeIf { it.id == retourSummary.id }
    val retour = loaded ?: retourSummary
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(retourSummary.id) { viewModel.loadRetourDetail(retourSummary.id) }

    // "Produit défectueux" and "Produit périmé" come back and go straight out again as pertes.
    val asPertes = RetourClientMotifs.resolve(retour.motif).perteType != null

    RetourDetailContent(
        numberLabel   = retour.numberLabel,
        partyLabel    = "Client",
        partyName     = retour.client_name,
        total         = retour.total,
        date          = retour.date,
        createdAt     = retour.created_at,
        motif         = retour.motif,
        stockEffect   = if (asPertes) "Enregistré en perte — le stock camion ne change pas" else "Remis dans le stock camion",
        note          = retour.note,
        lines         = loaded?.items?.map { RetourLine(it.id, it.product_name, it.unit_type, it.quantity, it.unit_price, it.total_price) },
        deleteEffects = listOf(
            if (asPertes) "Stock : les pertes liées à ce retour seront supprimées ; le stock camion ne change pas."
            else "Stock : les produits retournés seront retirés du stock camion.",
            "Solde : ${Amount.format(retour.total)} DA seront rajoutés au solde du client « ${retour.client_name} »."
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
