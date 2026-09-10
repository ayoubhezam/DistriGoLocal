package com.distrigo.app.ui.tournees

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.distrigo.app.data.model.TourneeVenteDraft
import com.distrigo.app.ui.common.DraftCardUi
import com.distrigo.app.ui.common.DraftSheetCopy
import com.distrigo.app.ui.common.DraftsSheet
import com.distrigo.app.ui.common.bidiIsolate
import com.distrigo.app.ui.common.formatRelativeFr

// Everything that draws a Brouillon lives in ui/common/DraftComponents.kt and is shared with the
// other two form flows. What stays here is only what is specific to a van sale: which field leads
// a card, and the words on the sheet.

/**
 * The title a Brouillon card leads with.
 *
 * Always the client, never a record number — the tournée form is create-only, so there is no
 * committed vente for a draft to be a modification of, and no "Modification · vente #12" branch to
 * write. Isolated because most client names in this catalogue are Arabic and this string is
 * interpolated into French sentences elsewhere; see [bidiIsolate].
 */
internal fun TourneeVenteDraft.cardTitle(): String =
    clientName?.let { bidiIsolate(it) } ?: "Client non choisi"

/** "3 produits · il y a 12 min" */
internal fun TourneeVenteDraft.cardMeta(): String {
    val parts = buildList {
        add(if (itemCount <= 1) "$itemCount produit" else "$itemCount produits")
        formatRelativeFr(updatedAt)?.let { add(it) }
    }
    return parts.joinToString(" · ")
}

/**
 * How a van-sale draft renders as a card.
 *
 * `isEdit` is constantly false and `blockedReason` constantly null, which between them make the
 * card the plain kind: the ordinary glyph, no OBSOLÈTE state, no red reason line. Neither can
 * arise without a source record, and this flow has none.
 */
internal fun TourneeVenteDraft.toCardUi() = DraftCardUi(
    title         = cardTitle(),
    meta          = cardMeta(),
    total         = total,
    isEdit        = false,
    blockedReason = null
)

@Composable
internal fun TourneeVenteDraftRow(
    draft: TourneeVenteDraft,
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
internal fun TourneeVenteBrouillonsSheet(
    drafts    : List<TourneeVenteDraft>,
    onResume  : (TourneeVenteDraft) -> Unit,
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
    subtitle  = "Reprenez un brouillon ou commencez une nouvelle vente.",
    startNew  = "Commencer une nouvelle vente"
)

/**
 * There is no `tourneeVenteDraftResumeAction` to go with the other two flows'.
 *
 * That gate exists to resolve a draft against the record it edits — is it still there, has it
 * moved, is it still editable — and to raise the right dialog for each answer. A van-sale draft
 * edits nothing, so every resume is unconditional: open the form on that draft. The Brouillons
 * screen and the FAB sheet both call straight through.
 */
