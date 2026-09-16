package com.distrigo.app.ui.purchases

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.distrigo.app.data.model.DraftBlock
import com.distrigo.app.data.model.PurchaseDraft
import com.distrigo.app.ui.common.DraftCardUi
import com.distrigo.app.ui.common.DraftConflictCopy
import com.distrigo.app.ui.common.DraftSheetCopy
import com.distrigo.app.ui.common.DraftsSheet
import com.distrigo.app.ui.common.bidiIsolate
import com.distrigo.app.ui.common.formatRelativeFr
import com.distrigo.app.ui.common.draftResumeAction as genericDraftResumeAction

// Everything that draws a Brouillon lives in ui/common/DraftComponents.kt and is shared with the
// other form flows. What stays here is only what is specific to Achats: which field leads a card,
// what a blocked bon is called, and the words on each dialog.

/**
 * The title a Brouillon card leads with.
 *
 * Edit drafts lead with the bon number, because that is the thing that tells two of them apart —
 * the supplier moves to the meta line rather than the other way round.
 */
internal fun PurchaseDraft.cardTitle(): String = when {
    sourceOrderId != null -> "Modification · bon ${sourceNumber ?: "#$sourceOrderId"}"
    supplierName != null  -> bidiIsolate(supplierName)
    else                  -> "Fournisseur non choisi"
}

/**
 * "ETS Belkacem · 5 produits · il y a 3 h"
 *
 * The supplier name is bidi-isolated because it is joined to French text here — see
 * [bidiIsolate] for what an Arabic name does to the separators around it otherwise.
 */
internal fun PurchaseDraft.cardMeta(): String {
    val parts = buildList {
        if (sourceOrderId != null && supplierName != null) add(bidiIsolate(supplierName))
        add(if (itemCount <= 1) "$itemCount produit" else "$itemCount produits")
        formatRelativeFr(updatedAt)?.let { add(it) }
    }
    return parts.joinToString(" · ")
}

private fun PurchaseDraft.blockedReason(): String? = when (blockState) {
    DraftBlock.RECEIVED -> "Bon déjà réceptionné"
    DraftBlock.DELETED  -> "Bon supprimé"
    DraftBlock.NONE     -> null
}

/** How an Achats draft renders as a card. */
internal fun PurchaseDraft.toCardUi() = DraftCardUi(
    title         = cardTitle(),
    meta          = cardMeta(),
    total         = total,
    isEdit        = isEdit,
    blockedReason = blockedReason()
)

@Composable
internal fun DraftRow(
    draft: PurchaseDraft,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    totalText: String? = null,
    selected: Boolean? = null,
    trailing: @Composable (() -> Unit)? = null
) = com.distrigo.app.ui.common.DraftRow(
    card      = draft.toCardUi(),
    onClick   = onClick,
    modifier  = modifier,
    totalText = totalText,
    selected  = selected,
    trailing  = trailing
)

@Composable
internal fun BrouillonsSheet(
    drafts    : List<PurchaseDraft>,
    onResume  : (PurchaseDraft) -> Unit,
    onStartNew: () -> Unit,
    onSeeAll  : () -> Unit,
    onDismiss : () -> Unit
) = DraftsSheet(
    drafts     = drafts,
    card       = { it.toCardUi() },
    copy       = SHEET_COPY,
    onResume   = onResume,
    onStartNew = onStartNew,
    onSeeAll   = onSeeAll,
    onDismiss  = onDismiss
)

private val SHEET_COPY = DraftSheetCopy(
    titleOne  = "Brouillon en cours",
    titleMany = "Brouillons en cours",
    subtitle  = "Reprenez un brouillon ou commencez un nouveau bon.",
    startNew  = "Commencer un nouveau bon"
)

private fun conflictCopy(draft: PurchaseDraft): DraftConflictCopy {
    val bon = draft.sourceOrderId?.let { "bon ${draft.sourceNumber ?: "#$it"}" } ?: "bon"
    return DraftConflictCopy(
        changedTitle = "Le $bon a changé",
        changedBody  = "Ce $bon a été modifié depuis que vous avez commencé cette modification. " +
                       "Vos changements n'ont pas encore été enregistrés.",
        resumeMine   = "Reprendre mes modifications",
        useCommitted = "Repartir du bon actuel",
        blockedTitle = "Bon déjà réceptionné",
        blockedBody  = "Le $bon a été réceptionné et le stock a déjà été mis à jour. " +
                       "Cette modification ne peut plus être appliquée.",
        viewRecord   = "Voir le bon",
        deletedTitle = "Bon introuvable",
        deletedBody  = "Le $bon a été supprimé. Il n'y a plus rien à modifier."
    )
}

/**
 * The single gate every Achats resume goes through — see the generic
 * `com.distrigo.app.ui.common.draftResumeAction` for why both halves stay together.
 */
@Composable
internal fun draftResumeAction(
    viewModel  : PurchaseViewModel,
    onOpenForm : (orderId: Int?, draftId: Int?) -> Unit,
    onViewOrder: (Int) -> Unit
): (PurchaseDraft) -> Unit = genericDraftResumeAction(
    resolveBase    = { viewModel.resolveDraftBase(it) },
    conflictCopy   = ::conflictCopy,
    draftRowIdOf   = { it.id },
    sourceRecordOf = { it.sourceOrderId },
    onDeleteDraft  = { viewModel.deleteDraft(it) },
    onOpenForm     = onOpenForm,
    onViewRecord   = onViewOrder
)
