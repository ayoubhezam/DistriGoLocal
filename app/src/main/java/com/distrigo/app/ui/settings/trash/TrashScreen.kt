package com.distrigo.app.ui.settings.trash

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.trash.TrashItem
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Paramètres → Corbeille: what was deleted, to put back or, when nothing uses it, delete for good. */
@Composable
fun TrashScreen(onBack: () -> Unit, viewModel: TrashViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }
    BackHandler { onBack() }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = "Corbeille", leading = DsTopBarLeading.Back(onBack))

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.Primary)
            }
            state.total == 0 -> EmptyTrash()
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    state.kinds.forEach { kind ->
                        FilterChip(
                            selected = state.kind == kind,
                            onClick = { viewModel.choose(kind) },
                            label = { Text("${kind.label} (${state.counts[kind] ?: 0})") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DsColors.PrimaryLight, selectedLabelColor = DsColors.Primary)
                        )
                    }
                }
                Text(
                    "Restaurer remet l'élément à sa place. La suppression définitive n'est possible que pour un élément qu'aucune vente, aucun achat ni aucun mouvement n'utilise.",
                    fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = DsSpacing.lg)
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(DsSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(state.items, key = { "${it.kind}-${it.id}" }) { item ->
                        TrashRow(item, onRestore = { viewModel.restore(item) }, onDelete = { viewModel.askDelete(item) })
                    }
                }
            }
        }
    }

    when (val prompt = state.prompt) {
        is TrashPrompt.ConfirmDelete -> AlertDialog(
            onDismissRequest = viewModel::dismissPrompt,
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = DsColors.Danger) },
            title = { Text("Supprimer définitivement ?") },
            text = { Text("« ${prompt.item.name} » sera supprimé pour de bon. Cette action est irréversible.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteForGood(prompt.item) }) {
                    Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissPrompt) { Text("Annuler") } },
            containerColor = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor = DsColors.TextSecondary
        )
        is TrashPrompt.Refused -> AlertDialog(
            onDismissRequest = viewModel::dismissPrompt,
            title = { Text(prompt.title) },
            text = { Text(prompt.reason) },
            confirmButton = { TextButton(onClick = viewModel::dismissPrompt) { Text("OK", color = DsColors.Primary, fontWeight = FontWeight.SemiBold) } },
            containerColor = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor = DsColors.TextSecondary
        )
        null -> Unit
    }
}

@Composable
private fun TrashRow(item: TrashItem, onRestore: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.SurfaceMuted).padding(start = DsSpacing.lg, top = DsSpacing.md, bottom = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            item.detail?.let { Text(it, fontSize = DsTextSize.caption, color = DsColors.TextSecondary) }
            Text("Supprimé le " + DELETED.format(item.deletedAt.atZone(ZoneId.systemDefault())), fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
        }
        TextButton(onClick = onRestore) {
            Icon(Icons.Default.Restore, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DsSpacing.xs))
            Text("Restaurer", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Plus d'actions", tint = DsColors.TextSecondary)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Supprimer définitivement", color = DsColors.Danger) },
                    leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = DsColors.Danger) },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun EmptyTrash() {
    Column(
        modifier = Modifier.fillMaxSize().padding(DsSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(DsSpacing.lg))
        Text("La corbeille est vide", fontSize = DsTextSize.title, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
        Spacer(Modifier.height(DsSpacing.sm))
        Text(
            "Les produits, clients, fournisseurs, catégories, marques et types que vous supprimez arrivent ici, et peuvent être restaurés.",
            fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, textAlign = TextAlign.Center
        )
    }
}

private val DELETED = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH)
