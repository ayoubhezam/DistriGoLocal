package com.distrigo.app.ui.settings.data.importer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.importer.ImportPlan
import com.distrigo.app.data.importer.LookupsToCreate
import com.distrigo.app.data.importer.PlannedRow
import com.distrigo.app.data.importer.RowOutcome
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading

/** Données et sauvegarde → Importer: pick a workbook, see what it would do, row by row, then confirm. */
@Composable
fun ImportScreen(onBack: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val working = state is ImportState.Working

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::inspect)
    }
    val pick = { openLauncher.launch(arrayOf("*/*")) }

    BackHandler { if (!working) onBack() }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = "Importer depuis Excel", leading = DsTopBarLeading.Back { if (!working) onBack() })

        when (val current = state) {
            ImportState.Idle -> Intro(onPick = pick)
            is ImportState.Working -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                    CircularProgressIndicator(color = DsColors.Primary)
                    Text(current.label, fontSize = DsTextSize.body, color = DsColors.TextSecondary)
                }
            }
            is ImportState.Preview -> Preview(current, onCancel = viewModel::reset, onConfirm = viewModel::apply)
            is ImportState.Done -> Done(current, onAnother = viewModel::reset, onFinish = onBack)
            is ImportState.Failed -> Failed(current.message, onPick = pick)
        }
    }
}

@Composable
private fun Intro(onPick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
    ) {
        Panel {
            Text("Ajoute ou met à jour des produits et des clients depuis un classeur Excel (.xlsx).", fontSize = DsTextSize.body, color = DsColors.TextPrimary)
            Text(
                "Le plus simple : exportez les produits ou les clients, modifiez le fichier sur l'ordinateur, puis importez-le ici. " +
                    "Une ligne dont le nom (ou le code-barres) existe déjà met la fiche à jour ; les autres lignes créent des fiches. " +
                    "Une cellule vide ne change rien, donc un fichier avec seulement les colonnes à modifier suffit.",
                fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary
            )
            Text(
                "Vous verrez tout ce que l'import ferait avant de confirmer, et une copie de sécurité de vos données est faite juste avant.",
                fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary
            )
        }
        Panel {
            Text("Ce que l'import ne fait pas", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text(
                "• Le stock d'un produit existant ne vient jamais du fichier : il se corrige par un inventaire ou un ajustement. Un nouveau produit prend son stock de départ.\n" +
                    "• Le solde d'un client vient de ses ventes et de ses paiements.\n" +
                    "• Rien n'est supprimé : une fiche absente du fichier reste telle quelle.",
                fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary
            )
        }
        Button(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = DsShapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(DsSpacing.sm))
            Text("Choisir un fichier Excel", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

private enum class Filter(val label: String) { ALL("Tout"), NEW("Nouveaux"), UPDATED("Modifiés"), REFUSED("Refusés"), UNCHANGED("Inchangés") }

@Composable
private fun Preview(preview: ImportState.Preview, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val plan = preview.plan
    var filter by remember { mutableStateOf(Filter.ALL) }
    val rows = remember(plan, filter) {
        plan.rows.filter {
            when (filter) {
                Filter.ALL -> true
                Filter.NEW -> it.isCreate
                Filter.UPDATED -> it.isUpdate
                Filter.REFUSED -> it.isRefused
                Filter.UNCHANGED -> it.isUnchanged
            }
        }
    }
    val counts = mapOf(Filter.ALL to plan.rows.size, Filter.NEW to plan.creates, Filter.UPDATED to plan.updates, Filter.REFUSED to plan.refused, Filter.UNCHANGED to plan.unchanged)

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            item {
                Panel {
                    Text(preview.fileName, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Count(plan.creates, "nouveau", "nouveaux", DsColors.Success, DsColors.SuccessLight)
                        Count(plan.updates, "modifié", "modifiés", DsColors.Primary, DsColors.PrimaryLight)
                        Count(plan.unchanged, "inchangé", "inchangés", DsColors.TextSecondary, DsColors.SurfaceSunken)
                        Count(plan.refused, "refusé", "refusés", DsColors.Danger, DsColors.DangerLight)
                    }
                    creations(plan.toCreate)?.let { Note(it, DsColors.Success) }
                    plan.notes.forEach { Note(it, DsColors.TextSecondary) }
                    if (!plan.hasWork) Note("Rien à importer : aucune ligne ne crée ni ne modifie de fiche.", DsColors.Warning)
                }
            }
            item {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    Filter.entries.filter { it == Filter.ALL || (counts[it] ?: 0) > 0 }.forEach { choice ->
                        FilterChip(
                            selected = filter == choice,
                            onClick = { filter = choice },
                            label = { Text("${choice.label} (${counts[choice]})") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DsColors.PrimaryLight, selectedLabelColor = DsColors.Primary)
                        )
                    }
                }
            }
            items(rows, key = { "${it.sheet}-${it.rowNumber}" }) { row -> RowCard(row) }
        }
        HorizontalDivider(color = DsColors.Border)
        Row(Modifier.padding(DsSpacing.lg), horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp), shape = DsShapes.medium) {
                Text("Annuler", color = DsColors.TextPrimary)
            }
            Button(
                onClick = onConfirm,
                enabled = plan.hasWork,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = DsShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
            ) {
                Text("Importer (${plan.creates + plan.updates})", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RowCard(row: PlannedRow) {
    val (label, tint, background) = when (row.outcome) {
        is RowOutcome.CreateProduct, is RowOutcome.CreateClient -> Triple("Nouveau", DsColors.Success, DsColors.SuccessLight)
        is RowOutcome.UpdateProduct, is RowOutcome.UpdateClient -> Triple("Modifié", DsColors.Primary, DsColors.PrimaryLight)
        is RowOutcome.Unchanged -> Triple("Inchangé", DsColors.TextSecondary, DsColors.SurfaceSunken)
        is RowOutcome.Refused -> Triple("Refusé", DsColors.Danger, DsColors.DangerLight)
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.SurfaceMuted).padding(DsSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            Text(row.name, Modifier.weight(1f), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Badge(label, tint, background)
        }
        Text("${row.sheet.singular.replaceFirstChar { it.uppercase() }} · ligne ${row.rowNumber}", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
        when (val outcome = row.outcome) {
            is RowOutcome.UpdateProduct -> outcome.changes.forEach { Text("${it.label} : ${it.from ?: "—"} → ${it.to}", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary) }
            is RowOutcome.UpdateClient -> outcome.changes.forEach { Text("${it.label} : ${it.from ?: "—"} → ${it.to}", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary) }
            is RowOutcome.Refused -> Text(outcome.reason, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
            else -> Unit
        }
    }
}

@Composable
private fun Done(done: ImportState.Done, onAnother: () -> Unit, onFinish: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DsSpacing.lg), verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DsColors.Success)
                Text("Import terminé", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            }
            Text(
                "${done.fileName} : ${plural(done.result.created, "fiche créée", "fiches créées")}, ${plural(done.result.updated, "fiche modifiée", "fiches modifiées")}.",
                fontSize = DsTextSize.body, color = DsColors.TextPrimary
            )
            Text(
                "Une copie de sécurité de vos données d'avant l'import a été gardée : Données et sauvegarde › Copies de sécurité. Restaurez-la pour annuler l'import.",
                fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary
            )
        }
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(48.dp), shape = DsShapes.medium, colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)) {
            Text("Terminer", fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth().height(48.dp), shape = DsShapes.medium) {
            Text("Importer un autre fichier", color = DsColors.Primary)
        }
    }
}

