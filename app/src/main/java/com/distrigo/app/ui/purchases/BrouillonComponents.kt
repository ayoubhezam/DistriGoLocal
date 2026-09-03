package com.distrigo.app.ui.purchases

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
import androidx.compose.ui.unit.sp
import com.distrigo.app.data.model.DraftBaseState
import com.distrigo.app.data.model.DraftBlock
import com.distrigo.app.data.model.PurchaseDraft
import com.distrigo.app.ui.common.formatRelativeFr
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import kotlinx.coroutines.launch

/**
 * The title a Brouillon card leads with.
 *
 * Edit drafts lead with the bon number, because that is the thing that tells two of them apart —
 * the supplier moves to the meta line rather than the other way round.
 */
internal fun PurchaseDraft.cardTitle(): String = when {
    sourceOrderId != null -> "Modification · bon #$sourceOrderId"
    supplierName != null  -> supplierName
    else                  -> "Fournisseur non choisi"
}

/** "ETS Belkacem · 5 produits · il y a 3 h" */
internal fun PurchaseDraft.cardMeta(): String {
    val parts = buildList {
        if (sourceOrderId != null && supplierName != null) add(supplierName)
        add(if (itemCount <= 1) "$itemCount produit" else "$itemCount produits")
        formatRelativeFr(updatedAt)?.let { add(it) }
    }
    return parts.joinToString(" · ")
}

/**
 * One draft, as a row. Shared by the FAB sheet and the Brouillons screen so the two cannot drift.
 *
 * The three kinds are separated before the text is read: a new purchase takes the accent and a
 * document glyph, an edit takes the warning tone and a pencil, and one whose bon has been received
 * or deleted takes the danger tone and says so instead of showing a time.
 */
