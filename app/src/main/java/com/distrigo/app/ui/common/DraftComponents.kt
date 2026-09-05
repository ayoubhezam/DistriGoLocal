package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.DraftBaseState
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import kotlinx.coroutines.launch

/**
 * One draft reduced to what a card needs to render it.
 *
 * Every flow that keeps Brouillons maps its own draft type to this, so the card, the sheet and the
 * Brouillons screen are written once and cannot drift between flows. Which fields lead the title,
 * and what a block is called, stay with the domain — those are the parts that genuinely differ.
 *
 * [blockedReason] doubles as the blocked flag: non-null means the record this draft edits can no
 * longer receive it, and the card says so instead of showing a time.
 */
data class DraftCardUi(
    val title        : String,
    val meta         : String,
    val total        : Double,
    val isEdit       : Boolean,
    val blockedReason: String? = null
) {
    val blocked: Boolean get() = blockedReason != null
}

/**
 * One draft, as a row. Shared by the FAB sheet and the Brouillons screen so the two cannot drift.
 *
 * The three kinds are separated before the text is read: a new record takes the accent and a
 * document glyph, an edit takes the warning tone and a pencil, and one whose record can no longer
 * receive it takes the danger tone and says why.
 */
@Composable
fun DraftRow(
    card: DraftCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    val (tint, container, icon) = when {
        card.blocked -> Triple(DsColors.Danger, DsColors.DangerLight, Icons.Default.WarningAmber)
        card.isEdit  -> Triple(DsColors.Warning, DsColors.WarningLight, Icons.Default.Edit)
        else         -> Triple(DsColors.Primary, DsColors.PrimaryLight, Icons.Default.Description)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(DsShapes.small).background(container),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                card.title,
                fontSize   = DsTextSize.body,
                fontWeight = FontWeight.SemiBold,
                color      = DsColors.TextPrimary,
                // Two lines, and nothing shares the row. The record number is the last thing in an
                // edit draft's title and the only thing that tells two of them apart, so it is
                // exactly what an ellipsis eats first. On a 360dp screen a priced draft leaves this
                // column 122dp; an "OBSOLÈTE" pill beside the title took 60 of them and cut
                // "Modification · bon #11" down to "Modification ·…" — the number gone, which is
                // the one thing the title exists to carry. The pill said nothing the red glyph and
                // the red reason line below do not already say, so it went rather than the number.
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                card.blockedReason ?: card.meta,
                fontSize   = DsTextSize.caption,
                // Carries the blocked state on its own now: bold and in the danger tone, and saying
                // *why* rather than merely that something is wrong.
                fontWeight = if (card.blocked) FontWeight.SemiBold else FontWeight.Normal,
                color      = if (card.blocked) DsColors.Danger else DsColors.TextSecondary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
        }

        if (trailing != null) trailing()
        else Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = DsColors.TextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** The words a flow puts on its "resume or start fresh" sheet. */
data class DraftSheetCopy(
    val titleOne : String,
    val titleMany: String,
    val subtitle : String,
    val startNew : String
)

/**
 * Shown when the "+" is tapped and at least one Brouillon exists.
 *
 * Drafts are listed directly rather than hidden behind a second tap — the point of the sheet is to
 * make resuming the easy path. The "start something new" action sits below a divider as a button
 * rather than one more row: it starts something instead of resuming something, and it must never
 * read as another draft. Choosing it leaves every draft untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <D> DraftsSheet(
    drafts    : List<D>,
    card      : (D) -> DraftCardUi,
    copy      : DraftSheetCopy,
    onResume  : (D) -> Unit,
    onStartNew: () -> Unit,
    onSeeAll  : () -> Unit,
    onDismiss : () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    // Four rows plus the header and the separated action sit inside a partially expanded sheet
    // without a drag; beyond that the sheet stops being a quick decision and the full screen is
    // the better place.
    val shown = drafts.take(MAX_SHEET_ROWS)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = DsColors.Surface
    ) {
        Column(
            Modifier.padding(
                start  = DsSpacing.lg,
                end    = DsSpacing.lg,
                bottom = DsSpacing.xxxl
            )
        ) {
            Text(
                if (drafts.size > 1) copy.titleMany else copy.titleOne,
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                copy.subtitle,
                fontSize = DsTextSize.bodySmall,
                color    = DsColors.TextSecondary
            )
            Spacer(Modifier.height(DsSpacing.sm))

            shown.forEach { draft ->
                HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
                DraftRow(card = card(draft), onClick = { onResume(draft) })
            }

            if (drafts.size > shown.size) {
                HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
                Text(
                    "Voir tout (${drafts.size}) →",
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = DsColors.Primary,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSeeAll)
                        .padding(vertical = DsSpacing.md)
                )
            }

            Spacer(Modifier.height(DsSpacing.md))
            HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
            Spacer(Modifier.height(DsSpacing.lg))

            OutlinedButton(
                onClick  = onStartNew,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = DsShapes.large,
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = DsColors.Primary),
                border   = androidx.compose.foundation.BorderStroke(1.5.dp, DsColors.Primary)
            ) {
                Text(copy.startNew, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private const val MAX_SHEET_ROWS = 4

/**
 * The words a flow puts on its conflict dialogs.
 *
 * [blockedTitle]/[blockedBody] cover the hard block a flow has, if it has one — Achats blocks on a
 * received bon, because reception already moved stock. A flow whose update path reverses its own
 * effects has no such state and never reaches that branch.
 */
data class DraftConflictCopy(
    val changedTitle: String,
    val changedBody : String,
    val resumeMine  : String,
    val useCommitted: String,
    val blockedTitle: String,
    val blockedBody : String,
    val viewRecord  : String,
    val deletedTitle: String,
    val deletedBody : String
)

/**
 * Shown when a resumed edit draft's record has moved underneath it.
 *
 * Every option here is a state somebody actually authored — the draft as it was left, or the record
 * as it now stands. There is deliberately no third, merged option: a silent three-way merge on an
 * invoice is the one outcome worse than losing the draft, because nobody would notice.
 */
@Composable
fun DraftConflictDialog(
    state         : DraftBaseState,
    copy          : DraftConflictCopy,
    onResume      : () -> Unit,
    onUseCommitted: () -> Unit,
    onDelete      : () -> Unit,
    onViewRecord  : () -> Unit,
    onDismiss     : () -> Unit
) {
    when (state) {
        DraftBaseState.CHANGED -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor   = DsColors.Surface,
            title = { Text(copy.changedTitle, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary) },
            text  = { Text(copy.changedBody, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = onResume) {
                    Text(copy.resumeMine, color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = onUseCommitted) {
                    Text(copy.useCommitted, color = DsColors.TextSecondary)
                }
            }
        )

        DraftBaseState.RECEIVED -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor   = DsColors.Surface,
            title = { Text(copy.blockedTitle, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary) },
            text  = { Text(copy.blockedBody, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = onViewRecord) {
                    Text(copy.viewRecord, color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDelete) {
                    Text("Supprimer le brouillon", color = DsColors.Danger)
                }
            }
        )

        DraftBaseState.DELETED -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor   = DsColors.Surface,
            title = { Text(copy.deletedTitle, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary) },
            text  = { Text(copy.deletedBody, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("Supprimer le brouillon", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Annuler", color = DsColors.TextSecondary) }
            }
        )

        // Resumable — the caller navigates straight into the form and never shows this.
        DraftBaseState.NONE, DraftBaseState.UNCHANGED -> Unit
    }
}

/**
 * The single gate every resume goes through, wherever it is triggered from — a FAB sheet, a
 * Brouillons card, or tapping "Modifier" on a record that already has a pending edit draft.
 *
 * Returns the action to invoke with a draft, and renders the conflict dialog itself when the
 * draft's record has moved. Keeping both halves together is the point: a second call site that
 * navigated straight into the form would silently skip the check, and the resulting overwrite is
 * exactly the failure this exists to prevent.
 *
 * A new-record draft always resolves to NONE and opens immediately — the dialog only ever concerns
 * edits.
 */
@Composable
fun <D> draftResumeAction(
    resolveBase   : suspend (D) -> DraftBaseState,
    conflictCopy  : (D) -> DraftConflictCopy,
    draftRowIdOf  : (D) -> Int,
    sourceRecordOf: (D) -> Int?,
    onDeleteDraft : (Int) -> Unit,
    onOpenForm    : (recordId: Int?, draftId: Int?) -> Unit,
    onViewRecord  : (Int) -> Unit
): (D) -> Unit {
    val scope = rememberCoroutineScope()
    var conflict by remember { mutableStateOf<Pair<D, DraftBaseState>?>(null) }

    conflict?.let { (draft, state) ->
        DraftConflictDialog(
            state = state,
            copy  = conflictCopy(draft),
            onResume       = { conflict = null; onOpenForm(sourceRecordOf(draft), draftRowIdOf(draft)) },
            onUseCommitted = {
                conflict = null
                onDeleteDraft(draftRowIdOf(draft))
                onOpenForm(sourceRecordOf(draft), null)
            },
            onDelete     = { conflict = null; onDeleteDraft(draftRowIdOf(draft)) },
            onViewRecord = { conflict = null; sourceRecordOf(draft)?.let(onViewRecord) },
            onDismiss    = { conflict = null }
        )
    }

    return { draft ->
        scope.launch {
            when (val state = resolveBase(draft)) {
                DraftBaseState.NONE,
                DraftBaseState.UNCHANGED -> onOpenForm(sourceRecordOf(draft), draftRowIdOf(draft))
                else                     -> conflict = draft to state
            }
        }
    }
}
