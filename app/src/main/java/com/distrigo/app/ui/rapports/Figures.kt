package com.distrigo.app.ui.rapports

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.distrigo.app.ui.common.FitText
import com.distrigo.app.ui.common.TABULAR_FIGURES
import com.distrigo.app.core.format.MoneyFormatter
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** How long a figure or a bar takes to reach its new value: long enough to see, short enough not to wait. */
internal const val GLIDE_MILLIS = 450

internal fun <T> glide(): AnimationSpec<T> = tween(GLIDE_MILLIS, easing = FastOutSlowInEasing)

/**
 * What a report's card shows: a [value], and how to [write] it. When the value changes — another
 * period, another commune — the card counts from the old one to the new.
 *
 * A [value] of NaN is a text, not a number ("—", or nothing to show): it changes at once.
 */
class Figure(val value: Double, val write: (Double) -> String) {
    companion object {
        fun text(text: String) = Figure(Double.NaN) { text }
    }
}

/** An amount in DA, as the user's money setting writes it, after [prefix] if any ("− "). */
fun MoneyFormatter.figure(amount: Double, prefix: String = "") = Figure(amount) { prefix + da(it) }

/** A count, whole at every step. */
fun countFigure(count: Int) = Figure(count.toDouble()) { it.roundToLong().toString() }

/** A count of things, "1 produit", "12 produits", whole at every step. */
fun countFigure(count: Int, one: String, many: String) = Figure(count.toDouble()) { plural(it.roundToInt(), one, many) }

/** A share as a percentage, or "—" when there is none. */
fun rateFigure(rate: Double?) = if (rate == null) Figure.text("—") else Figure(rate) { percent(it) }

/**
 * A [figure] on one line that shrinks to fit, as FitText — and, when its value changes, counts from
 * the old value to the new one in [GLIDE_MILLIS]. Not on its first showing: a report opened is read
 * at once, only a change is shown moving.
 *
 * - The size is settled once, on the widest the figure will be on its way, by an unseen FitText: a
 *   FitText fed a new text every frame would settle again every frame, hidden meanwhile — a blink.
 * - It counts in Double, from a progress of 0 to 1: an amount of hundreds of millions has more digits
 *   than a Float animation keeps, and the last step writes the new value exactly.
 * - Its digits are all as wide (TABULAR_FIGURES), so a counting amount does not shake sideways.
 * - Only this text reads the progress: only it is drawn again each frame, not its card.
 */
@Composable
fun AnimatedFigure(
    figure: Figure,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    minScale: Float = 0.6f,
) {
    val target = figure.value
    val progress = remember { Animatable(1f) }
    var from by remember { mutableDoubleStateOf(target) }
    var to by remember { mutableDoubleStateOf(target) }
    var scale by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(target) {
        if (target.isNaN() || to.isNaN() || target == to) {
            from = target
            to = target
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        // From where the figure stands — part of the way, if it was still counting.
        from += (to - from) * progress.value
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, glide())
    }

    val numbers = !target.isNaN() && !to.isNaN()
    val p = progress.value
    val shown = when {
        !numbers -> target
        p >= 1f -> to
        else -> from + (to - from) * p
    }
    val widest = if (numbers) listOf(from, to, target).map(figure.write).maxBy { it.length } else figure.write(target)

    Box(modifier) {
        FitText(
            widest, fontSize = fontSize, color = color, fontWeight = fontWeight, textAlign = textAlign,
            minScale = minScale, tabular = true, onFit = { scale = it },
            modifier = Modifier.drawWithContent { }.clearAndSetSemantics { },
        )
        scale?.let { s ->
            Text(
                figure.write(shown), fontSize = fontSize * s, color = color, fontWeight = fontWeight, textAlign = textAlign,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR_FIGURES),
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * [target] values — a ring's angles, a chart's heights — that move together from the last ones when
 * they change, so a ring stays whole all the way; not on their first showing. When [layout] changes
 * — other days, another split — the old values mean nothing for the new: they start from zero, a chart
 * rising from its baseline. Read in a draw: only the drawing is done again each frame. A list longer
 * than the other meets it at zero.
 */
@Composable
internal fun glidingValues(target: List<Float>, layout: Any? = null): () -> List<Float> {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    var drawn by remember { mutableStateOf(layout) }
    LaunchedEffect(target, layout) {
        if (target == to && layout == drawn) return@LaunchedEffect
        from = if (layout == drawn) between(from, to, progress.value) else emptyList()
        drawn = layout
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, glide())
    }
    return { between(from, to, progress.value) }
}

private fun between(a: List<Float>, b: List<Float>, p: Float): List<Float> =
    List(maxOf(a.size, b.size)) { i ->
        val x = a.getOrElse(i) { 0f }
        x + (b.getOrElse(i) { 0f } - x) * p
    }
