package com.distrigo.app.ui.products

import android.graphics.Paint
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.distrigo.app.data.model.PriceMovementKind
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.suppliers.formatDZD
import kotlin.math.abs
import kotlin.math.roundToInt

/** The colour each side of the trade is drawn in, matching the summary blocks. */
internal fun PriceMovementKind.chartColor(): Color =
    if (this == PriceMovementKind.ACHAT) DsColors.Success else DsColors.Primary

/** What the finger is currently on: one plotted point of one line. */
private data class Scrubbed(val series: Int, val point: Int)

/**
 * A product's prices across the period's slots, one line per kind, scrubbable with a finger.
 *
 * ### The axis belongs to the period
 *
 * The slots come from the period alone — seven days, four weeks, twelve months — so the labels read
 * « Lun Mar Mer… », « S1…S4 » or « Jan Fév Mar… », and both lines sit on the same footing. A slot
 * with no movement carries the last price forward and is drawn **without a dot**: the line stays
 * continuous, as a price in force is, while only real transactions are marked.
 *
 * ### Keeping the drag off the composition
 *
 * The scrub position is a [MutableState] read only inside the drawing lambda and by the readout above
 * the chart. A state read in the draw phase does not recompose: dragging redraws the canvas and
 * relabels one row, and nothing else on the screen is touched.
 *
 * One `awaitEachGesture` serves both a tap and a scrub — the nearest point is taken on the **down**
 * event, not after the touch slop — and only mostly-horizontal movement is consumed, leaving a
 * vertical swipe to scroll the screen underneath.
 */
