package com.distrigo.app.ui.settings.data.export

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.export.ExportDataset
import com.distrigo.app.data.export.ExportFormat
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.border
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R
import androidx.compose.ui.res.pluralStringResource

/** Paramètres → Exporter les données: choose data, a format and a period, then save the file or share it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(onBack: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(state.mimeType)) { uri ->
        uri?.let(viewModel::saveTo)
    }

    // The share sheet opens once the file is ready.
    LaunchedEffect(state.result) {
        val ready = state.result as? ExportResult.ReadyToShare ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = ready.mimeType
            putExtra(Intent.EXTRA_STREAM, ready.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.export_share_title)))
        viewModel.dismissResult()
    }

    BackHandler { if (!state.working) onBack() }

    var picking by remember { mutableStateOf<String?>(null) } // "from" or "to"

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
            DsTopAppBar(title = stringResource(R.string.settings_export), leading = DsTopBarLeading.Back { if (!state.working) onBack() })

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
            ) {
                // ── Données ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.export_data), Modifier.weight(1f), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark)
                    val all = state.selected.size == ExportDataset.entries.size
                    TextButton(onClick = { viewModel.selectAll(!all) }) {
                        Text(stringResource(if (all) R.string.export_unselect_all else R.string.export_select_all), color = DsColors.Primary)
                    }
                }
                Column(Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.large)) {
                    ExportDataset.entries.forEachIndexed { index, dataset ->
                        if (index > 0) HorizontalDivider(color = DsColors.Border)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.toggle(dataset) }.padding(horizontal = DsSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = dataset in state.selected,
                                onCheckedChange = { viewModel.toggle(dataset) },
                                colors = CheckboxDefaults.colors(checkedColor = DsColors.Primary)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(datasetLabel(dataset)), fontSize = DsTextSize.body, color = DsColors.TextPrimary)
                                if (!dataset.byPeriod) {
                                    Text(stringResource(R.string.export_as_today), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                }
                            }
                        }
                    }
                }

                // ── Format ──
                Text(stringResource(R.string.export_format), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    ExportFormat.entries.forEach { format ->
                        FilterChip(
                            selected = state.format == format,
                            onClick = { viewModel.chooseFormat(format) },
                            label = { Text(stringResource(if (format == ExportFormat.CSV) R.string.export_format_csv else R.string.export_format_xlsx)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DsColors.PrimaryLight, selectedLabelColor = DsColors.Primary)
                        )
                    }
                }

                // ── Période ──
                if (state.usesPeriod) {
                    Text(stringResource(R.string.common_label_period), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                        PeriodPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = state.preset == preset,
                                onClick = { viewModel.choosePreset(preset) },
                                label = { Text(stringResource(preset.label)) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DsColors.PrimaryLight, selectedLabelColor = DsColors.Primary)
                            )
                        }
                    }
                    if (state.preset == PeriodPreset.CUSTOM) {
                        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                            OutlinedButton(onClick = { picking = "from" }, modifier = Modifier.weight(1f), shape = DsShapes.medium) {
                                Text(stringResource(R.string.export_from, state.customFrom?.let(DAY::format) ?: "…"), color = DsColors.TextPrimary)
                            }
                            OutlinedButton(onClick = { picking = "to" }, modifier = Modifier.weight(1f), shape = DsShapes.medium) {
                                Text(stringResource(R.string.export_to, state.customTo?.let(DAY::format) ?: "…"), color = DsColors.TextPrimary)
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.export_period_sentence, ExportChoices.describe(state.period)),
                        fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary
                    )
                }

                // ── What the format gives you ──
                Row(
                    modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.PrimaryLight).padding(DsSpacing.lg),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(DsSpacing.md))
                    Text(
                        when (state.format) {
                            ExportFormat.XLSX ->
                                stringResource(R.string.export_xlsx_note)
                            ExportFormat.CSV ->
                                stringResource(R.string.export_csv_note)
                        },
                        fontSize = DsTextSize.caption, color = DsColors.TextPrimary
                    )
                }
                Spacer(Modifier.height(DsSpacing.sm))
            }

            // ── Actions ──
            Column(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                val ready = state.selected.isNotEmpty() && !state.working
                Text(
                    if (state.selected.isEmpty()) stringResource(R.string.export_choose_one) else state.fileName,
                    fontSize = DsTextSize.caption, color = DsColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { saveLauncher.launch(state.fileName) },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = DsShapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text(stringResource(R.string.common_action_save), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                OutlinedButton(
                    onClick = viewModel::share,
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = DsShapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text(stringResource(R.string.settings_share), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
                }
            }
        }

        if (state.working) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(DsSpacing.xxl).clip(DsShapes.large).background(DsColors.Surface).padding(DsSpacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
                ) {
                    CircularProgressIndicator(color = DsColors.Primary)
                    Text(stringResource(R.string.export_running), fontSize = DsTextSize.body, color = DsColors.TextPrimary)
                }
            }
        }

        when (val result = state.result) {
            is ExportResult.Saved -> AlertDialog(
                onDismissRequest = viewModel::dismissResult,
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DsColors.Success) },
                title = { Text(stringResource(R.string.export_saved)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.xs)) {
                        Text(result.fileName)
                        result.rows.forEach { (dataset, count) ->
                            Text(stringResource(R.string.export_rows_of, stringResource(datasetLabel(dataset)), pluralStringResource(R.plurals.export_n_rows, count, count)), fontSize = DsTextSize.bodySmall)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = viewModel::dismissResult) { Text(stringResource(R.string.common_action_ok), color = DsColors.Primary, fontWeight = FontWeight.SemiBold) } },
                containerColor = DsColors.Surface,
                titleContentColor = DsColors.TextPrimary,
                textContentColor = DsColors.TextSecondary
            )
            is ExportResult.Failed -> AlertDialog(
                onDismissRequest = viewModel::dismissResult,
                icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = DsColors.Danger) },
                title = { Text(stringResource(R.string.export_impossible)) },
                text = { Text(result.message) },
                confirmButton = { TextButton(onClick = viewModel::dismissResult) { Text(stringResource(R.string.common_action_ok), color = DsColors.Primary, fontWeight = FontWeight.SemiBold) } },
                containerColor = DsColors.Surface,
                titleContentColor = DsColors.TextPrimary,
                textContentColor = DsColors.TextSecondary
            )
            else -> Unit
        }
    }

    picking?.let { which ->
        val initial = if (which == "from") state.customFrom else state.customTo
        val pickerState = rememberDatePickerState(
            // Material date pickers work in UTC milliseconds (docs/data/timestamps.md, rule 5).
            initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    val day = pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    if (which == "from") viewModel.chooseFrom(day) else viewModel.chooseTo(day)
                    picking = null
                }) { Text(stringResource(R.string.common_action_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.common_action_cancel)) } }
        ) { DatePicker(state = pickerState) }
    }
}

/** A dataset's name in the app's language; the file keeps its French name. */
@androidx.annotation.StringRes
private fun datasetLabel(dataset: ExportDataset): Int = when (dataset) {
    ExportDataset.VENTES -> R.string.export_ds_ventes
    ExportDataset.LIGNES_VENTE -> R.string.export_ds_sale_lines
    ExportDataset.ACHATS -> R.string.export_ds_achats
    ExportDataset.PAIEMENTS_CLIENTS -> R.string.export_ds_client_payments
    ExportDataset.PAIEMENTS_FOURNISSEURS -> R.string.export_ds_supplier_payments
    ExportDataset.CLIENTS -> R.string.export_ds_clients
    ExportDataset.PRODUITS -> R.string.export_ds_products
    ExportDataset.MOUVEMENTS_STOCK -> R.string.export_ds_stock_movements
    ExportDataset.CHARGES -> R.string.export_ds_charges
    ExportDataset.PERTES -> R.string.export_ds_pertes
}

private val DAY: DateTimeFormatter get() = com.distrigo.app.core.format.AppDates.numeric("dd/MM/yyyy")
