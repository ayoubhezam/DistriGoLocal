package com.distrigo.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R
import androidx.compose.ui.res.pluralStringResource

/**
 * Survives rotation and process death, so a selection built up over a long list is not lost to a
 * screen turn. A `Set` is not `Parcelable`; the ids go across as a list.
 */
val DraftSelectionSaver: Saver<Set<Int>, Any> =
    listSaver(save = { it.toList() }, restore = { it.toSet() })

/**
 * The bulk-action bar, docked at the bottom while selecting.
 *
 * Bottom rather than in the top bar: "Supprimer" is the one irreversible thing on this screen, and
 * putting it under the thumb with a full word on it beats a bare icon in the far top corner, both
 * for reach and for saying plainly what it will do. The count rides on the label so the button
 * always states its own blast radius.
 *
 * The select-all control sits at the far end from Supprimer on purpose — they are the two actions
 * most costly to confuse, and "Tout sélectionner" immediately followed by a mis-tap on a delete
 * beside it is exactly the accident this layout is avoiding.
 */
@Composable
fun DraftSelectionBar(
    selectedCount: Int,
    totalCount   : Int,
    onSelectAll  : () -> Unit,
    onClearAll   : () -> Unit,
    onDelete     : () -> Unit,
    modifier     : Modifier = Modifier
) {
    val allSelected = selectedCount == totalCount && totalCount > 0

    Surface(
        modifier = modifier.fillMaxWidth(),
        color    = DsColors.Surface,
        shadowElevation = 12.dp
    ) {
        Column {
            HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                TextButton(
                    onClick = if (allSelected) onClearAll else onSelectAll,
                    shape   = DsShapes.medium
                ) {
                    Text(
                        stringResource(if (allSelected) R.string.common_action_deselect_all else R.string.common_action_select_all),
                        fontSize   = DsTextSize.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = DsColors.Primary
                    )
                }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick  = onDelete,
                    enabled  = selectedCount > 0,
                    shape    = DsShapes.medium,
                    modifier = Modifier.height(44.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = DsColors.Danger,
                        disabledContainerColor = DsColors.SurfaceSunken
                    )
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (selectedCount > 0) Color.White else DsColors.TextTertiary
                    )
                    Spacer(Modifier.width(DsSpacing.xs))
                    Text(
                        if (selectedCount > 0) stringResource(R.string.common_action_delete_count, selectedCount) else stringResource(R.string.common_action_delete),
                        fontSize   = DsTextSize.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (selectedCount > 0) Color.White else DsColors.TextTertiary
                    )
                }
            }
        }
    }
}

/** Slides the bar in from the bottom edge so entering selection mode reads as a mode change. */
@Composable
fun DraftSelectionBarHost(
    visible      : Boolean,
    selectedCount: Int,
    totalCount   : Int,
    onSelectAll  : () -> Unit,
    onClearAll   : () -> Unit,
    onDelete     : () -> Unit,
    modifier     : Modifier = Modifier
) {
    AnimatedVisibility(
        visible  = visible,
        enter    = slideInVertically { it },
        exit     = slideOutVertically { it },
        modifier = modifier
    ) {
        DraftSelectionBar(
            selectedCount = selectedCount,
            totalCount    = totalCount,
            onSelectAll   = onSelectAll,
            onClearAll    = onClearAll,
            onDelete      = onDelete
        )
    }
}

/**
 * The confirmation before a bulk delete.
 *
 * Deliberately two registers. Deleting some of the drafts is an ordinary destructive confirm.
 * Deleting *every* one of them gets a heavier dialog — a warning glyph, its own title, and a
 * confirm button that says "Tout supprimer" rather than just "Supprimer" — because that is the
 * action with nothing left to recover from, and it is one mis-tap away from "Tout sélectionner".
 *
 * Neither offers an undo, so the count is stated in the title where it cannot be missed, and the
 * body says plainly that the drafts do not come back.
 */
@Composable
fun DraftBulkDeleteDialog(
    count      : Int,
    totalCount : Int,
    onConfirm  : () -> Unit,
    onDismiss  : () -> Unit
) {
    val deletingAll = count >= totalCount && totalCount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = DsColors.Surface,
        icon = if (!deletingAll) null else {
            {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(DsShapes.pill)
                        .background(DsColors.DangerLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint     = DsColors.Danger,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        title = {
            Text(
                if (deletingAll) stringResource(R.string.common_drafts_delete_all_title)
                else             pluralStringResource(R.plurals.common_drafts_delete_title, count, count),
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary
            )
        },
        text = {
            Text(
                if (deletingAll)
                    pluralStringResource(R.plurals.common_drafts_delete_all_body, count, count)
                else
                    pluralStringResource(R.plurals.common_drafts_delete_body, count, count),
                fontSize = DsTextSize.bodySmall,
                color    = DsColors.TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(if (deletingAll) R.string.common_action_delete_all else R.string.common_action_delete),
                    color      = DsColors.Danger,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_action_cancel), color = DsColors.TextSecondary)
            }
        }
    )
}
