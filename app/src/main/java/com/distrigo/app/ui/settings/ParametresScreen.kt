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
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.BuildConfig
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