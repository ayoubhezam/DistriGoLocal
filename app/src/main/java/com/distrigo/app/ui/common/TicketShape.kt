package com.distrigo.app.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsTextSize

class TicketShape(
    private val cornerRadius: Dp = 8.dp,
    private val notchRadius: Dp = 6.dp,
    private val notchSpacing: Dp = 20.dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = with(density) { cornerRadius.toPx() }
        val notchR = with(density) { notchRadius.toPx() }
        val spacing = with(density) { notchSpacing.toPx() }
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(corner, 0f)
            val topEnd = w - corner
            val notchCount = (((topEnd - corner) / spacing).toInt()).coerceAtLeast(1)
            val step = (topEnd - corner) / notchCount
            var x = corner
            repeat(notchCount) {
                val cx = x + step / 2f
                arcTo(Rect(cx - notchR, -notchR, cx + notchR, notchR), 180f, -180f, false)
                x += step
            }
            lineTo(topEnd, 0f)
            arcTo(Rect(w - 2 * corner, 0f, w, 2 * corner), -90f, 90f, false)
            lineTo(w, h - corner)
            arcTo(Rect(w - 2 * corner, h - 2 * corner, w, h), 0f, 90f, false)
            var bx = w - corner
            repeat(notchCount) {
                val cx = bx - step / 2f
                arcTo(Rect(cx - notchR, h - notchR, cx + notchR, h + notchR), 0f, -180f, false)
                bx -= step
            }
            lineTo(corner, h)
            arcTo(Rect(0f, h - 2 * corner, 2 * corner, h), 90f, 90f, false)
            lineTo(0f, corner)
            arcTo(Rect(0f, 0f, 2 * corner, 2 * corner), 180f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun PaidStamp(modifier: Modifier = Modifier, text: String = "PAYÉ") {
    Box(
        modifier = modifier
            .rotate(-3f)
            .border(1.5.dp, DsColors.Success, DsShapes.small)
            .clip(DsShapes.small)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = DsColors.Success,
            fontSize = DsTextSize.caption,
            fontWeight = FontWeight.Bold
        )
    }
}
