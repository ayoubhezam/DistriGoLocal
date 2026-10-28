package com.distrigo.app.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

/**
 * What an ⓘ explains: a [summary] paragraph, then [toKnow] as the « À savoir » points. Screens keep their own text
 * short and put the rest here (owner, 2026-10-21).
 */
class DsInfo(val title: String, val icon: ImageVector, val tint: Color, val summary: String, val toKnow: List<String> = emptyList())

/** The ⓘ beside a title: 48 dp to tap, read as « En savoir plus sur … ». */
@Composable
fun DsInfoButton(about: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.common_cd_learn_more, about), tint = DsColors.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

/** An ⓘ's explanation, as a sheet: the subject's icon and title, the summary, « À savoir », and « Compris ». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DsInfoSheet(info: DsInfo, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = DsColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = DsSpacing.lg, end = DsSpacing.lg, bottom = DsSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(DsShapes.large).background(info.tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(info.icon, contentDescription = null, tint = info.tint, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(DsSpacing.md))
                Text(info.title, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_action_close), tint = DsColors.TextSecondary)
                }
            }
            Text(info.summary, fontSize = DsTextSize.body, color = DsColors.TextPrimary)
            if (info.toKnow.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.SurfaceMuted).padding(DsSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(DsSpacing.sm))
                        Text(stringResource(R.string.common_info_to_know), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark)
                    }
                    info.toKnow.forEach { point ->
                        Row {
                            Text("•", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(start = DsSpacing.xs, end = DsSpacing.sm))
                            Text(point, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                        }
                    }
                }
            }
            Button(
                onClick  = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape    = DsShapes.medium,
                colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary),
            ) {
                Text(stringResource(R.string.common_action_got_it), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}