@Composable
private fun Failed(message: String, onPick: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DsSpacing.lg), verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = DsColors.Danger)
                Text("Import impossible", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            }
            Text(message, fontSize = DsTextSize.body, color = DsColors.TextSecondary)
        }
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth().height(48.dp), shape = DsShapes.medium, colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)) {
            Text("Choisir un autre fichier", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Pieces ──

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.SurfaceMuted).padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
        content = content
    )
}

@Composable
private fun Count(count: Int, singular: String, plural: String, tint: Color, background: Color) {
    if (count == 0) return
    Badge("$count ${if (count == 1) singular else plural}", tint, background)
}

@Composable
private fun Badge(text: String, tint: Color, background: Color) {
    Text(
        text, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = tint,
        modifier = Modifier.clip(DsShapes.pill).background(background).padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs)
    )
}

@Composable
private fun Note(text: String, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Info, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Text(text, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
    }
}

private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

/** "Seront créés : 2 catégories (Eaux, Jus), 1 marque (Ifri)", or null when nothing is. */
private fun creations(toCreate: LookupsToCreate): String? {
    if (toCreate.isEmpty) return null
    fun part(names: List<String>, one: String, many: String) = names.takeIf { it.isNotEmpty() }?.let { "${plural(it.size, one, many)} (${it.joinToString(", ")})" }
    val parts = listOfNotNull(
        part(toCreate.categories, "catégorie", "catégories"),
        part(toCreate.sousCategories.values.flatten(), "sous-catégorie", "sous-catégories"),
        part(toCreate.marques, "marque", "marques"),
        part(toCreate.suppliers, "fournisseur", "fournisseurs"),
        part(toCreate.secteurs.values.flatten(), "secteur", "secteurs"),
    )
    return "Seront créés : " + parts.joinToString(", ") + "."
}
