package com.distrigo.app.ui.chargements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.ChargementDraft
import com.distrigo.app.ui.common.DraftBulkDeleteDialog
import com.distrigo.app.ui.common.DraftSelectionBarHost
import com.distrigo.app.ui.common.DraftSelectionSaver
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading

/**
 * The chargement Brouillons list.
 *
 * The same screen as the other three — selection mode, bulk delete, a per-row delete outside it —
 * with one omission: no filter bar. All three axes the shared [com.distrigo.app.ui.common.DraftFilterState]
 * offers are meaningless here. Type and état need a source record to edit and a block to be in, and
 * this flow is create-only; the party axis needs a supplier or a client, and a stock movement has
 * neither. A filter that cannot partition its list is a control that only ever wastes a tap.
 *
 * What the list shows is guaranteed by the query, not by this screen: the repository's
 * `observeDrafts` excludes every row belonging to the single-product "Modifier" card, so that
 * card's private editing state can never appear here.
 *
 * Filtering and selection are screen state, not view-model state.
 */
@Composable
fun ChargementBrouillonsScreen(
    viewModel: ChargementViewModel = hiltViewModel(),
    onBack   : () -> Unit,
    onResume : (ChargementDraft) -> Unit
) {
    val drafts by viewModel.drafts.collectAsState()

    var pendingDelete by remember { mutableStateOf<ChargementDraft?>(null) }
    var confirmBulk   by remember { mutableStateOf(false) }

    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected  by rememberSaveable(stateSaver = DraftSelectionSaver) {
        mutableStateOf(emptySet<Int>())
    }

    // A draft can vanish under the selection — a resumed one gets committed, or another screen
    // deletes it — so the selection is always intersected with what is actually on screen.
    val visibleIds   = remember(drafts) { drafts.map { it.id }.toSet() }
    val effectiveSel = remember(selected, visibleIds) { selected intersect visibleIds }

    fun exitSelection() { selecting = false; selected = emptySet() }

    BackHandler(enabled = selecting) { exitSelection() }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        if (selecting) {
            DsTopAppBar(
                title    = "${effectiveSel.size} sélectionné${if (effectiveSel.size > 1) "s" else ""}",
                subtitle = "sur ${drafts.size}",
                leading  = DsTopBarLeading.Close { exitSelection() }
            )
        } else {
            DsTopAppBar(
                title    = "Brouillons",
                subtitle = if (drafts.isEmpty()) "Aucun brouillon"
                           else "${drafts.size} ${if (drafts.size == 1) "brouillon" else "brouillons"}",
                leading  = DsTopBarLeading.Back(onBack)
            ) {
                if (drafts.isNotEmpty()) {
                    TextButton(onClick = { selecting = true }) {
                        Text("Sélectionner", color = DsColors.Primary, fontSize = DsTextSize.bodySmall)
                    }
                    Spacer(Modifier.width(DsSpacing.xs))
                }
            }
        }
        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

        if (drafts.isEmpty()) {
            EmptyChargementBrouillons(Modifier.weight(1f))
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start  = DsSpacing.lg,
                end    = DsSpacing.lg,
                top    = DsSpacing.md,
                bottom = DsSpacing.bottomNavClearance
            ),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            items(drafts, key = { it.id }) { draft ->
                val isSelected = draft.id in effectiveSel
                Card(
                    shape  = DsShapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) DsColors.PrimaryLight else DsColors.Surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = if (!isSelected) Modifier else
                        Modifier.border(1.dp, DsColors.Primary, DsShapes.large)
                ) {
                    Column(Modifier.padding(horizontal = DsSpacing.md)) {
                        ChargementDraftRow(
                            draft   = draft,
                            onClick = {
                                if (selecting) {
                                    selected = if (isSelected) selected - draft.id
                                               else            selected + draft.id
                                } else onResume(draft)
                            },
                            selected = if (selecting) isSelected else null,
                            trailing = if (selecting) ({}) else ({
                                IconButton(onClick = { pendingDelete = draft }) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Supprimer le brouillon",
                                        tint = DsColors.TextTertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            })
                        )
                    }
                }
            }
        }

        DraftSelectionBarHost(
            visible       = selecting,
            selectedCount = effectiveSel.size,
            totalCount    = drafts.size,
            onSelectAll   = { selected = visibleIds },
            onClearAll    = { selected = emptySet() },
            onDelete      = { confirmBulk = true }
        )
    }

    if (confirmBulk) {
        DraftBulkDeleteDialog(
            count      = effectiveSel.size,
            totalCount = drafts.size,
            onConfirm  = {
                viewModel.deleteDrafts(effectiveSel)
                confirmBulk = false
                exitSelection()
            },
            onDismiss  = { confirmBulk = false }
        )
    }

    pendingDelete?.let { draft ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor   = DsColors.Surface,
            title = { Text("Supprimer ce brouillon ?", fontWeight = FontWeight.Bold, color = DsColors.TextPrimary) },
            text  = {
                Text(
                    "« ${draft.cardTitle()} » sera définitivement perdu. Cette action est irréversible.",
                    fontSize = DsTextSize.bodySmall,
                    color    = DsColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteDraft(draft.id); pendingDelete = null }) {
                    Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Annuler", color = DsColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun EmptyChargementBrouillons(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(DsSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(DsShapes.large).background(DsColors.PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = DsColors.Primary,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(DsSpacing.lg))
        Text("Aucun brouillon", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Un mouvement non terminé est enregistré ici automatiquement, et vous pouvez le reprendre quand vous voulez.",
            fontSize  = DsTextSize.bodySmall,
            color     = DsColors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
