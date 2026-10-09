package com.distrigo.app.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import kotlin.math.roundToInt

/**
 * The share of a row of stat columns an amount gets, against 1 for each count beside it: an amount to
 * the centime is three or four times as long as a count.
 */
const val MONEY_STAT_WEIGHT = 1.6f

/** Digits all as wide as each other, so a figure that changes does not shift its neighbours. */
const val TABULAR_FIGURES = "tnum"

/**
 * One line of text that shrinks until it fits its width — for an amount in a row of stat columns.
 *
 * Amounts are written in full, to the centime ("7 928 534 259,60 DA"), and a no-break space keeps
 * them whole; in a column a third of the screen wide, a large one wrapped in the middle of a group
 * and pushed its neighbours' labels together. This keeps it on one line instead, shrinking the font
 * by steps of a tenth down to [minScale] of [fontSize], and ellipsizing only past that. [tabular]
 * writes its digits all as wide. Compose's own auto-size text arrived after the version this app uses.
 *
 * The size is settled in the layout pass that measures it, and the line drawn in the same frame: one
 * text layout for a line that fits as it is, no recomposition, nothing hidden while it settles. It used
 * to settle across frames — hidden, one recomposition and one text layout for each tenth it shrank,
 * and one more once settled — and a report opening laid out about a hundred lines that way (UI
 * fluidity audit, "Screen openings: traced").
 */
@Composable
fun FitText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    minScale: Float = 0.6f,
    tabular: Boolean = false,
) = FittedText(text, fitTo = listOf(text), fontSize, modifier, color, fontWeight, textAlign, minScale, tabular)

/**
 * [text] drawn on one line, at the largest step of a tenth of [fontSize] at which every line of [fitTo]
 * fits the width available, in a box as wide as the widest of them. For [FitText], the text itself; for
 * a counting figure, the values it passes through — measured, not counted in characters: "-6,7 %" and
 * "20,0 %" are as long, and not as wide — so its size holds still while it counts. Ellipsized only when
 * [fitTo] does not fit even at [minScale].
 */
@Composable
internal fun FittedText(
    text: String,
    fitTo: List<String>,
    fontSize: TextUnit,
    modifier: Modifier,
    color: Color,
    fontWeight: FontWeight?,
    textAlign: TextAlign?,
    minScale: Float,
    tabular: Boolean,
) {
    val measurer = rememberTextMeasurer()
    // Resolved as Text resolves it: the theme's text style, the colour given, else the style's, else
    // the content colour around it.
    val base = LocalTextStyle.current
    val contentColor = LocalContentColor.current
    val style = remember(base, contentColor, color, fontWeight, textAlign, tabular) {
        val merged = base.merge(
            TextStyle(
                fontWeight = fontWeight,
                textAlign = textAlign ?: TextAlign.Unspecified,
                fontFeatureSettings = if (tabular) TABULAR_FIGURES else null,
            )
        )
        merged.copy(color = color.takeOrElse { merged.color.takeOrElse { contentColor } })
    }
    // What the layout pass settled on, for the draw pass that follows it. Not state: nothing recomposes on it.
    val fit = remember { Fit() }

    Spacer(
        modifier
            .layout { measurable, constraints ->
                val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
                // The widest of the lines at a scale: the one the box is sized for.
                fun widest(scale: Float) = fitTo.map {
                    measurer.measure(it, style.copy(fontSize = fontSize * scale), TextOverflow.Clip, softWrap = false, maxLines = 1)
                }.maxBy { it.size.width }
                var scale = 1f
                var fitted = widest(scale)
                while (fitted.size.width > maxWidth && scale > minScale) {
                    scale = (scale - 0.1f).coerceAtLeast(minScale)
                    fitted = widest(scale)
                }
                fit.scale = scale
                fit.line = fitted
                val width = constraints.constrainWidth(fitted.size.width)
                val height = constraints.constrainHeight(fitted.size.height)
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .drawBehind {
                val width = size.width.roundToInt()
                val fitted = fit.line
                // The line the layout pass measured, when it is the text shown and fills the box exactly;
                // otherwise the text laid out across the box: aligned in it, ellipsized if it must be.
                val shown = if (fitted != null && text == fitted.layoutInput.text.text && fitted.size.width == width) fitted
                else measurer.measure(
                    text, style.copy(fontSize = fontSize * fit.scale), TextOverflow.Ellipsis,
                    softWrap = false, maxLines = 1, constraints = Constraints.fixedWidth(width),
                )
                drawText(shown)
            }
            .semantics { this.text = AnnotatedString(text) }
    )
}

private class Fit {
    var scale = 1f
    var line: TextLayoutResult? = null
}
