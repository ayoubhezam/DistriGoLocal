package com.distrigo.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

@Composable
fun ParametresScreen(onBack: () -> Unit) {
    var showReceiptSettings   by remember { mutableStateOf(false) }
    var showCommissionPolicy  by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    if (showReceiptSettings) {
        com.distrigo.app.ui.settings.receipt.ReceiptSettingsScreen(
            onBack = { showReceiptSettings = false }
        )
        return
    }

    if (showCommissionPolicy) {
        com.distrigo.app.ui.settings.incentive.CommissionPolicyScreen(
            onBack = { showCommissionPolicy = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
            .padding(DsSpacing.lg)
    ) {
        // ── Header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.xs))
            Text(
                "Paramètres",
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary
            )
        }

        Spacer(Modifier.height(DsSpacing.lg))

        Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            SettingsNavCard(
                icon     = Icons.Default.Receipt,
                iconBg   = DsColors.SuccessLight,
                iconTint = DsColors.Success,
                title    = "Paramètres du reçu",
                subtitle = "Logo, nom du commerce et informations affichées sur le reçu",
                onClick  = { showReceiptSettings = true }
            )
            SettingsNavCard(
                icon     = Icons.Default.Percent,
                iconBg   = DsColors.PrimaryLight,
                iconTint = DsColors.Primary,
                title    = "Politique de commission",
                subtitle = "Définir l'objectif et le mode de calcul des primes",
                onClick  = { showCommissionPolicy = true }
            )
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