package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsTextSize

/** A pill that filters a list: outlined when off, filled with [activeBg] when on. Clients and Tournées. */
@Composable
fun DsFilterChip(
    label        : String,
    active       : Boolean,
    activeBg     : Color = DsColors.PrimaryLight,
    activeBorder : Color = DsColors.Primary,
    activeText   : Color = DsColors.Primary,
    onClick      : () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(DsShapes.pill)
            .background(if (active) activeBg else DsColors.Surface)
            .border(1.dp, if (active) activeBorder else DsColors.Border, DsShapes.pill)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            fontSize   = DsTextSize.bodySmall,
            color      = if (active) activeText else DsColors.TextSecondary,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
