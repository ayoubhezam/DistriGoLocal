package com.distrigo.app.ui.settings.print

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.data.print.PaperProfile
import com.distrigo.app.data.print.lang.MonoRaster
import com.distrigo.app.ui.designsystem.DsColors
import java.nio.ByteBuffer

/** Thermal paper is not white: a faint warm grey, which is also what keeps the card off the surface. */
private val PaperTint = Color(0xFFFDFCF8)

/** The widest roll we lay out for. Narrower paper is drawn to scale against it. */
private const val WIDEST_PAPER_MM = 80f

/**
 * Shows the receipt the printer will actually produce.
 *
 * Not an approximation of it — **the same bitmaps**. The renderer draws each row on a Canvas and
 * reduces it to one bit per dot; the printer gets those dots over `GS v 0` and this draws them on
 * screen. There is no second layout to drift, no monospace stand-in for a proportional font, and no
 * way for the screen to promise something the paper will not deliver.
 *
 * That is also the only honest way to preview Arabic: the shaping and the right-to-left order come
 * from `StaticLayout` inside the renderer, so anything drawn here independently would be a guess at
 * its output.
 *
 * Two things are to scale and worth the trouble:
 * - **Card width** follows the real roll width, so 58 mm is visibly narrower than 80 mm rather than
 *   just holding less text.
 * - **The inner margin** follows the gap between the roll width and the printable width — the head
 *   does not reach the paper's edges, and receipts from a real printer show that margin.
 */
@Composable
fun ThermalReceiptPreview(
    rows    : List<PreviewRow>,
    paper   : PaperProfile,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val available = maxWidth
        val cardWidth = available * (paper.paperMm / WIDEST_PAPER_MM)
        val margin    = cardWidth * ((paper.paperMm - paper.printableMm) / 2f / paper.paperMm)
        val contentWidth = cardWidth - margin * 2

        Box(modifier = Modifier.width(cardWidth).align(Alignment.TopCenter)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaperTint)
                    .padding(horizontal = margin, vertical = 10.dp)
                    .padding(bottom = TornEdgeHeight),
            ) {
                rows.forEach { row -> RasterRow(row, contentWidth) }
            }
            TornEdge(
                color = DsColors.SurfaceSunken,
                modifier = Modifier.fillMaxWidth().height(TornEdgeHeight).align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * One printed row, ready to draw: its dots as an image, and the dot size it was drawn at.
 *
 * Built by [toPreviewRows], off the main thread. The composable only draws it — turning a receipt's
 * dots into pixels used to happen here, in composition, for every row at once, and for a long
 * receipt that was tens of milliseconds and megabytes of pixels on the UI thread.
 */
@Immutable
class PreviewRow(val image: ImageBitmap, val widthDots: Int, val heightDots: Int)

/**
 * The rows as images, **one byte per dot**.
 *
 * `ALPHA_8` rather than ARGB: a dot is burnt or not, which an alpha mask says in a quarter of the
 * memory, and drawn in black over the paper colour it shows exactly what ARGB black-on-white did.
 * A 1,200-dot receipt on 80 mm is 0.7 MB of images this way, against 2.8 MB as ARGB plus as much
 * again in the pixel array it was built from.
 *
 * Call it off the main thread; it touches every dot.
 */
fun List<MonoRaster>.toPreviewRows(): List<PreviewRow> = map { it.toPreviewRow() }

private fun MonoRaster.toPreviewRow(): PreviewRow {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
    // The stride the bitmap actually has, which may be padded past the width.
    val stride = bitmap.rowBytes
    val mask = ByteArray(stride * height)
    for (y in 0 until height) {
        val from = y * bytesPerRow
        val to = y * stride
        for (byteX in 0 until bytesPerRow) {
            val byte = bits[from + byteX].toInt()
            if (byte == 0) continue
            for (bit in 0 until 8) {
                if ((byte shr (7 - bit)) and 1 == 1) mask[to + byteX * 8 + bit] = BURNT
            }
        }
    }
    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(mask))
    // Starts the upload to the GPU now, on this thread, rather than on the first frame that draws it.
    bitmap.prepareToDraw()
    return PreviewRow(bitmap.asImageBitmap(), width, height)
}

/** A fully opaque alpha value, as a byte. */
private const val BURNT: Byte = -1

/**
 * One printed row, at its own dot resolution scaled to the paper's width.
 *
 * `FilterQuality.None` on purpose: the dots are the information. Smoothing them would show a cleaner
 * receipt than the head can produce, which is the one thing this preview exists not to do.
 *
 * The tint is what gives an alpha mask its colour; without it the dots would take whatever colour
 * the paint happened to have.
 */
@Composable
private fun RasterRow(row: PreviewRow, width: Dp) {
    val height = width * (row.heightDots.toFloat() / row.widthDots.toFloat())
    Image(
        bitmap = row.image,
        contentDescription = null,
        modifier = Modifier.width(width).height(height),
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None,
        colorFilter = DotInk,
    )
}

private val DotInk = ColorFilter.tint(Color.Black)

private val TornEdgeHeight = 10.dp

/** The zigzag a roll tears along, painted in the surface colour so it bites into the card. */
@Composable
private fun TornEdge(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val toothWidth = size.height * 1.6f
        val teeth = (size.width / toothWidth).toInt().coerceAtLeast(1)
        val step = size.width / teeth
        val path = Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, 0f)
            for (i in 0 until teeth) {
                lineTo(i * step + step / 2f, size.height)
                lineTo((i + 1) * step, 0f)
            }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, color)
    }
}

/**
 * The A4 stand-in.
 *
 * A4 never reaches the thermal renderer: it keeps the existing `ReceiptPdfGenerator` and Android's
 * own print dialog, whose printer discovery negotiates the media size itself. Showing a page shape
 * and saying so is more honest than rendering a fake A4 receipt this module would not produce.
 */
@Composable
fun A4PreviewPlaceholder(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cardWidth = maxWidth * 0.62f
        Column(
            modifier = Modifier
                .width(cardWidth)
                .height(cardWidth * 297f / 210f)
                .align(Alignment.TopCenter)
                .background(PaperTint)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "A4",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = DsColors.TextTertiary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Le reçu A4 garde sa mise en page actuelle et passe par la boîte d'impression " +
                    "d'Android, qui choisit le format du papier avec l'imprimante.",
                fontSize = 12.sp,
                color = DsColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
            )
        }
    }
}