@Composable
internal fun DraftRow(
    draft: PurchaseDraft,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    val blocked = draft.blockState != DraftBlock.NONE

    val (tint, container, icon) = when {
        blocked                    -> Triple(DsColors.Danger, DsColors.DangerLight, Icons.Default.WarningAmber)
        draft.sourceOrderId != null -> Triple(DsColors.Warning, DsColors.WarningLight, Icons.Default.Edit)
        else                       -> Triple(DsColors.Primary, DsColors.PrimaryLight, Icons.Default.Description)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    draft.cardTitle(),
                    fontSize   = DsTextSize.body,
                    fontWeight = FontWeight.SemiBold,
                    color      = DsColors.TextPrimary,
                    // Two lines rather than one: the bon number is the last thing in an edit
                    // draft's title and the only thing that tells two of them apart, so it is
                    // exactly what a single-line ellipsis eats once the price, the delete button
                    // and the OBSOLÈTE badge have taken their width.
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f, fill = false)
                )
                if (blocked) {
                    Spacer(Modifier.width(DsSpacing.xs))
                    Box(
                        modifier = Modifier
                            .clip(DsShapes.pill)
                            .background(DsColors.DangerLight)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "OBSOLÈTE",
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color      = DsColors.Danger
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (blocked) blockedReason(draft) else draft.cardMeta(),
                fontSize = DsTextSize.caption,
                color    = if (blocked) DsColors.Danger else DsColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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

private fun blockedReason(draft: PurchaseDraft): String = when (draft.blockState) {
    DraftBlock.RECEIVED -> "Bon déjà réceptionné"
    DraftBlock.DELETED  -> "Bon supprimé"
    DraftBlock.NONE     -> ""
}

/**
 * Shown when the "+" is tapped and at least one Brouillon exists.
 *
 * Drafts are listed directly rather than hidden behind a second tap — the point of the sheet is to
 * make resuming the easy path. "Commencer un nouveau bon" sits below a divider as a button rather
 * than a fifth row: it starts something instead of resuming something, and it must never read as
 * one more draft. Choosing it leaves every draft untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrouillonsSheet(
    drafts    : List<PurchaseDraft>,
    onResume  : (PurchaseDraft) -> Unit,
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
                if (drafts.size > 1) "Brouillons en cours" else "Brouillon en cours",
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Reprenez un brouillon ou commencez un nouveau bon.",
                fontSize = DsTextSize.bodySmall,
                color    = DsColors.TextSecondary
            )
            Spacer(Modifier.height(DsSpacing.sm))

            shown.forEach { draft ->
                HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
                DraftRow(draft = draft, onClick = { onResume(draft) })
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
                Text("Commencer un nouveau bon", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private const val MAX_SHEET_ROWS = 4

/**
 * Shown when a resumed edit draft's bon has moved underneath it.
 *
 * Every option here is a state somebody actually authored — the draft as it was left, or the bon as
 * it now stands. There is deliberately no third, merged option: a silent three-way merge on a
 * purchase invoice is the one outcome worse than losing the draft, because nobody would notice.
 */
@Composable
internal fun DraftConflictDialog(
    draft   : PurchaseDraft,
    state   : DraftBaseState,
    onResume: () -> Unit,
    onUseCommitted: () -> Unit,
    onDelete: () -> Unit,
    onViewOrder: () -> Unit,
    onDismiss: () -> Unit
) {
    val bon = draft.sourceOrderId?.let { "bon #$it" } ?: "bon"

    when (state) {
        DraftBaseState.CHANGED -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor   = DsColors.Surface,
            title = { Text("Le $bon a changé", fontWeight = FontWeight.Bold, color = DsColors.TextPrimary) },
            text  = {
                Text(
                    "Ce $bon a été modifié depuis que vous avez commencé cette modification. " +
                        "Vos changements n'ont pas encore été enregistrés.",
                    fontSize = DsTextSize.bodySmall,
                    color    = DsColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = onResume) {
                    Text("Reprendre mes modifications", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = onUseCommitted) {
                    Text("Repartir du bon actuel", color = DsColors.TextSecondary)
                }
            }
        )

        DraftBaseState.RECEIVED -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor   = DsColors.Surface,
            title = { Text("Bon déjà réceptionné", fontWeight = FontWeight.Bold, color = DsColors.TextPrimary) },
            text  = {
                Text(
                    "Le $bon a été réceptionné et le stock a déjà été mis à jour. " +
                        "Cette modification ne peut plus être appliquée.",
                    fontSize = DsTextSize.bodySmall,
                    color    = DsColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = onViewOrder) {
                    Text("Voir le bon", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
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
            title = { Text("Bon introuvable", fontWeight = FontWeight.Bold, color = DsColors.TextPrimary) },
            text  = {
                Text(
                    "Le $bon a été supprimé. Il n'y a plus rien à modifier.",
                    fontSize = DsTextSize.bodySmall,
                    color    = DsColors.TextSecondary
                )
            },
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
 * The single gate every resume goes through, wherever it is triggered from — the FAB sheet, a
 * Brouillons card, or tapping "Modifier" on a bon that already has a pending edit draft.
 *
 * Returns the action to invoke with a draft, and renders the conflict dialog itself when the
 * draft's bon has moved. Keeping both halves together is the point: a second call site that
 * navigated straight into the form would silently skip the check, and the resulting overwrite is
 * exactly the failure A.3 exists to prevent.
 *
 * A new-purchase draft always resolves to NONE and opens immediately — the dialog only ever
 * concerns edits.
 */
@Composable
internal fun draftResumeAction(
    viewModel  : PurchaseViewModel,
    onOpenForm : (orderId: Int?, draftId: Int?) -> Unit,
    onViewOrder: (Int) -> Unit
): (PurchaseDraft) -> Unit {
    val scope = rememberCoroutineScope()
    var conflict by remember { mutableStateOf<Pair<PurchaseDraft, DraftBaseState>?>(null) }

    conflict?.let { (draft, state) ->
        DraftConflictDialog(
            draft = draft,
            state = state,
            onResume       = { conflict = null; onOpenForm(draft.sourceOrderId, draft.id) },
            onUseCommitted = {
                conflict = null
                viewModel.deleteDraft(draft.id)
                onOpenForm(draft.sourceOrderId, null)
            },
            onDelete    = { conflict = null; viewModel.deleteDraft(draft.id) },
            onViewOrder = { conflict = null; draft.sourceOrderId?.let(onViewOrder) },
            onDismiss   = { conflict = null }
        )
    }

    return { draft ->
        scope.launch {
            when (val state = viewModel.resolveDraftBase(draft)) {
                DraftBaseState.NONE,
                DraftBaseState.UNCHANGED -> onOpenForm(draft.sourceOrderId, draft.id)
                else                     -> conflict = draft to state
            }
        }
    }
}
