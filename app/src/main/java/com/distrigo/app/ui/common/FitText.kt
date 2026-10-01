package com.distrigo.app.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * The share of a row of stat columns an amount gets, against 1 for each count beside it: an amount to
 * the centime is three or four times as long as a count.
 */
const val MONEY_STAT_WEIGHT = 1.6f

/**
 * One line of text that shrinks until it fits its width — for an amount in a row of stat columns.
 *
 * Amounts are written in full, to the centime ("7 928 534 259,60 DA"), and a no-break space keeps
 * them whole; in a column a third of the screen wide, a large one wrapped in the middle of a group
 * and pushed its neighbours' labels together. This keeps it on one line instead, shrinking the font
 * by steps of a tenth down to [minScale] of [fontSize], and ellipsizing only past that.
 *
 * Drawn only once its size is settled, so the overflowing first measure never flashes. Compose's own
 * auto-size text arrived after the version this app uses.
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
) {
    var scale by remember(text, fontSize) { mutableStateOf(1f) }
    var settled by remember(text, fontSize) { mutableStateOf(false) }
    Text(
        text       = text,
        fontSize   = fontSize * scale,
        color      = color,
        fontWeight = fontWeight,
        textAlign  = textAlign,
        maxLines   = 1,
        softWrap   = false,
        overflow   = if (settled) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier   = modifier.drawWithContent { if (settled) drawContent() },
        onTextLayout = { layout ->
            if (settled) return@Text
            if (layout.didOverflowWidth && scale > minScale) scale = (scale - 0.1f).coerceAtLeast(minScale)
            else settled = true
        },
    )
}
