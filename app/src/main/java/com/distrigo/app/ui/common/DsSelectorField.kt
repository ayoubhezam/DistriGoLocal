package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

@Composable
fun DsSelectorField(
    label       : String?,
    value       : String,
    placeholder : String,
    error       : String = "",
    /** Filled for the user and not changeable: no sheet, a lock instead of the arrow. */
    locked      : Boolean = false,
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
                .background(if (locked) DsColors.SurfaceSunken else Color.Transparent)
                .clickable(enabled = !locked, onClick = onClick)
                .padding(horizontal = DsSpacing.md, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text     = value.ifBlank { placeholder },
                fontSize = DsTextSize.body,
                color    = if (value.isBlank()) DsColors.TextTertiary else DsColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (locked) Icons.Outlined.Lock else Icons.Default.ArrowDropDown,
                contentDescription = if (locked) stringResource(R.string.common_cd_locked) else null,
                tint = DsColors.TextSecondary,
                modifier = if (locked) Modifier.size(20.dp) else Modifier,
            )
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