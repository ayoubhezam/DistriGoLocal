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
import com.distrigo.app.ui.common.relativeTime
import com.distrigo.app.core.text.UiText
import com.distrigo.app.R
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
internal fun PurchaseDraft.cardTitle(): UiText = when {
    sourceOrderId != null -> UiText.Res(R.string.purchases_draft_edit_title, sourceNumber ?: "#$sourceOrderId")
    supplierName != null  -> UiText.Raw(bidiIsolate(supplierName))
    else                  -> UiText.Res(R.string.purchases_draft_supplier_not_chosen)
}

/**
 * "ETS Belkacem · 5 produits · il y a 3 h"
 *
 * The supplier name is bidi-isolated because it is joined to French text here — see
 * [bidiIsolate] for what an Arabic name does to the separators around it otherwise.
 */
internal fun PurchaseDraft.cardMeta(): UiText = UiText.Joined(
    buildList {
        if (sourceOrderId != null && supplierName != null) add(UiText.Raw(bidiIsolate(supplierName)))
        add(UiText.Plural(R.plurals.common_products_count, itemCount))
        relativeTime(updatedAt)?.let { add(it) }
    }
)

private fun PurchaseDraft.blockedReason(): UiText? = when (blockState) {
    DraftBlock.RECEIVED -> UiText.Res(R.string.purchases_draft_blocked_received)
    DraftBlock.DELETED  -> UiText.Res(R.string.purchases_draft_blocked_deleted)
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
    titleOne  = UiText.Res(R.string.common_drafts_sheet_title_one),
    titleMany = UiText.Res(R.string.common_drafts_sheet_title_many),
    subtitle  = UiText.Res(R.string.purchases_drafts_sheet_subtitle),
    startNew  = UiText.Res(R.string.purchases_drafts_sheet_start_new)
)

private fun conflictCopy(draft: PurchaseDraft): DraftConflictCopy {
    val number = draft.sourceOrderId?.let { draft.sourceNumber ?: "#$it" } ?: ""
    return DraftConflictCopy(
        changedTitle = UiText.Res(R.string.purchases_draft_changed_title, number),
        changedBody  = UiText.Res(R.string.purchases_draft_changed_body, number),
        resumeMine   = UiText.Res(R.string.common_draft_resume_mine),
        useCommitted = UiText.Res(R.string.purchases_draft_use_committed),
        blockedTitle = UiText.Res(R.string.purchases_draft_blocked_received),
        blockedBody  = UiText.Res(R.string.purchases_draft_blocked_body, number),
        viewRecord   = UiText.Res(R.string.purchases_draft_view_record),
        deletedTitle = UiText.Res(R.string.purchases_draft_deleted_title),
        deletedBody  = UiText.Res(R.string.purchases_draft_deleted_body, number)
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
