package com.distrigo.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.BuildConfig
import com.distrigo.app.R
import com.distrigo.app.core.locale.AppLanguage
import com.distrigo.app.ui.account.findActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.distrigo.app.core.format.MoneyFormat
import com.distrigo.app.core.format.MoneyFormatter
import com.distrigo.app.data.model.CostMethod
import com.distrigo.app.data.model.Quantity
import com.distrigo.app.ui.common.FittedText
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import androidx.compose.ui.res.pluralStringResource

/**
 * Paramètres, in sections (the owner's design, 2026-10-21): the account, the business's own rules and lists, the
 * display preferences, the data, and help. The preferences not built yet are shown, faded, « Bientôt disponible ».
 */
@Composable
fun ParametresScreen(
    onBack: () -> Unit,
    onAccount: () -> Unit,
    onLists: () -> Unit,
    onReceipt: () -> Unit,
    onCommission: () -> Unit,
    onExport: () -> Unit,
    onData: () -> Unit,
    onTrash: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.SurfaceMuted)
    ) {
        DsTopAppBar(
            title   = stringResource(R.string.settings_title),
            leading = DsTopBarLeading.Back(onBack)
        )

        // Scrolls: the debug cards below the last entry used to fall off the bottom of the screen.
        Column(
            modifier            = Modifier.verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.xl)
        ) {
            SettingsSection(stringResource(R.string.settings_section_account)) {
                SettingsRow(Icons.Outlined.Person, DsColors.Primary, stringResource(R.string.settings_account), stringResource(R.string.settings_account_sub), onClick = onAccount)
            }

            SettingsSection(stringResource(R.string.settings_section_activity)) {
                SettingsRow(
                    Icons.Outlined.Folder, DsColors.AccentViolet, stringResource(R.string.settings_lists),
                    stringResource(R.string.settings_lists_sub), onClick = onLists,
                )
                SettingsDivider()
                SettingsRow(
                    Icons.Outlined.Percent, DsColors.Primary, stringResource(R.string.settings_commission),
                    stringResource(R.string.settings_commission_sub), onClick = onCommission,
                )
                SettingsDivider()
                SettingsRow(Icons.Outlined.Receipt, DsColors.Success, stringResource(R.string.settings_receipts), stringResource(R.string.settings_receipts_sub), onClick = onReceipt)
                SettingsDivider()
                NegativeStockRow()
                SettingsDivider()
                CostMethodRow()
            }

            SettingsSection(stringResource(R.string.settings_section_preferences)) {
                MoneyFormatRow()
                SettingsDivider()
                LanguageRow()
                SettingsDivider()
                SettingsRow(Icons.Outlined.MonetizationOn, DsColors.Success, stringResource(R.string.settings_currency), enabled = false)
                SettingsDivider()
                SettingsRow(Icons.Outlined.Contrast, DsColors.Warning, stringResource(R.string.settings_theme), enabled = false)
                SettingsDivider()
                SettingsRow(Icons.Outlined.CalendarMonth, DsColors.Primary, stringResource(R.string.settings_date_format), enabled = false)
                SettingsDivider()
                SettingsRow(Icons.Outlined.Notifications, DsColors.AccentViolet, stringResource(R.string.settings_notifications), enabled = false)
            }

            SettingsSection(stringResource(R.string.settings_section_data)) {
                SettingsRow(Icons.Outlined.FileUpload, DsColors.Primary, stringResource(R.string.settings_export), stringResource(R.string.settings_export_sub), onClick = onExport)
                SettingsDivider()
                SettingsRow(
                    Icons.Outlined.CloudUpload, DsColors.Warning, stringResource(R.string.settings_backup),
                    stringResource(R.string.settings_backup_sub), onClick = onData,
                )
                SettingsDivider()
                SettingsRow(
                    Icons.Outlined.DeleteOutline, DsColors.Danger, stringResource(R.string.settings_trash),
                    stringResource(R.string.settings_trash_sub), onClick = onTrash,
                )
            }

            SettingsSection(stringResource(R.string.settings_section_support)) {
                SettingsRow(Icons.Outlined.BugReport, DsColors.TextSecondary, stringResource(R.string.settings_diagnostics), stringResource(R.string.settings_diagnostics_sub), onClick = onDiagnostics)
            }

            // Absent from release builds: BuildConfig.DEBUG is a compile-time false there.
            if (BuildConfig.DEBUG) StressDataCard()
        }
    }
}

