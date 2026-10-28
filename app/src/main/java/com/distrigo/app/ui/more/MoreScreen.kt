package com.distrigo.app.ui.more

import com.distrigo.app.ui.designsystem.ProvideArabicTrialFonts
import com.distrigo.app.ui.designsystem.LocalArabicTrialFonts
import androidx.compose.ui.unit.sp
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
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarSize
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R
private data class MoreMenuEntry(
    val icon       : ImageVector,
    @androidx.annotation.StringRes val title    : Int,
    @androidx.annotation.StringRes val subtitle : Int,
    val iconBg     : Color,
    val route      : String
)

private val MORE_MENU_ENTRIES = listOf(
    MoreMenuEntry(Icons.Default.People, R.string.more_clients, R.string.more_clients_sub, DsColors.AccentOrange, "clients"),
    MoreMenuEntry(Icons.Default.Business, R.string.more_suppliers, R.string.more_suppliers_sub, DsColors.AccentViolet, "fournisseurs"),
    MoreMenuEntry(Icons.Default.BarChart, R.string.more_reports, R.string.more_reports_sub, DsColors.AccentRose, "rapports"),
    MoreMenuEntry(Icons.Default.Settings, R.string.more_settings, R.string.more_settings_sub, DsColors.AccentTeal, "parametres"),
    MoreMenuEntry(Icons.Default.Payments, R.string.more_charges, R.string.more_charges_sub, DsColors.Primary, "charges"),
    MoreMenuEntry(Icons.Default.DeleteSweep, R.string.more_pertes, R.string.more_pertes_sub, DsColors.Danger, "pertes"),
    MoreMenuEntry(Icons.Default.Inventory2, R.string.more_inventory, R.string.more_inventory_sub, DsColors.Success, "inventaire"),
    MoreMenuEntry(Icons.Default.Warehouse, R.string.more_truck_stock, R.string.more_truck_stock_sub, DsColors.AccentSky, "stock_camion"),
    )

@Composable
fun MoreScreen(
    onNavigate : (String) -> Unit
) {
    // The owner's Arabic font trial (2026-10-28), with the Dashboard: Arabic only
    ProvideArabicTrialFonts {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DsColors.Surface)
        ) {
            // A drawer is a sheet, white as menus and dialogs: its bar too, not the pages' Mist
            DsTopAppBar(title = stringResource(R.string.more_title), size = DsTopBarSize.Large, containerColor = DsColors.Surface)

            // The bar is pinned, so scrolling moved off the root onto the menu list below it.
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                MORE_MENU_ENTRIES.forEachIndexed { index, entry ->
                    MoreMenuItem(
                        icon     = entry.icon,
                        title    = stringResource(entry.title),
                        subtitle = stringResource(entry.subtitle),
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
            // In the Arabic trial: Cairo Bold 1 sp up for the title, Plex Medium 1 sp up for what it does
            val trial = LocalArabicTrialFonts.current
            Text(
                title, fontSize = if (trial != null) 15.sp else DsTextSize.body,
                fontWeight = if (trial != null) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = trial?.ui, color = DsColors.TextPrimary,
            )
            Text(subtitle, fontSize = if (trial != null) 12.sp else DsTextSize.caption, color = DsColors.TextSecondary)
        }

        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(18.dp))
    }
}
