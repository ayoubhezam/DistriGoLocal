package com.distrigo.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.BuildConfig
import com.distrigo.app.core.format.MoneyFormat
import com.distrigo.app.core.format.MoneyFormatter
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading

@Composable
fun ParametresScreen(
    onBack: () -> Unit,
    onReceipt: () -> Unit,
    onCommission: () -> Unit,
    onData: () -> Unit,
    onTrash: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        DsTopAppBar(
            title   = "Paramètres",
            leading = DsTopBarLeading.Back(onBack)
        )

        // Scrolls: the debug cards below the last entry used to fall off the bottom of the screen.
        Column(
            modifier            = Modifier.verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            NegativeStockCard()
            MoneyFormatCard()
            SettingsNavCard(
                icon     = Icons.Default.Receipt,
                iconBg   = DsColors.SuccessLight,
                iconTint = DsColors.Success,
                title    = "Reçus et impression",
                subtitle = "Logo, format du papier et imprimante",
                onClick  = onReceipt
            )
            SettingsNavCard(
                icon     = Icons.Default.Percent,
                iconBg   = DsColors.PrimaryLight,
                iconTint = DsColors.Primary,
                title    = "Politique de commission",
                subtitle = "Définir l'objectif et le mode de calcul des primes",
                onClick  = onCommission
            )
            SettingsNavCard(
                icon     = Icons.Default.Backup,
                iconBg   = DsColors.WarningLight,
                iconTint = DsColors.Warning,
                title    = "Données et sauvegarde",
                subtitle = "Sauvegarder vos données dans un fichier et les restaurer",
                onClick  = onData
            )
            SettingsNavCard(
                icon     = Icons.Default.DeleteOutline,
                iconBg   = DsColors.DangerLight,
                iconTint = DsColors.Danger,
                title    = "Corbeille",
                subtitle = "Restaurer les produits, clients et autres éléments supprimés",
                onClick  = onTrash
            )
            SettingsNavCard(
                icon     = Icons.Default.BugReport,
                iconBg   = DsColors.SurfaceMuted,
                iconTint = DsColors.TextSecondary,
                title    = "Diagnostic",
                subtitle = "Plantages et blocages enregistrés sur ce téléphone",
                onClick  = onDiagnostics
            )
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
private fun NegativeStockCard(viewModel: StockSettingsViewModel = hiltViewModel()) {
    val allowNegative by viewModel.allowNegative.collectAsState()
    val pendingStrict by viewModel.pendingStrict.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.SurfaceMuted)
            .padding(DsSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Inventory2, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text("Autoriser le stock négatif", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text(
                if (allowNegative == false) "Désactivé : le stock du dépôt ne peut jamais passer sous zéro"
                else "Activé : le dépôt peut passer sous zéro, avec un avertissement",
                fontSize = DsTextSize.caption,
                color    = DsColors.TextSecondary
            )
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Switch(
            checked         = allowNegative ?: true,
            enabled         = allowNegative != null,
            onCheckedChange = { viewModel.setAllowNegative(it) }
        )
    }

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
            dismissButton = { TextButton(onClick = { viewModel.cancelStrict() }) { Text("Annuler") } },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }
}

/** The amount every format is shown with, so the three can be told apart at a glance. */
private const val MONEY_FORMAT_SAMPLE = 1_236_790.5

/**
 * "Format des montants": spaces, commas or dots between the thousands. The card shows the current
 * format on a sample amount; the dialog shows each choice the same way, and applies it on tap.
 */
@Composable
private fun MoneyFormatCard(viewModel: MoneyFormatSettingsViewModel = hiltViewModel()) {
    val current by viewModel.format.collectAsState()
    var choosing by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.SurfaceMuted)
            .clickable(enabled = current != null) { choosing = true }
            .padding(DsSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Payments, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text("Format des montants", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text(
                current?.let { MoneyFormatter.screen(it).da(MONEY_FORMAT_SAMPLE) } ?: "",
                fontSize = DsTextSize.caption,
                color    = DsColors.TextSecondary
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(18.dp))
    }

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
                                MoneyFormatter.screen(format).da(MONEY_FORMAT_SAMPLE),
                                fontSize   = DsTextSize.body,
                                fontWeight = if (format == selected) FontWeight.SemiBold else FontWeight.Normal,
                                color      = DsColors.TextPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Fermer") } },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }
}

@Composable
private fun SettingsNavCard(
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    iconBg   : androidx.compose.ui.graphics.Color,
    iconTint : androidx.compose.ui.graphics.Color,
    title    : String,
    subtitle : String,
    onClick  : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.SurfaceMuted)
            .clickable { onClick() }
            .padding(DsSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text(subtitle, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(18.dp))
    }
}