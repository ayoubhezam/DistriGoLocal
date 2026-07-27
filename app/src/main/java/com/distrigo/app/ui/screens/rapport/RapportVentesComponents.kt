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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.time.LocalDate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.EmojiEvents


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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentesPeriodSection(
    selectedPeriod: ReportPeriod,
    periodeLabel: String,
    onPeriodSelected: (ReportPeriod) -> Unit,
    onCustomRangeSelected: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var showRangePicker by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        PeriodSelectorChips(
            selectedPeriod = selectedPeriod,
            onPeriodSelected = { period ->
                if (period == ReportPeriod.PERSONNALISEE) showRangePicker = true
                else onPeriodSelected(period)
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DsColors.PrimaryLight, DsShapes.medium)
                .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
            Text("Période du rapport :", fontSize = DsTextSize.caption, color = DsColors.Primary)
            Text(periodeLabel, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
        }
    }

    if (showRangePicker) {
        val rangeState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = rangeState.selectedStartDateMillis
                    val e = rangeState.selectedEndDateMillis
                    if (s != null && e != null) {
                        onCustomRangeSelected(
                            java.time.Instant.ofEpochMilli(s).atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                            java.time.Instant.ofEpochMilli(e).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    showRangePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showRangePicker = false }) { Text("Annuler") } }
        ) {
            DateRangePicker(state = rangeState, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun VenteKpiCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    label: String,
    value: String,
    trend: TrendInfo,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(DsColors.Surface, DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            Box(modifier = Modifier.size(32.dp).background(iconBg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
            Text(label, fontSize = DsTextSize.caption, fontWeight = FontWeight.Medium, color = DsColors.TextSecondary)
        }
        Text(value, fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val color = if (trend.isPositive) DsColors.Success else DsColors.Danger
            Text(
                text = "${if (trend.isPositive) "↑" else "↓"} ${trend.percentage}%",
                fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = color
            )
            Text("vs période précédente", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
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

private fun pointX(index: Int, count: Int, width: Float): Float =
    if (count > 1) (width / (count - 1)) * index else width / 2f

private fun nearestIndex(x: Float, width: Float, count: Int): Int {
    if (count <= 1) return 0
    val stepX = width / (count - 1)
    return (x / stepX).roundToInt().coerceIn(0, count - 1)
}

private fun niceCeiling(value: Double): Double {
    if (value <= 0.0) return 1.0
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(value)))
    val normalized = value / magnitude
    val nice = when {
        normalized <= 1 -> 1.0
        normalized <= 2 -> 2.0
        normalized <= 5 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

private fun formatAxisLabel(value: Double): String =
    if (value >= 1000) "${(value / 1000).roundToInt()}K" else value.roundToInt().toString()

@Composable
fun SalesEvolutionChart(
    points: List<SalesPoint>,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(points) { mutableStateOf(points.lastIndex.coerceAtLeast(0)) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val maxAmount = (points.maxOfOrNull { it.amount } ?: 0.0).coerceAtLeast(1.0)
    val yAxisMax = niceCeiling(maxAmount)
    val yAxisSteps = 4
    val primaryColor = DsColors.Primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface, DsShapes.large)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .padding(DsSpacing.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Évolution des ventes", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Par jour", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        if (points.isEmpty() || points.all { it.amount == 0.0 }) {
            Spacer(Modifier.height(DsSpacing.md))
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Text("Aucune donnée à afficher", fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary)
            }
            return@Column
        }

        Spacer(Modifier.height(DsSpacing.sm))
        Text("(DA)", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
        Spacer(Modifier.height(4.dp))

        Row(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            Column(
                modifier = Modifier.width(40.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                for (step in yAxisSteps downTo 0) {
                    Text(formatAxisLabel(yAxisMax * step / yAxisSteps), fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
                }
            }

            Spacer(Modifier.width(DsSpacing.sm))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(points) {
                        coroutineScope {
                            launch {
                                detectTapGestures { offset ->
                                    selectedIndex = nearestIndex(offset.x, size.width.toFloat(), points.size)
                                }
                            }
                            launch {
                                detectDragGestures(
                                    onDragStart = { offset -> selectedIndex = nearestIndex(offset.x, size.width.toFloat(), points.size) },
                                    onDrag = { change, _ -> selectedIndex = nearestIndex(change.position.x, size.width.toFloat(), points.size) }
                                )
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val path = Path()
                    val fillPath = Path()

                    points.forEachIndexed { index, point ->
                        val x = pointX(index, points.size, size.width)
                        val y = size.height - (point.amount / yAxisMax).toFloat() * size.height
                        if (index == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, size.height)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }
                    fillPath.lineTo(pointX(points.lastIndex, points.size, size.width), size.height)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.25f), primaryColor.copy(alpha = 0f)))
                    )
                    if (points.size > 1) {
                        drawPath(path = path, color = primaryColor, style = Stroke(width = 2.5.dp.toPx()))
                    }

                    val selX = pointX(selectedIndex, points.size, size.width)
                    val selY = size.height - (points[selectedIndex].amount / yAxisMax).toFloat() * size.height
                    drawLine(
                        color = DsColors.Border,
                        start = Offset(selX, 0f),
                        end = Offset(selX, size.height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                    drawCircle(color = primaryColor, radius = 5.dp.toPx(), center = Offset(selX, selY))
                    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(selX, selY))
                }

                if (canvasSize.width > 0f) {
                    val selX = pointX(selectedIndex, points.size, canvasSize.width)
                    val point = points[selectedIndex]
                    Column(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (selX - 60.dp.toPx()).toInt().coerceIn(0, (canvasSize.width - 120.dp.toPx()).toInt().coerceAtLeast(0)),
                                    y = 0
                                )
                            }
                            .width(120.dp)
                            .background(DsColors.Surface, DsShapes.small)
                            .border(1.dp, DsColors.Border, DsShapes.small)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(point.fullDateLabel, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                        Text(formatCurrency(point.amount.roundToInt()), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                    }
                }
            }
        }

        Spacer(Modifier.height(DsSpacing.sm))

        val labelIndices = if (points.size <= 10) points.indices.toList()
        else {
            val step = (points.size - 1) / 7.0
            (0..7).map { (it * step).roundToInt() }.distinct()
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            labelIndices.forEach { index -> Text(points[index].shortLabel, fontSize = DsTextSize.caption, color = DsColors.TextTertiary) }
        }
    }
}

@Composable
fun SourcesVentesCard(
    sources: List<SourceVenteItem>,
    modifier: Modifier = Modifier
) {
    val colors = listOf(DsColors.Primary, DsColors.Success) // Dépôt = bleu، Tournées = vert
    val total = sources.sumOf { it.amount }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface, DsShapes.large)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .padding(DsSpacing.lg)
    ) {
        Text("Sources des ventes", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        Spacer(Modifier.height(DsSpacing.lg))

        if (total <= 0.0) {
            Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                Text("Aucune donnée à afficher", fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary)
            }
            return@Column
        }

        Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(140.dp)) {
                val strokeWidth = 24.dp.toPx()
                var startAngle = -90f
                sources.forEachIndexed { index, item ->
                    val sweep = (item.amount / total * 360.0).toFloat()
                    drawArc(
                        color = colors.getOrElse(index) { DsColors.TextTertiary },
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                    )
                    startAngle += sweep
                }
            }
        }

        Spacer(Modifier.height(DsSpacing.lg))

        Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
            sources.forEachIndexed { index, item ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(colors.getOrElse(index) { DsColors.TextTertiary }, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(item.label, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary, modifier = Modifier.weight(1f))
                    Text("${item.percent}%", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(formatCurrency(item.amount.roundToInt()), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                }
            }
        }
    }
}


@Composable
fun WilayaRow(
    name: String, valueLabel: String, percent: Int,
    onClick: (() -> Unit)?, modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).background(DsColors.Border, DsShapes.pill)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((percent / 100f).coerceIn(0f, 1f)).background(DsColors.Primary, DsShapes.pill))
            }
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(horizontalAlignment = Alignment.End) {
            Text(valueLabel, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text("$percent%", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
        }
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun RepartitionWilayaCard(
    title: String,
    wilayas: List<com.distrigo.app.data.model.report.WilayaBreakdown>,
    valueLabel: (com.distrigo.app.data.model.report.WilayaBreakdown) -> String,
    onWilayaClick: (com.distrigo.app.data.model.report.WilayaBreakdown) -> Unit,
    modifier: Modifier = Modifier,
    headerIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    hintText: String = "Appuyez sur une wilaya pour voir la répartition par communes"
) {
    Column(
        modifier = modifier.fillMaxWidth().background(DsColors.Surface, DsShapes.large)
            .border(1.dp, DsColors.Border, DsShapes.large).padding(DsSpacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (headerIcon != null) {
                Icon(headerIcon, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(title, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }
        Spacer(Modifier.height(DsSpacing.md))
        if (wilayas.isEmpty()) {
            Text("Aucune donnée à afficher", fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary)
        } else {
            wilayas.forEachIndexed { index, w ->
                WilayaRow(name = w.name, valueLabel = valueLabel(w), percent = w.percent, onClick = { onWilayaClick(w) })
                if (index != wilayas.lastIndex) HorizontalDivider(color = DsColors.Border)
            }
            Spacer(Modifier.height(DsSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth().background(DsColors.PrimaryLight, DsShapes.medium).padding(DsSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.TouchApp, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(hintText, fontSize = DsTextSize.caption, color = DsColors.Primary)
            }
        }
    }
}

@Composable
fun TopSecteursCard(
    secteurs: List<com.distrigo.app.data.model.report.SecteurRankItem>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().background(DsColors.Surface, DsShapes.large)
            .border(1.dp, DsColors.Border, DsShapes.large).padding(DsSpacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = DsColors.Warning, modifier = Modifier.size(18.dp))
            Text("Top Secteurs", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }
        Spacer(Modifier.height(DsSpacing.md))
        if (secteurs.isEmpty()) {
            Text("Aucune donnée à afficher", fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary)
        } else {
            secteurs.forEachIndexed { index, item ->
                SecteurRankRow(item)
                if (index != secteurs.lastIndex) HorizontalDivider(color = DsColors.Border)
            }
        }
    }
}

@Composable
private fun SecteurRankRow(item: com.distrigo.app.data.model.report.SecteurRankItem) {
    val accentColor = if (item.isWeak) DsColors.Danger else DsColors.Success
    val badgeBg = if (item.isWeak) DsColors.DangerLight else DsColors.SuccessLight

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = DsSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(24.dp).background(badgeBg, CircleShape), contentAlignment = Alignment.Center) {
            Text("${item.rank}", fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = accentColor)
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth(0.6f).height(4.dp).background(DsColors.Border, DsShapes.pill)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((item.percent / 100f).coerceIn(0f, 1f)).background(accentColor, DsShapes.pill))
            }
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatCurrency(item.amount.roundToInt()), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text("${item.percent}%", fontSize = DsTextSize.caption, color = accentColor)
        }
    }
}

@Composable
fun <T : com.distrigo.app.data.model.report.GeoAmountItem> GeoDrillDownScreen(
    title: String,
    items: List<T>,
    valueLabel: (T) -> String,
    onBack: () -> Unit,
    onItemClick: ((T) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(DsColors.Surface).padding(DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = DsSpacing.lg),
            contentPadding = PaddingValues(vertical = DsSpacing.lg)
        ) {
            if (items.isEmpty()) {
                item { Text("Aucune donnée à afficher", fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary) }
            } else {
                itemsIndexed(items) { index, geoItem ->
                    WilayaRow(
                        name = geoItem.name, valueLabel = valueLabel(geoItem), percent = geoItem.percent,
                        onClick = onItemClick?.let { callback -> { callback(geoItem) } }
                    )
                    if (index != items.lastIndex) HorizontalDivider(color = DsColors.Border)
                }
            }
        }
    }
}