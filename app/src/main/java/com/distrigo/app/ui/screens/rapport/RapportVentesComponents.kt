package com.distrigo.app.ui.screens.rapport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

/** بطاقة عنوان التقرير: الفترة + تاريخ التوليد. لا تفاعلية هنا، عرض فقط. */
@Composable
fun ReportHeaderCard(
    periodeLabel: String,
    genereLeLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface, DsShapes.large)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .padding(DsSpacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text("Période du rapport", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
            Text(
                periodeLabel,
                fontSize = DsTextSize.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = DsColors.TextPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(genereLeLabel, fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
    }
}

/** شريط أفقي واحد مقسوم بنسبة "Clients avec vente" مقابل "Clients sans vente" + Legend أسفله (بلا تفاعل، تقرير رسمي) */
@Composable
fun ClientsCoverageBar(
    clientsAvecVente: Int,
    clientsSansVente: Int,
    avecFraction: Float,
    modifier: Modifier = Modifier
) {
    val total = (clientsAvecVente + clientsSansVente).coerceAtLeast(1)
    val avecPercent = percentOf(clientsAvecVente, total)
    val sansPercent = percentOf(clientsSansVente, total)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface, DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .padding(DsSpacing.lg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(DsColors.SurfaceSunken, DsShapes.pill)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(avecFraction.coerceIn(0f, 1f))
                    .background(DsColors.Success, DsShapes.pill)
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(DsColors.Warning, DsShapes.pill)
            )
        }

        Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CoverageLegendRow(DsColors.Success, "Clients avec vente", "$clientsAvecVente · $avecPercent%")
            CoverageLegendRow(DsColors.Warning, "Clients sans vente", "$clientsSansVente · $sansPercent%")
        }
    }
}

@Composable
private fun CoverageLegendRow(dotColor: Color, label: String, valueText: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
        Text(label, fontSize = DsTextSize.bodySmall, color = Color(0xFF344054), modifier = Modifier.weight(1f))
        Text(valueText, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
    }
}

/** صف زري "Imprimer" / "Exporter en PDF". الأحداث تُمرَّر من الشاشة الأم فقط — لا منطق طباعة هنا. */
@Composable
fun ExportActionsRow(
    onPrintClick: () -> Unit,
    onExportPdfClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.SurfaceSunken, DsShapes.medium)
            .padding(DsSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ExportButton(
            label = "Imprimer",
            icon = Icons.Filled.Print,
            containerColor = DsColors.Surface,
            contentColor = DsColors.TextPrimary,
            hasBorder = true,
            enabled = enabled,
            onClick = onPrintClick,
            modifier = Modifier.weight(1f)
        )
        ExportButton(
            label = "Exporter en PDF",
            icon = Icons.Filled.FileDownload,
            containerColor = DsColors.Primary,
            contentColor = Color.White,
            hasBorder = false,
            enabled = enabled,
            onClick = onExportPdfClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ExportButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    hasBorder: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = modifier
            .height(48.dp)
            .background(containerColor.copy(alpha = alpha), DsShapes.small)
            .let { if (hasBorder) it.border(1.dp, DsColors.Border, DsShapes.small) else it }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = contentColor.copy(alpha = alpha), modifier = Modifier.size(18.dp))
        Text(
            label,
            fontSize = DsTextSize.body,
            fontWeight = FontWeight.SemiBold,
            color = contentColor.copy(alpha = alpha),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}