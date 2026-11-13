package com.distrigo.app.ui.settings.data.importer

import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import android.widget.Toast
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.ui.platform.LocalContext
import com.distrigo.app.data.importer.ImportPlan
import com.distrigo.app.data.importer.ImportSheet
import com.distrigo.app.data.importer.ImportTemplate
import com.distrigo.app.data.importer.LookupsToCreate
import com.distrigo.app.data.importer.PlannedRow
import com.distrigo.app.data.importer.RowOutcome
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import androidx.compose.foundation.border
import androidx.compose.ui.res.stringResource
import com.distrigo.app.data.lists.ListWords
import com.distrigo.app.R
import androidx.compose.ui.res.pluralStringResource

/**
 * Produits or Clients → Importer: pick a workbook, see what importing its [sheet] would do, row by row, then confirm.
 * Only [sheet] is read, whatever else the file holds; « Télécharger un modèle vide » saves an empty one to fill in.
 */
@Composable
fun ImportScreen(sheet: ImportSheet, onBack: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val working = state is ImportState.Working
    val context = LocalContext.current

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.inspect(it, sheet) }
    }
    val pick = { openLauncher.launch(arrayOf("*/*")) }
    val templateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX)) { uri ->
        uri?.let { viewModel.saveTemplate(it, sheet) { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() } }
    }

    BackHandler { if (!working) onBack() }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = stringResource(if (sheet == ImportSheet.PRODUITS) R.string.import_title_products else R.string.import_title_clients), leading = DsTopBarLeading.Back { if (!working) onBack() })

        when (val current = state) {
            ImportState.Idle -> Intro(sheet, onPick = pick, onTemplate = { templateLauncher.launch(ImportTemplate.fileName(sheet)) })
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

/** The template's file type, as the save dialog needs it. */
private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
private fun Intro(sheet: ImportSheet, onPick: () -> Unit, onTemplate: () -> Unit) {
    val products = sheet == ImportSheet.PRODUITS
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
    ) {
        Panel {
            Text(stringResource(if (products) R.string.import_intro_products else R.string.import_intro_clients), fontSize = DsTextSize.body, color = DsColors.TextPrimary)
            Text(
                stringResource(if (products) R.string.import_how_products else R.string.import_how_clients),
                fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary
            )
            Text(
                stringResource(R.string.import_preview_first),
                fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary
            )
        }
        Panel {
            Text(stringResource(R.string.import_does_not), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark)
            Text(
                stringResource(if (products) R.string.import_does_not_products else R.string.import_does_not_clients),
                fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary
            )
        }
        Button(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = DsShapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(DsSpacing.sm))
            Text(stringResource(R.string.import_pick_excel), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(onClick = onTemplate, modifier = Modifier.fillMaxWidth().height(52.dp), shape = DsShapes.medium) {
            Icon(Icons.Default.FileDownload, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(DsSpacing.sm))
            Text(stringResource(R.string.import_template), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
        }
    }
}

private enum class Filter(@androidx.annotation.StringRes val label: Int) {
    ALL(R.string.common_source_all), NEW(R.string.import_filter_new), UPDATED(R.string.import_filter_updated),
    REFUSED(R.string.import_filter_refused), UNCHANGED(R.string.import_filter_unchanged),
}

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
                        Count(plan.creates, R.plurals.import_n_new, DsColors.Success, DsColors.SuccessLight)
                        Count(plan.updates, R.plurals.import_n_updated, DsColors.Primary, DsColors.PrimaryLight)
                        Count(plan.unchanged, R.plurals.import_n_unchanged, DsColors.TextSecondary, DsColors.SurfaceSunken)
                        Count(plan.refused, R.plurals.import_n_refused, DsColors.Danger, DsColors.DangerLight)
                    }
                    creations(plan.toCreate)?.let { Note(it, DsColors.Success) }
                    plan.notes.forEach { Note(it, DsColors.TextSecondary) }
                    if (!plan.hasWork) Note(stringResource(R.string.import_nothing), DsColors.Warning)
                }
            }
            item {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    Filter.entries.filter { it == Filter.ALL || (counts[it] ?: 0) > 0 }.forEach { choice ->
                        FilterChip(
                            selected = filter == choice,
                            onClick = { filter = choice },
                            label = { Text("${stringResource(choice.label)} (${counts[choice]})") },
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
                Text(stringResource(R.string.common_action_cancel), color = DsColors.TextPrimary)
            }
            Button(
                onClick = onConfirm,
                enabled = plan.hasWork,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = DsShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
            ) {
                Text(stringResource(R.string.import_go, plan.creates + plan.updates), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RowCard(row: PlannedRow) {
    val (label, tint, background) = when (row.outcome) {
        is RowOutcome.CreateProduct, is RowOutcome.CreateClient -> Triple(stringResource(R.string.import_badge_new), DsColors.Success, DsColors.SuccessLight)
        is RowOutcome.UpdateProduct, is RowOutcome.UpdateClient -> Triple(stringResource(R.string.import_badge_updated), DsColors.Primary, DsColors.PrimaryLight)
        is RowOutcome.Unchanged -> Triple(stringResource(R.string.import_badge_unchanged), DsColors.TextSecondary, DsColors.SurfaceSunken)
        is RowOutcome.Refused -> Triple(stringResource(R.string.import_badge_refused), DsColors.Danger, DsColors.DangerLight)
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.large).padding(DsSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            Text(row.name, Modifier.weight(1f), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Badge(label, tint, background)
        }
        Text(stringResource(if (row.sheet == ImportSheet.PRODUITS) R.string.import_row_product else R.string.import_row_client, row.rowNumber), fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
        when (val outcome = row.outcome) {
            is RowOutcome.UpdateProduct -> outcome.changes.forEach { Text(stringResource(R.string.import_change, it.label, it.from ?: "—", it.to ?: "—"), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary) }
            is RowOutcome.UpdateClient -> outcome.changes.forEach { Text(stringResource(R.string.import_change, it.label, it.from ?: "—", it.to ?: "—"), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary) }
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
                Text(stringResource(R.string.import_done), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark)
            }
            Text(
                stringResource(R.string.import_done_body, done.fileName, pluralStringResource(R.plurals.import_n_created, done.result.created, done.result.created), pluralStringResource(R.plurals.import_n_modified, done.result.updated, done.result.updated)),
                fontSize = DsTextSize.body, color = DsColors.TextPrimary
            )
            Text(
                stringResource(R.string.import_safety_kept),
                fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary
            )
        }
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(48.dp), shape = DsShapes.medium, colors = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)) {
            Text(stringResource(R.string.import_finish), fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth().height(48.dp), shape = DsShapes.medium) {
            Text(stringResource(R.string.import_another), color = DsColors.Primary)
        }
    }
}

@Composable
private fun Failed(message: String, onPick: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DsSpacing.lg), verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = DsColors.Danger)
                Text(stringResource(R.string.import_impossible), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark)
            }
            Text(message, fontSize = DsTextSize.body, color = DsColors.TextSecondary)
        }
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth().height(48.dp), shape = DsShapes.medium, colors = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)) {
            Text(stringResource(R.string.import_pick_another), fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Pieces ──

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.large).padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
        content = content
    )
}

@Composable
private fun Count(count: Int, @androidx.annotation.PluralsRes words: Int, tint: Color, background: Color) {
    if (count == 0) return
    Badge(pluralStringResource(words, count, count), tint, background)
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


/** "Seront créés : 2 catégories (Eaux, Jus), 1 marque (Ifri)", or null when nothing is. */
private fun creations(toCreate: LookupsToCreate): String? {
    if (toCreate.isEmpty) return null
    fun part(names: List<String>, @androidx.annotation.PluralsRes words: Int) =
        names.takeIf { it.isNotEmpty() }?.let { "${ListWords.count(words, it.size)} (${it.joinToString(", ")})" }
    val parts = listOfNotNull(
        part(toCreate.categories, R.plurals.lists_n_categories),
        part(toCreate.sousCategories.values.flatten(), R.plurals.lists_n_subcategories),
        part(toCreate.marques, R.plurals.lists_n_brands),
        part(toCreate.suppliers, R.plurals.suppliers_count),
        part(toCreate.secteurs.values.flatten(), R.plurals.lists_n_secteurs),
    )
    return ListWords.text(R.string.import_will_create, ListWords.and(parts))
}
