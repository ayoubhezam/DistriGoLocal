package com.distrigo.app.ui.products

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.distrigo.app.data.model.PriceMovementKind
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.suppliers.formatDZD
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** The colour each side of the trade is drawn in, matching the summary blocks. */
internal fun PriceMovementKind.chartColor(): Color =
    if (this == PriceMovementKind.ACHAT) DsColors.Success else DsColors.Primary

/** What the finger is currently on: one plotted point of one line. */
private data class Scrubbed(val series: Int, val point: Int)

/**
 * A product's prices over time, one line per kind, scrubbable with a finger.
 *
 * ### Keeping the drag off the composition
 *
 * The scrub position is a [MutableState] read **only inside the drawing lambda** and by the readout
 * above the chart. A state read in the draw phase does not recompose: dragging redraws the canvas
 * and relabels one row, and nothing else on the screen is touched — not the summaries, not the list.
 * The drawing itself stays cheap because the points are already thinned to at most a few dozen by
 * `chartSeries`, so a moving finger costs one nearest-point scan and one redraw.
 *
 * ### The gesture
 *
 * One `awaitEachGesture` serves both a tap and a scrub: the nearest point is selected on the **down**
 * event rather than after the touch slop, so a tap and the start of a drag feel identical. Only
 * mostly-horizontal movement is consumed, leaving a vertical swipe to scroll the screen underneath.
 */
