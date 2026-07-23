package com.distrigo.app.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

@Composable
fun DsSelectorField(
    label       : String?,
    value       : String,
    placeholder : String,
    error       : String = "",
    onClick     : () -> Unit
) {
    Column {
        if (label != null) {
            Text(
                label,
                fontSize = DsTextSize.bodySmall,
                color    = DsColors.TextSecondary,
                modifier = Modifier.padding(bottom = DsSpacing.xs)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (error.isNotEmpty()) DsColors.Danger else DsColors.Border, DsShapes.medium)
                .clip(DsShapes.medium)
                .clickable(onClick = onClick)
                .padding(horizontal = DsSpacing.md, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text     = value.ifBlank { placeholder },
                fontSize = DsTextSize.body,
                color    = if (value.isBlank()) DsColors.TextTertiary else DsColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = DsColors.TextSecondary)
        }
        if (error.isNotEmpty()) {
            Text(
                error,
                fontSize = DsTextSize.caption,
                color    = DsColors.Danger,
                modifier = Modifier.padding(start = DsSpacing.xs, top = 2.dp)
            )
        }
    }
}