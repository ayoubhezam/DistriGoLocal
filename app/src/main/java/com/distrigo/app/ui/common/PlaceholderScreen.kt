package com.distrigo.app.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading

@Composable
fun PlaceholderScreen(
    title    : String,
    subtitle : String = "Cette fonctionnalité arrive bientôt.",
    icon     : ImageVector = Icons.Default.Construction,
    onBack   : (() -> Unit)? = null
) {
    onBack?.let { BackHandler { it() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        // Title stays empty on purpose — the placeholder already states it in the centred body,
        // and repeating it in the bar would read as a duplicate.
        onBack?.let { DsTopAppBar(title = "", leading = DsTopBarLeading.Back(it)) }

        Box(
            modifier         = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier         = Modifier.size(88.dp).clip(DsShapes.large).background(DsColors.SurfaceMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(DsSpacing.lg))
                Text(title, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Spacer(Modifier.height(DsSpacing.xs))
                Text(subtitle, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
            }
        }
    }
}