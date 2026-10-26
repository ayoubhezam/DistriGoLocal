package com.distrigo.app.ui.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The lists' « + » at the bottom right: round, and smaller than Material's 56dp rounded square (owner, 2026-10-26). */
val DsFabSize = 48.dp

/**
 * A list's « + », placed by the caller at its bottom right above the bottom bar
 * (`Modifier.align(Alignment.BottomEnd).padding(end = DsSpacing.lg, bottom = DsSpacing.fabBottomClearance)`).
 */
@Composable
fun DsFab(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick        = onClick,
        containerColor = DsColors.PrimaryDark,
        contentColor   = Color.White,
        shape          = CircleShape,
        modifier       = modifier.size(DsFabSize)
    ) {
        Icon(Icons.Default.Add, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
    }
}