@Composable
fun PriceChart(
    series   : List<PriceSeries>,
    period   : PricePeriod,
    slots    : List<PriceSlot>,
    modifier : Modifier = Modifier,
) {
    if (series.isEmpty() || slots.isEmpty()) return

    val prices = series.flatMap { s -> s.points.map { it.price } }
    // A flat line would divide by zero and sit on the axis; give it room above and below.
    val spread = if (prices.min() == prices.max()) 5.0 else (prices.max() - prices.min()) * 0.15
    val lo = (prices.min() - spread).toFloat()
    val hi = (prices.max() + spread).toFloat()

    var scrubbed by remember(series) { mutableStateOf<Scrubbed?>(null) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    Column(modifier) {
        // A fixed-height row, so a scrub never moves the layout under the finger.
        Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.CenterStart) {
            val line  = scrubbed?.let { series.getOrNull(it.series) }
            val point = scrubbed?.let { line?.points?.getOrNull(it.point) }
            if (line != null && point != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    Box(Modifier.size(10.dp).clip(DsShapes.pill).background(line.kind.chartColor()))
                    Text(
                        "${formatDZD(point.price)} DA",
                        fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary
                    )
                    Text(pointCaption(line.kind, point), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                }
            } else {
                Text(
                    "Touchez le graphique pour lire un prix",
                    fontSize = DsTextSize.caption, color = DsColors.TextTertiary
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .semantics {
                    contentDescription = "Évolution des prix sur ${period.label}, de ${formatDZD(prices.min())} " +
                        "à ${formatDZD(prices.max())} DA. La liste ci-dessous donne le détail."
                }
                .pointerInput(series, slots) {
                    val plot = plotArea(size.toSize(), density.density)
                    fun select(x: Float) {
                        val found = nearestPoint(series, slots.size, x, plot)
                        if (found != null && found != scrubbed) {
                            scrubbed = found
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        select(down.position.x)
                        var horizontal = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val movement = change.positionChange()
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
                drawSeries(line, plot, slots.size, lo, hi, selected = scrubbed?.takeIf { it.series == index }?.point)
            }
            scrubbed?.let { s ->
                val point = series.getOrNull(s.series)?.points?.getOrNull(s.point) ?: return@let
                val x = plot.xOf(point.slot.index, slots.size)
                drawLine(
                    color = DsColors.TextTertiary,
                    start = Offset(x, plot.top),
                    end   = Offset(x, plot.bottom),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            }
        }

        // Each slot's own label, under the point it belongs to. Twelve of them would collide on a
        // phone, so every other one is named — counted back from the newest, which is the slot a
        // price is read against and must never be the one left unnamed. The scrub readout names the
        // rest. A label is allowed to overflow into its blank neighbours rather than be clipped:
        // a twelfth of a phone's width does not hold « Sept ».
        val everyOther = slots.size > 8
        Row(Modifier.fillMaxWidth().padding(start = AXIS_GUTTER.dp, top = 2.dp)) {
            slots.forEach { slot ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (!everyOther || (slots.lastIndex - slot.index) % 2 == 0) {
                        Text(
                            slot.label,
                            fontSize  = DsTextSize.caption,
                            color     = DsColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            maxLines  = 1,
                            modifier  = Modifier.wrapContentWidth(unbounded = true)
                        )
                    }
                }
            }
        }
    }
}

/** What the readout says beside the price: the kind, the slot, and whether the price was carried. */
private fun pointCaption(kind: PriceMovementKind, point: PricePoint): String = when {
    point.carried   -> "${kind.label} · ${point.slot.label} · prix en vigueur"
    point.count > 1 -> "${kind.label} · ${point.slot.label} · moyenne de ${point.count}"
    else            -> "${kind.label} · ${point.slot.label}"
}

/** Room left at the left edge for the price labels, in dp. */
private const val AXIS_GUTTER = 38

/** Where the lines are drawn inside the canvas; a slot's point sits in the middle of its share. */
private class Plot(val left: Float, val right: Float, val top: Float, val bottom: Float) {
    fun xOf(slot: Int, slots: Int): Float {
        val step = (right - left) / slots
        return left + step * (slot + 0.5f)
    }

    fun yOf(price: Float, lo: Float, hi: Float): Float =
        bottom - (price - lo) / (hi - lo) * (bottom - top)
}

private fun plotArea(size: Size, density: Float) = Plot(
    left   = AXIS_GUTTER * density,
    right  = size.width,
    top    = 8f * density,
    bottom = size.height - 8f * density,
)

/** Three grid lines, each labelled with the price it stands for. */
private fun DrawScope.drawGrid(plot: Plot, lo: Float, hi: Float) {
    val paint = Paint().apply {
        color = DsColors.TextSecondary.toArgb()
        textSize = 10.dp.toPx()
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }
    listOf(hi, (lo + hi) / 2f, lo).forEach { value ->
        val y = plot.yOf(value, lo, hi)
        drawLine(DsColors.Border, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1f)
        drawContext.canvas.nativeCanvas.drawText(
            value.roundToInt().toString(), plot.left - 6.dp.toPx(), y + 4.dp.toPx(), paint
        )
    }
}

private fun DrawScope.drawSeries(
    line: PriceSeries, plot: Plot, slots: Int, lo: Float, hi: Float, selected: Int?,
) {
    val colour = line.kind.chartColor()
    val offsets = line.points.map { Offset(plot.xOf(it.slot.index, slots), plot.yOf(it.price.toFloat(), lo, hi)) }
    if (offsets.size > 1) {
        val path = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            offsets.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, colour, style = Stroke(width = 2.dp.toPx()))
    }
    // A dot marks a real transaction; a carried price is a line, not an event, so it has none —
    // unless the finger is on it, where the marker has to show what is being read. The last real
    // price of each line is filled, being the one in force.
    val lastReal = line.points.indexOfLast { !it.carried }
    line.points.forEachIndexed { index, point ->
        val marked = index == selected
        if (point.carried && !marked) return@forEachIndexed
        val filled = marked || index == lastReal
        val radius = if (marked) 6.dp.toPx() else 4.dp.toPx()
        drawCircle(if (filled && !point.carried) colour else DsColors.Surface, radius, offsets[index])
        drawCircle(colour, radius, offsets[index], style = Stroke(width = 2.dp.toPx()))
    }
}

/** The point nearest [x], across every line — the finger need not be on a line to read one. */
private fun nearestPoint(series: List<PriceSeries>, slots: Int, x: Float, plot: Plot): Scrubbed? {
    var best: Scrubbed? = null
    var bestDistance = Float.MAX_VALUE
    series.forEachIndexed { s, line ->
        line.points.forEachIndexed { p, point ->
            val distance = abs(plot.xOf(point.slot.index, slots) - x)
            if (distance < bestDistance) {
                bestDistance = distance
                best = Scrubbed(s, p)
            }
        }
    }
    return best
}
