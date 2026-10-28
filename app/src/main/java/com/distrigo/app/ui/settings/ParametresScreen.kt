package com.distrigo.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.distrigo.app.core.format.MoneyFormat
import com.distrigo.app.core.format.MoneyFormatter
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading

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
            title   = "Paramètres",
            leading = DsTopBarLeading.Back(onBack)
        )

        // Scrolls: the debug cards below the last entry used to fall off the bottom of the screen.
        Column(
            modifier            = Modifier.verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.xl)
        ) {
            SettingsSection("Compte") {
                SettingsRow(Icons.Outlined.Person, DsColors.Primary, "Compte et connexion", "Gérer votre profil", onClick = onAccount)
            }

            SettingsSection("Votre activité") {
                SettingsRow(
                    Icons.Outlined.Folder, DsColors.AccentViolet, "Listes et catégories",
                    "Catégories, marques, types de charges et de pertes, secteurs", onClick = onLists,
                )
                SettingsDivider()
                SettingsRow(
                    Icons.Outlined.Percent, DsColors.Primary, "Politique de commission",
                    "Définir l'objectif et le mode de calcul des primes", onClick = onCommission,
                )
                SettingsDivider()
                SettingsRow(Icons.Outlined.Receipt, DsColors.Success, "Reçus et impression", "Logo, format du papier et imprimante", onClick = onReceipt)
                SettingsDivider()
                NegativeStockRow()
            }

            SettingsSection("Préférences") {
                MoneyFormatRow()
                // Offered in debug builds until the Arabic text is complete (docs/i18n_rtl_plan.md, Phase 5)
                if (BuildConfig.DEBUG) {
                    SettingsDivider()
                    LanguageRow()
                }
                SettingsDivider()
                SettingsRow(Icons.Outlined.MonetizationOn, DsColors.Success, "Devise principale", enabled = false)
                SettingsDivider()
                SettingsRow(Icons.Outlined.Contrast, DsColors.Warning, "Thème", enabled = false)
                SettingsDivider()
                SettingsRow(Icons.Outlined.CalendarMonth, DsColors.Primary, "Format de date", enabled = false)
                SettingsDivider()
                SettingsRow(Icons.Outlined.Notifications, DsColors.AccentViolet, "Notifications", enabled = false)
            }

            SettingsSection("Gestion des données") {
                SettingsRow(Icons.Outlined.FileUpload, DsColors.Primary, "Exporter les données", "Ventes, achats, clients, produits… en Excel ou CSV", onClick = onExport)
                SettingsDivider()
                SettingsRow(
                    Icons.Outlined.CloudUpload, DsColors.Warning, "Sauvegarde et restauration",
                    "Sauvegarder vos données dans un fichier et les restaurer", onClick = onData,
                )
                SettingsDivider()
                SettingsRow(
                    Icons.Outlined.DeleteOutline, DsColors.Danger, "Corbeille",
                    "Restaurer les produits, clients et autres éléments supprimés", onClick = onTrash,
                )
            }

            SettingsSection("Assistance") {
                SettingsRow(Icons.Outlined.BugReport, DsColors.TextSecondary, "Diagnostic", "Plantages et blocages enregistrés sur ce téléphone", onClick = onDiagnostics)
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
        title    = "Autoriser le stock négatif",
        subtitle = if (allowNegative == false) "Désactivé : le stock du dépôt ne peut jamais passer sous zéro"
                   else "Activé : le dépôt peut passer sous zéro, avec un avertissement",
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
            title = { Text("Désactiver le stock négatif ?") },
            text  = {
                Text(
                    "$count produit(s) sont déjà en stock négatif au dépôt. Ils ne pourront plus être " +
                        "vendus, chargés ni sortis du dépôt avant d'être réapprovisionnés."
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.confirmStrict() }) { Text("Désactiver") } },
            dismissButton = { TextButton(onClick = { viewModel.cancelStrict() }) { Text(stringResource(R.string.common_action_cancel)) } },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }
}

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
        title    = "Format des montants",
        subtitle = current?.let { MoneyFormatter.of(it).da(MONEY_FORMAT_SAMPLE) } ?: "",
        onClick  = { if (current != null) choosing = true },
    )

    val selected = current
    if (choosing && selected != null) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Format des montants") },
            text  = {
                Column {
                    Text(
                        "Sur les écrans, les reçus et les PDF, pour tous les téléphones de l'entreprise. " +
                            "Les exports Excel et CSV ne changent pas.",
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
                                    if (language != current) (context as? android.app.Activity)?.let { AppLanguage.set(it, language) }
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