/**
 * "Autoriser le stock négatif": on, the dépôt may go below zero and the forms only warn; off (strict
 * stock), nothing can take the dépôt below zero — sales, chargements, pertes, supplier returns,
 * inventories, or undoing a document that brought stock in.
 */
@Composable
private fun NegativeStockRow(viewModel: StockSettingsViewModel = hiltViewModel()) {
    val allowNegative by viewModel.allowNegative.collectAsState()
    val pendingStrict by viewModel.pendingStrict.collectAsState()

    SettingsRow(
        icon     = Icons.Outlined.Inventory2,
        tint     = DsColors.Primary,
        title    = stringResource(R.string.settings_allow_negative),
        subtitle = stringResource(if (allowNegative == false) R.string.settings_allow_negative_off else R.string.settings_allow_negative_on),
        trailing = {
            Switch(
                checked         = allowNegative ?: true,
                enabled         = allowNegative != null,
                onCheckedChange = { viewModel.setAllowNegative(it) }
            )
        }
    )

    pendingStrict?.let { count ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelStrict() },
            title = { Text(stringResource(R.string.settings_strict_title)) },
            text  = {
                Text(
                    pluralStringResource(R.plurals.settings_strict_body, count, count)
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.confirmStrict() }) { Text(stringResource(R.string.settings_disable)) } },
            dismissButton = { TextButton(onClick = { viewModel.cancelStrict() }) { Text(stringResource(R.string.common_action_cancel)) } },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }
}

/** The title of each way a product's cost follows the bons received. */
private val CostMethod.title: Int get() = when (this) {
    CostMethod.WEIGHTED_AVERAGE -> R.string.settings_cost_cmup
    CostMethod.LAST_PURCHASE    -> R.string.settings_cost_last
}

/**
 * « Coût des produits » (FIN-04): weighted average or last purchase price. Each choice shows what it does to the
 * same example — 100 held at 50, 100 received at 60 — worked out by [CostMethod.costAfter] itself, in the user's money
 * format. A choice applies from the next receipt.
 */
@Composable
private fun CostMethodRow(viewModel: CostMethodSettingsViewModel = hiltViewModel()) {
    val current by viewModel.method.collectAsState()
    val money = LocalMoneyFormatter.current
    var choosing by remember { mutableStateOf(false) }

    SettingsRow(
        icon     = Icons.Outlined.Calculate,
        tint     = DsColors.Success,
        title    = stringResource(R.string.settings_cost_method),
        subtitle = current?.let { stringResource(it.title) } ?: "",
        onClick  = { if (current != null) choosing = true },
    )

    val selected = current
    if (choosing && selected != null) {
        val held = stringResource(R.string.settings_cost_lot, Quantity.format(COST_SAMPLE_QTY), money.da(COST_SAMPLE_HELD))
        val received = stringResource(R.string.settings_cost_lot, Quantity.format(COST_SAMPLE_QTY), money.da(COST_SAMPLE_RECEIVED))
        val examples = CostMethod.entries.associateWith { method ->
            val cost = money.da(method.costAfter(COST_SAMPLE_QTY, COST_SAMPLE_HELD, COST_SAMPLE_QTY, COST_SAMPLE_RECEIVED))
            stringResource(R.string.settings_cost_example, held, received, cost)
        }
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.settings_cost_method)) },
            text  = {
                Column {
                    Text(stringResource(R.string.settings_cost_method_body), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    Spacer(Modifier.height(DsSpacing.sm))
                    CostMethod.entries.forEach { method ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.medium)
                                .clickable {
                                    viewModel.choose(method)
                                    choosing = false
                                }
                                .padding(vertical = DsSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = method == selected, onClick = null)
                            Spacer(Modifier.width(DsSpacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(method.title),
                                    fontSize   = DsTextSize.body,
                                    fontWeight = if (method == selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color      = DsColors.TextPrimary
                                )
                                // One line each, all three at the same size
                                FittedText(
                                    text = examples.getValue(method), fitTo = examples.values.toList(),
                                    fontSize = DsTextSize.caption, modifier = Modifier.fillMaxWidth(), color = DsColors.TextSecondary,
                                    fontWeight = null, textAlign = null, minScale = 0.6f, tabular = false,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.common_action_close)) } },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }
}

