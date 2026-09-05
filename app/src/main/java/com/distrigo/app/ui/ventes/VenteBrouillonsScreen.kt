package com.distrigo.app.ui.ventes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterListOff
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
import com.distrigo.app.data.model.DraftBlock
import com.distrigo.app.data.model.VenteDraft
import com.distrigo.app.ui.common.DraftBulkDeleteDialog
import com.distrigo.app.ui.common.DraftFilterBar
import com.distrigo.app.ui.common.DraftFilterCopy
import com.distrigo.app.ui.common.DraftFilterFacts
import com.distrigo.app.ui.common.DraftFilterSheet
import com.distrigo.app.ui.common.DraftFilterState
import com.distrigo.app.ui.common.DraftSelectionBarHost
import com.distrigo.app.ui.common.DraftSelectionSaver
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.suppliers.formatDZD

/** What a draft reports to the filter. */
private fun VenteDraft.filterFacts() = DraftFilterFacts(
    isEdit    = isEdit,
    blocked   = blockState != DraftBlock.NONE,
    partyName = clientName
)

private val FILTER_COPY = DraftFilterCopy(
    partyLabel = "Client",
    partyAll   = "Tous les clients"
)

/**
 * The Dépôt Vente Brouillons list — its own screen, separate from the Ventes list, mirroring the
 * Achats one.
 *
 * Tapping a card resumes that draft directly; there is no confirmation, because the card *is* the
 * confirmation. Deletion is the only destructive action here and it asks first, since a draft is
 * unrecoverable and nothing expires on its own.
 *
 * The screen has two modes. Ordinarily a tap resumes and the row carries its own delete button.
 * In selection mode a tap ticks the row instead, resuming is unreachable, and deletion moves to a
 * single bulk action at the bottom — so the destructive control is never adjacent to the one that
 * merely opens something.
 *
 * Filtering and selection are screen state, not view-model state: neither touches what is stored
 * or the order it arrives in, and both are gone the moment the screen is.
 */
@Composable
fun VenteBrouillonsScreen(
    viewModel: VenteViewModel = hiltViewModel(),
    onBack   : () -> Unit,
    onResume : (VenteDraft) -> Unit
) {
    val drafts by viewModel.drafts.collectAsState()

    var pendingDelete by remember { mutableStateOf<VenteDraft?>(null) }
    var showFilters   by remember { mutableStateOf(false) }
    var confirmBulk   by remember { mutableStateOf(false) }

    var filter    by rememberSaveable(stateSaver = DraftFilterState.Saver) {
        mutableStateOf(DraftFilterState())
    }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected  by rememberSaveable(stateSaver = DraftSelectionSaver) {
        mutableStateOf(emptySet<Int>())
    }

    val shown = remember(drafts, filter) {
        drafts.filter { filter.accepts(it.filterFacts()) }
    }
    val partyNames = remember(drafts) {
        drafts.mapNotNull { it.clientName }.distinct().sorted()
    }

    // A draft can vanish under the selection — a resumed one gets committed, or another screen
    // deletes it — so the selection is always intersected with what is actually on screen. Without
    // this the count could outrun the list and "Tout sélectionner" would never look satisfied.
    val visibleIds   = remember(shown) { shown.map { it.id }.toSet() }
    val effectiveSel = remember(selected, visibleIds) { selected intersect visibleIds }

    fun exitSelection() { selecting = false; selected = emptySet() }

    // Back leaves selection mode before it leaves the screen: losing a long selection to a stray
    // back gesture is the kind of thing that makes people distrust the mode.
    BackHandler(enabled = selecting) { exitSelection() }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        if (selecting) {
            DsTopAppBar(
                title    = "${effectiveSel.size} sélectionné${if (effectiveSel.size > 1) "s" else ""}",
                subtitle = "sur ${shown.size}",
                leading  = DsTopBarLeading.Close { exitSelection() }
            )
        } else {
            DsTopAppBar(
                title    = "Brouillons",
                subtitle = if (drafts.isEmpty()) "Aucun brouillon"
                           else "${drafts.size} ${if (drafts.size == 1) "brouillon" else "brouillons"}",
                leading  = DsTopBarLeading.Back(onBack)
            )
        }
        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

        if (drafts.isEmpty()) {
            EmptyVenteBrouillons(Modifier.weight(1f))
            return@Column
        }

        if (!selecting) {
            DraftFilterBar(
                filter           = filter,
                shownCount       = shown.size,
                totalCount       = drafts.size,
                onOpenFilters    = { showFilters = true },
                onClearFilters   = { filter = DraftFilterState() },
                onEnterSelection = { selecting = true }
            )
            HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
        }

        if (shown.isEmpty()) {
            NoVenteDraftsMatchFilter(Modifier.weight(1f)) { filter = DraftFilterState() }
        } else {
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
                items(shown, key = { it.id }) { draft ->
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
                            VenteDraftRow(
                                draft   = draft,
                                onClick = {
                                    if (selecting) {
                                        selected = if (isSelected) selected - draft.id
                                                   else            selected + draft.id
                                    } else onResume(draft)
                                },
                                totalText = if (draft.total > 0) "${formatDZD(draft.total)} DA" else null,
                                selected  = if (selecting) isSelected else null,
                                trailing  = if (selecting) ({}) else ({
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
        }

        DraftSelectionBarHost(
            visible       = selecting,
            selectedCount = effectiveSel.size,
            totalCount    = shown.size,
            onSelectAll   = { selected = visibleIds },
            onClearAll    = { selected = emptySet() },
            onDelete      = { confirmBulk = true }
        )
    }

    if (showFilters) {
        DraftFilterSheet(
            filter     = filter,
            onChange   = { filter = it },
            onReset    = { filter = DraftFilterState() },
            onDismiss  = { showFilters = false },
            partyNames = partyNames,
            matchCount = shown.size,
            copy       = FILTER_COPY
        )
    }

    if (confirmBulk) {
        DraftBulkDeleteDialog(
            count      = effectiveSel.size,
            totalCount = shown.size,
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

/** Drafts exist, but none match the filter — distinct from having no drafts at all. */
@Composable
private fun NoVenteDraftsMatchFilter(modifier: Modifier = Modifier, onClear: () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().padding(DsSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(DsShapes.large).background(DsColors.SurfaceSunken),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.FilterListOff,
                contentDescription = null,
                tint = DsColors.TextSecondary,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(DsSpacing.lg))
        Text(
            "Aucun résultat",
            fontSize   = DsTextSize.title,
            fontWeight = FontWeight.Bold,
            color      = DsColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Aucun brouillon ne correspond à ces filtres.",
            fontSize  = DsTextSize.bodySmall,
            color     = DsColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(DsSpacing.md))
        TextButton(onClick = onClear) {
            Text("Réinitialiser les filtres", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyVenteBrouillons(modifier: Modifier = Modifier) {
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
        Text(
            "Aucun brouillon",
            fontSize   = DsTextSize.title,
            fontWeight = FontWeight.Bold,
            color      = DsColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Une vente non terminée est enregistrée ici automatiquement, et vous pouvez la reprendre quand vous voulez.",
            fontSize  = DsTextSize.bodySmall,
            color     = DsColors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
