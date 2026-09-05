package com.distrigo.app.ui.ventes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.VenteDraft
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.suppliers.formatDZD

/**
 * The Dépôt Vente Brouillons list — its own screen, separate from the Ventes list, mirroring the
 * Achats one.
 *
 * Tapping a card resumes that draft directly; there is no confirmation, because the card *is* the
 * confirmation. Deletion is the only destructive action here and it asks first, since a draft is
 * unrecoverable and nothing expires on its own.
 */
@Composable
fun VenteBrouillonsScreen(
    viewModel: VenteViewModel = hiltViewModel(),
    onBack   : () -> Unit,
    onResume : (VenteDraft) -> Unit
) {
    val drafts by viewModel.drafts.collectAsState()
    var pendingDelete by remember { mutableStateOf<VenteDraft?>(null) }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(
            title    = "Brouillons",
            subtitle = if (drafts.isEmpty()) "Aucun brouillon"
                       else "${drafts.size} ${if (drafts.size == 1) "brouillon" else "brouillons"}",
            leading  = DsTopBarLeading.Back(onBack)
        )
        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

        if (drafts.isEmpty()) {
            EmptyVenteBrouillons(Modifier.weight(1f))
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
                Card(
                    shape  = DsShapes.large,
                    colors = CardDefaults.cardColors(containerColor = DsColors.Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(horizontal = DsSpacing.md)) {
                        VenteDraftRow(
                            draft   = draft,
                            onClick = { onResume(draft) },
                            // The amount goes to the row rather than into the trailing slot, so it
                            // shares width with the title instead of narrowing the meta line.
                            totalText = if (draft.total > 0) "${formatDZD(draft.total)} DA" else null,
                            trailing = {
                                IconButton(onClick = { pendingDelete = draft }) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Supprimer le brouillon",
                                        tint = DsColors.TextTertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
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
