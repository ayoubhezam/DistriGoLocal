package com.distrigo.app.ui.chargements

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.distrigo.app.data.model.ChargementDraft
import com.distrigo.app.ui.common.DraftCardUi
import com.distrigo.app.ui.common.DraftSheetCopy
import com.distrigo.app.ui.common.DraftsSheet
import com.distrigo.app.ui.common.relativeTime
import com.distrigo.app.core.text.UiText
import com.distrigo.app.R

// Everything that draws a Brouillon lives in ui/common/DraftComponents.kt and is shared with the
// other three form flows. What stays here is only what is specific to a stock movement.

/**
 * The title a Brouillon card leads with.
 *
 * A chargement has no counterparty — no supplier, no client — so there is no name to lead with and
 * nothing that distinguishes two drafts but their contents. The count is therefore the title, and
 * the meta line carries the age.
 */
internal fun ChargementDraft.cardTitle(): UiText = UiText.Plural(R.plurals.chargements_draft_title, itemCount)

/** "il y a 12 min". */
internal fun ChargementDraft.cardMeta(): UiText = relativeTime(updatedAt) ?: UiText.Res(R.string.common_draft)

/**
 * How a chargement draft renders as a card.
 *
 * `total` is 0 and the row is given no `totalText` by its caller: a stock movement has no monetary
 * value, and showing "0,00 DA" on every card would be a number that is always wrong. `isEdit` and
 * `blockedReason` are constant for the same reason they are in Tournée Vente — nothing to edit,
 * nothing that can block.
 */
internal fun ChargementDraft.toCardUi() = DraftCardUi(
    title         = cardTitle(),
    meta          = cardMeta(),
    total         = 0.0,
    isEdit        = false,
    blockedReason = null
)

@Composable
internal fun ChargementDraftRow(
    draft: ChargementDraft,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    trailing: @Composable (() -> Unit)? = null
) = com.distrigo.app.ui.common.DraftRow(
    card     = draft.toCardUi(),
    onClick  = onClick,
    modifier = modifier,
    selected = selected,
    trailing = trailing
)

@Composable
internal fun ChargementBrouillonsSheet(
    drafts    : List<ChargementDraft>,
    onResume  : (ChargementDraft) -> Unit,
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
    subtitle  = UiText.Res(R.string.chargements_drafts_sheet_subtitle),
    startNew  = UiText.Res(R.string.chargements_drafts_sheet_start_new)
)