@Composable
fun PriceChart(
    series   : List<PriceSeries>,
    grouping : PriceGrouping,
    modifier : Modifier = Modifier,
) {
    if (series.isEmpty() || series.all { it.points.isEmpty() }) return

    val days   = series.flatMap { s -> s.points.map { it.day } }
    val prices = series.flatMap { s -> s.points.map { it.price } }
    val firstDay = days.min()
    val lastDay  = days.max()
    val span     = (lastDay.toEpochDay() - firstDay.toEpochDay()).coerceAtLeast(1).toFloat()
    // A flat line would divide by zero and sit on the axis; give it room above and below.
    val low  = if (prices.min() == prices.max()) prices.min() - 5 else prices.min()
    val high = if (prices.min() == prices.max()) prices.max() + 5 else prices.max()
    val pad  = (high - low) * 0.15
    val lo   = (low - pad).toFloat()
    val hi   = (high + pad).toFloat()

    var scrubbed by remember(series) { mutableStateOf<Scrubbed?>(null) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val axisLabel = remember { DateTimeFormatter.ofPattern("dd/MM") }

    Column(modifier) {
        // A fixed-height row, so a scrub never moves the layout under the finger.
        Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.CenterStart) {
            val current = scrubbed?.let { series.getOrNull(it.series)?.points?.getOrNull(it.point) to series.getOrNull(it.series)?.kind }
            val point = current?.first
            val kind  = current?.second
            if (point != null && kind != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    Box(Modifier.size(10.dp).clip(DsShapes.pill).background(kind.chartColor()))
                    Text(
                        "${formatDZD(point.price)} DA",
                        fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary
                    )
                    Text(
                        pointCaption(kind, point, grouping),
                        fontSize = DsTextSize.caption, color = DsColors.TextSecondary
                    )
                }
            } else {
                Text(
                    grouping.caption ?: "Touchez le graphique pour lire un prix",
                    fontSize = DsTextSize.caption, color = DsColors.TextTertiary
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .semantics {
                    contentDescription = "Évolution des prix du ${axisLabel.format(firstDay)} au ${axisLabel.format(lastDay)}, " +
                        "de ${formatDZD(prices.min())} à ${formatDZD(prices.max())} DA. La liste ci-dessous donne le détail."
                }
                .pointerInput(series) {
                    val plot = plotArea(size.toSize(), density.density)
                    fun select(x: Float) {
                        val found = nearestPoint(series, x, plot, firstDay, span)
                        if (found != null && found != scrubbed) {
                            scrubbed = found
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    awaitEachGesture {
                        // On the down event, not after the slop: a tap and a scrub start alike.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        select(down.position.x)
                        var horizontal = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val movement = change.positionChange()
                            // Claim the gesture only once it is mostly sideways; a vertical swipe
                            // stays the scrolling screen's.
                            if (!horizontal && abs(movement.x) > abs(movement.y) && abs(movement.x) > 0f) horizontal = true
                            if (horizontal) {
                                change.consume()
                                select(change.position.x)
                            }
                        }
                    }
                }
        ) {
            val plot = plotArea(size, density.density)
            drawGrid(plot, lo, hi)
            series.forEachIndexed { index, line ->
                drawSeries(line, plot, firstDay, span, lo, hi, selected = scrubbed?.takeIf { it.series == index }?.point)
            }
            scrubbed?.let { s ->
                val point = series.getOrNull(s.series)?.points?.getOrNull(s.point) ?: return@let
                val x = plot.xOf(point.day, firstDay, span)
                drawLine(
                    color = DsColors.TextTertiary,
                    start = Offset(x, plot.top),
                    end   = Offset(x, plot.bottom),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(axisLabel.format(firstDay), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            if (span > 1f) {
                Text(
                    axisLabel.format(firstDay.plusDays((span / 2).toLong())),
                    fontSize = DsTextSize.caption, color = DsColors.TextSecondary
                )
                Text(axisLabel.format(lastDay), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
        }
    }
}

/** What the readout says beside the price: the kind, and the date or the period the point covers. */
private fun pointCaption(kind: PriceMovementKind, point: PricePoint, grouping: PriceGrouping): String {
    val day = DateTimeFormatter.ofPattern("dd/MM/yyyy").format(point.day)
    val when_ = when (grouping) {
        PriceGrouping.DAILY   -> day
        PriceGrouping.WEEKLY  -> "semaine du $day"
        PriceGrouping.MONTHLY -> DateTimeFormatter.ofPattern("MM/yyyy").format(point.day)
    }
    val averaged = if (point.count > 1) " · moyenne de ${point.count}" else ""
    return "${kind.label} · $when_$averaged"
}

/** Where the lines are drawn, inside the canvas: room is left for the price labels and the dates. */
private class Plot(val left: Float, val right: Float, val top: Float, val bottom: Float) {
    fun xOf(day: LocalDate, first: LocalDate, span: Float): Float =
        left + (day.toEpochDay() - first.toEpochDay()) / span * (right - left)

    fun yOf(price: Float, lo: Float, hi: Float): Float =
        bottom - (price - lo) / (hi - lo) * (bottom - top)
}

private fun plotArea(size: Size, density: Float) = Plot(
    left   = 34f * density,
    right  = size.width - 6f * density,
    top    = 8f * density,
    bottom = size.height - 8f * density,
)

private fun DrawScope.drawGrid(plot: Plot, lo: Float, hi: Float) {
    listOf(lo, (lo + hi) / 2f, hi).forEach { value ->
        val y = plot.yOf(value, lo, hi)
        drawLine(DsColors.Border, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1f)
    }
}

private fun DrawScope.drawSeries(
    line: PriceSeries, plot: Plot, firstDay: LocalDate, span: Float, lo: Float, hi: Float, selected: Int?,
) {
    val colour = line.kind.chartColor()
    val offsets = line.points.map { Offset(plot.xOf(it.day, firstDay, span), plot.yOf(it.price.toFloat(), lo, hi)) }
    if (offsets.size > 1) {
        val path = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            offsets.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, colour, style = Stroke(width = 2.dp.toPx()))
    }
    offsets.forEachIndexed { index, offset ->
        val last = index == offsets.lastIndex
        val marked = index == selected
        val radius = if (marked) 6.dp.toPx() else if (last) 4.5.dp.toPx() else 3.dp.toPx()
        drawCircle(if (marked || last) colour else DsColors.Surface, radius, offset)
        drawCircle(colour, radius, offset, style = Stroke(width = 2.dp.toPx()))
    }
}

/** The point nearest [x], across every line — the finger need not be on a line to read one. */
private fun nearestPoint(series: List<PriceSeries>, x: Float, plot: Plot, firstDay: LocalDate, span: Float): Scrubbed? {
    var best: Scrubbed? = null
    var bestDistance = Float.MAX_VALUE
    series.forEachIndexed { s, line ->
        line.points.forEachIndexed { p, point ->
            val distance = abs(plot.xOf(point.day, firstDay, span) - x)
            if (distance < bestDistance) {
                bestDistance = distance
                best = Scrubbed(s, p)
            }
        }
    }
    return best
}
