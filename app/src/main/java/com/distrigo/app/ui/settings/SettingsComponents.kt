package com.distrigo.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

/** What a row not built yet says, in place of its subtitle (owner, 2026-10-21). */
const val SOON = "Bientôt disponible"

private val IconTile = 44.dp

/** A group of Paramètres: its title, then its rows in one white card, a hairline between each. */
@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark,
            modifier = Modifier.padding(start = DsSpacing.xs, bottom = DsSpacing.sm),
        )
        Column(
            Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.large),
            content = content,
        )
    }
}

/** Between two rows of a section, from under the titles to the edge. */
@Composable
fun SettingsDivider() {
    HorizontalDivider(color = DsColors.Border, thickness = 1.dp, modifier = Modifier.padding(start = DsSpacing.lg + IconTile + DsSpacing.md))
}

/**
 * A row of Paramètres: its icon on a tile of its [tint], its title and [subtitle], and [trailing] — a chevron when it
 * opens something. [enabled] false is a row not built yet: faded, not tappable, read as unavailable, its subtitle
 * saying so.
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(
                if (enabled && onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
                else Modifier
            )
            .then(if (enabled) Modifier else Modifier.semantics { disabled() })
            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val faded = if (enabled) Modifier else Modifier.alpha(0.45f)
        Box(
            faded.size(IconTile).clip(DsShapes.medium).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark, modifier = faded)
            val text = if (enabled) subtitle else SOON
            if (text != null) {
                Text(
                    text, fontSize = DsTextSize.caption,
                    color = if (enabled) DsColors.TextSecondary else DsColors.TextTertiary,
                    fontWeight = if (enabled) FontWeight.Normal else FontWeight.Medium,
                )
            }
        }
        when {
            !enabled -> Unit
            trailing != null -> { Spacer(Modifier.width(DsSpacing.sm)); trailing() }
            onClick != null -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
}