/** The example every cost method is shown on: 100 held at 50, 100 received at 60. */
private const val COST_SAMPLE_QTY = 100.0
private const val COST_SAMPLE_HELD = 50.0
private const val COST_SAMPLE_RECEIVED = 60.0

/** The amount every format is shown with, so the three can be told apart at a glance. */
private const val MONEY_FORMAT_SAMPLE = 1_236_790.5

/**
 * "Format des montants": spaces, commas or dots between the thousands. The row shows the current
 * format on a sample amount; the dialog shows each choice the same way, and applies it on tap.
 */
@Composable
private fun MoneyFormatRow(viewModel: MoneyFormatSettingsViewModel = hiltViewModel()) {
    val current by viewModel.format.collectAsState()
    var choosing by remember { mutableStateOf(false) }

    SettingsRow(
        icon     = Icons.Outlined.Payments,
        tint     = DsColors.Primary,
        title    = stringResource(R.string.settings_money_format),
        subtitle = current?.let { MoneyFormatter.of(it).da(MONEY_FORMAT_SAMPLE) } ?: "",
        onClick  = { if (current != null) choosing = true },
    )

    val selected = current
    if (choosing && selected != null) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.settings_money_format)) },
            text  = {
                Column {
                    Text(
                        stringResource(R.string.settings_money_format_body),
                        fontSize = DsTextSize.caption,
                        color    = DsColors.TextSecondary
                    )
                    Spacer(Modifier.height(DsSpacing.sm))
                    MoneyFormat.entries.forEach { format ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.medium)
                                .clickable {
                                    viewModel.choose(format)
                                    choosing = false
                                }
                                .padding(vertical = DsSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = format == selected, onClick = null)
                            Spacer(Modifier.width(DsSpacing.sm))
                            Text(
                                MoneyFormatter.of(format).da(MONEY_FORMAT_SAMPLE),
                                fontSize   = DsTextSize.body,
                                fontWeight = if (format == selected) FontWeight.SemiBold else FontWeight.Normal,
                                color      = DsColors.TextPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.common_action_close)) } },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }
}

/**
 * « Langue »: French or Arabic on this phone (AppLanguage). Choosing one rebuilds the screens in it; each language is
 * named in itself, so it reads whatever the app is in.
 */
@Composable
private fun LanguageRow() {
    val context = LocalContext.current
    val current = remember { AppLanguage.current(context) }
    var choosing by remember { mutableStateOf(false) }

    SettingsRow(
        icon     = Icons.Outlined.Language,
        tint     = DsColors.AccentSky,
        title    = stringResource(R.string.settings_language_title),
        subtitle = current.endonym,
        onClick  = { choosing = true },
    )

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.settings_language_dialog_title)) },
            text  = {
                Column {
                    Text(stringResource(R.string.settings_language_dialog_note), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    Spacer(Modifier.height(DsSpacing.sm))
                    AppLanguage.entries.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.medium)
                                .clickable {
                                    choosing = false
                                    if (language != current) context.findActivity()?.let { AppLanguage.set(it, language) }
                                }
                                .padding(vertical = DsSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = language == current, onClick = null)
                            Spacer(Modifier.width(DsSpacing.sm))
                            Text(
                                language.endonym,
                                fontSize   = DsTextSize.body,
                                fontWeight = if (language == current) FontWeight.SemiBold else FontWeight.Normal,
                                color      = DsColors.TextPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.common_action_close)) } },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }
}
