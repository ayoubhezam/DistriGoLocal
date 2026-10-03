package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

// How a list is sorted, the same everywhere: the "Trier" chip of Produits' controls row and its
// "Trier par" sheet, taken out of Produits so every list that sorts looks and behaves the same.

/** "Trier", a sunken chip; blue while a sort other than the list's default is on. */
@Composable
fun ListSortChip(active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(DsShapes.medium)
            .background(DsColors.SurfaceSunken)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Sort,
                contentDescription = "Trier",
                tint = if (active) DsColors.Primary else DsColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Trier",
                fontSize = DsTextSize.caption,
                color = if (active) DsColors.Primary else DsColors.TextSecondary
            )
        }
    }
}

/** "Trier par": the [options], the [selected] one ticked. Choosing one applies it and closes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SortOptionsSheet(
    options   : List<T>,
    selected  : T,
    label     : (T) -> String,
    onSelect  : (T) -> Unit,
    onDismiss : () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(),
        containerColor   = DsColors.Surface
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            Text("Trier par", fontWeight = FontWeight.Bold, fontSize = DsTextSize.bodyLarge, color = DsColors.TextPrimary)
            Spacer(Modifier.height(DsSpacing.md))
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.medium)
                        .background(if (selected == option) DsColors.PrimaryLight else Color.Transparent)
                        .clickable { onSelect(option); onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = label(option),
                        fontSize   = DsTextSize.body,
                        color      = if (selected == option) DsColors.Primary else DsColors.TextPrimary,
                        fontWeight = if (selected == option) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (selected == option) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
