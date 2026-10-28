package com.distrigo.app.ui.ventes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.distrigo.app.data.model.DraftBlock
import com.distrigo.app.data.model.VenteDraft
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
// other form flows. What stays here is only what is specific to Dépôt Vente: which field leads a
// card, what a blocked vente is called, and the words on each dialog.

/**
 * The title a Brouillon card leads with.
 *
 * Edit drafts lead with the vente number, because that is the thing that tells two of them apart —
 * the client moves to the meta line rather than the other way round.
 */
internal fun VenteDraft.cardTitle(): UiText = when {
    sourceVenteId != null -> UiText.Res(R.string.ventes_draft_edit_title, sourceNumber ?: "#$sourceVenteId")
    clientName != null    -> UiText.Raw(bidiIsolate(clientName))
    else                  -> UiText.Res(R.string.common_draft_client_not_chosen)
}

/**
 * "سوسن · 5 produits · il y a 3 h"
 *
 * The client name is bidi-isolated because it is joined to French text here — see [bidiIsolate]
 * for what an Arabic name does to the separators around it otherwise.
 */
internal fun VenteDraft.cardMeta(): UiText = UiText.Joined(
    buildList {
        if (sourceVenteId != null && clientName != null) add(UiText.Raw(bidiIsolate(clientName)))
        add(UiText.Plural(R.plurals.common_products_count, itemCount))
        relativeTime(updatedAt)?.let { add(it) }
    }
)

/**
 * Dépôt Vente has only one blocked state. A delivered vente is still editable — `updateVente`
 * reverses every old line's stock before applying the new ones — so the only thing that can stop a
 * draft being applied is the vente being gone.
 */
private fun VenteDraft.blockedReason(): UiText? = when (blockState) {
    DraftBlock.DELETED  -> UiText.Res(R.string.ventes_draft_blocked_deleted)
    DraftBlock.RECEIVED,
    DraftBlock.NONE     -> null
}

/** How a Dépôt Vente draft renders as a card. */
internal fun VenteDraft.toCardUi() = DraftCardUi(
    title         = cardTitle(),
    meta          = cardMeta(),
    total         = total,
    isEdit        = isEdit,
    blockedReason = blockedReason()
)

@Composable
internal fun VenteDraftRow(
    draft: VenteDraft,
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
internal fun VenteBrouillonsSheet(
    drafts    : List<VenteDraft>,
    onResume  : (VenteDraft) -> Unit,
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
    subtitle  = UiText.Res(R.string.ventes_drafts_sheet_subtitle),
    startNew  = UiText.Res(R.string.ventes_drafts_sheet_start_new)
)

/**
 * The words on the conflict dialogs.
 *
 * `blockedTitle`/`blockedBody` are never reached: Dépôt Vente produces only DELETED and CHANGED.
 * They are filled in rather than left blank so that a future block — if one is ever introduced —
 * fails loudly in review instead of rendering an empty dialog.
 */
private fun conflictCopy(draft: VenteDraft): DraftConflictCopy {
    val number = draft.sourceVenteId?.let { draft.sourceNumber ?: "#$it" } ?: ""
    return DraftConflictCopy(
        changedTitle = UiText.Res(R.string.ventes_draft_changed_title, number),
        changedBody  = UiText.Res(R.string.ventes_draft_changed_body, number),
        resumeMine   = UiText.Res(R.string.common_draft_resume_mine),
        useCommitted = UiText.Res(R.string.ventes_draft_use_committed),
        blockedTitle = UiText.Res(R.string.ventes_draft_blocked_title),
        blockedBody  = UiText.Res(R.string.ventes_draft_blocked_body, number),
        viewRecord   = UiText.Res(R.string.ventes_draft_view_record),
        deletedTitle = UiText.Res(R.string.ventes_draft_deleted_title),
        deletedBody  = UiText.Res(R.string.ventes_draft_deleted_body, number)
    )
}

/**
 * The single gate every Dépôt Vente resume goes through — see the generic
 * `com.distrigo.app.ui.common.draftResumeAction` for why both halves stay together.
 */
@Composable
internal fun venteDraftResumeAction(
    viewModel  : VenteViewModel,
    onOpenForm : (venteId: Int?, draftId: Int?) -> Unit,
    onViewVente: (Int) -> Unit
): (VenteDraft) -> Unit = genericDraftResumeAction(
    resolveBase    = { viewModel.resolveDraftBase(it) },
    conflictCopy   = ::conflictCopy,
    draftRowIdOf   = { it.id },
    sourceRecordOf = { it.sourceVenteId },
    onDeleteDraft  = { viewModel.deleteDraft(it) },
    onOpenForm     = onOpenForm,
    onViewRecord   = onViewVente
)
