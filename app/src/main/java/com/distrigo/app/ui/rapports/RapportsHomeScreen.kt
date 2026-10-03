package com.distrigo.app.ui.rapports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsTopBarSize

/** One report of the list. [onOpen] is null for a report not built yet, shown as "Bientôt". */
private class ReportEntry(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val subtitle: String,
    val onOpen: (() -> Unit)?,
)

/**
 * Rapports: the reports, a short list rather than a grid of every view a reporting app can offer —
 * see the plan of 2026-10-02. Each is one screen under the same period filter.
 */
@Composable
fun RapportsHomeScreen(onBack: () -> Unit, onOpenVentes: () -> Unit, onOpenDettes: () -> Unit) {
    val entries = listOf(
        ReportEntry(Icons.Default.ShoppingCart, DsColors.Primary, "Ventes", "Chiffre d'affaires, crédit, marge, par jour", onOpenVentes),
        ReportEntry(Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF9333EA), "Produits", "Meilleurs produits, catégories, analyse ABC", null),
        ReportEntry(Icons.Default.People, Color(0xFF0E9384), "Clients et fournisseurs", "Meilleurs clients, achats par fournisseur", null),
        ReportEntry(Icons.Default.AccountBalanceWallet, DsColors.Warning, "Créances et dettes", "Qui doit quoi, depuis quand", onOpenDettes),
        ReportEntry(Icons.Default.Inventory2, Color(0xFF667085), "Stock et pertes", "Valeur du stock, ruptures, pertes", null),
        ReportEntry(Icons.Default.PieChart, DsColors.Success, "Résultat", "Marge, charges et pertes : le bénéfice", null),
        ReportEntry(Icons.Default.LocalShipping, Color(0xFFE91E63), "Tournées", "Ventes, encaissements et visites par tournée", null),
    )

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(
            title = "Rapports",
            subtitle = "Statistiques et performances",
            leading = DsTopBarLeading.Back(onBack),
            size = DsTopBarSize.Large,
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
        ) {
            items(entries, key = { it.title }) { EntryRow(it) }
        }
    }
}

@Composable
private fun EntryRow(entry: ReportEntry) {
    val open = entry.onOpen
    Row(
        Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(DsColors.SurfaceMuted)
            .then(if (open != null) Modifier.clickable(role = Role.Button, onClick = open) else Modifier)
            .padding(DsSpacing.md)
            .alpha(if (open != null) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(DsShapes.small).background(entry.color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(entry.icon, contentDescription = null, tint = entry.color, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(entry.title, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text(entry.subtitle, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
        if (open != null) {
            Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(14.dp))
        } else {
            Text(
                "Bientôt", fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
                modifier = Modifier.clip(DsShapes.pill).background(DsColors.SurfaceSunken).padding(horizontal = DsSpacing.sm, vertical = 2.dp),
            )
        }
    }
}
