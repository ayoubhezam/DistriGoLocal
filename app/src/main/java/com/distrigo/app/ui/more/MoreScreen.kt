package com.distrigo.app.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

private data class MoreMenuEntry(
    val icon       : ImageVector,
    val title      : String,
    val subtitle   : String,
    val iconBg     : Color,
    val route      : String
)

private val MORE_MENU_ENTRIES = listOf(
    MoreMenuEntry(Icons.Default.Storefront, "Dépôt Vente", "Ventes depuis le dépôt", Color(0xFF2196F3), "depot_vente"),
    MoreMenuEntry(Icons.Default.People, "Clients", "Gérer vos clients et leurs comptes", Color(0xFFFF9800), "clients"),
    MoreMenuEntry(Icons.Default.Business, "Fournisseurs", "Gérer vos fournisseurs", Color(0xFF4CAF50), "fournisseurs"),
    MoreMenuEntry(Icons.Default.BarChart, "Rapports", "Statistiques et performances", Color(0xFFE91E63), "rapports"),
    MoreMenuEntry(Icons.Default.Inventory2, "Stock & Inventaire", "Stock dépôt et camion", Color(0xFF9C27B0), "stock_inventaire"),
    MoreMenuEntry(Icons.Default.Settings, "Paramètres", "Configuration de l'application", Color(0xFF009688), "parametres")
)

@Composable
fun MoreScreen(
    onNavigate : (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        Text(
            "Plus",
            fontSize   = DsTextSize.headline,
            fontWeight = FontWeight.ExtraBold,
            color      = DsColors.TextPrimary,
            modifier   = Modifier.padding(DsSpacing.lg)
        )

        Column {
            MORE_MENU_ENTRIES.forEachIndexed { index, entry ->
                MoreMenuItem(
                    icon     = entry.icon,
                    title    = entry.title,
                    subtitle = entry.subtitle,
                    iconBg   = entry.iconBg,
                    onClick  = { onNavigate(entry.route) }
                )
                if (index != MORE_MENU_ENTRIES.lastIndex) {
                    HorizontalDivider(
                        color     = DsColors.Border,
                        thickness = 0.5.dp,
                        modifier  = Modifier.padding(start = DsSpacing.lg + 44.dp + DsSpacing.md)
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreMenuItem(
    icon     : ImageVector,
    title    : String,
    subtitle : String,
    iconBg   : Color,
    onClick  : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier.size(44.dp).clip(DsShapes.medium).background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = DsSpacing.md)
        ) {
            Text(title, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text(subtitle, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }

        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(18.dp))
    }
}
