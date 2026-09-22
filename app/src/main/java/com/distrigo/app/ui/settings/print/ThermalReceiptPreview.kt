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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    rasters : List<MonoRaster>,
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
                rasters.forEach { raster -> RasterRow(raster, contentWidth) }
            }
            TornEdge(
                color = DsColors.SurfaceSunken,
                modifier = Modifier.fillMaxWidth().height(TornEdgeHeight).align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * One printed row, at its own dot resolution scaled to the paper's width.
 *
 * `FilterQuality.None` on purpose: the dots are the information. Smoothing them would show a cleaner
 * receipt than the head can produce, which is the one thing this preview exists not to do.
 */
@Composable
private fun RasterRow(raster: MonoRaster, width: Dp) {
    val image: ImageBitmap = remember(raster) { raster.toImageBitmap() }
    val height = width * (raster.height.toFloat() / raster.width.toFloat())
    Image(
        bitmap = image,
        contentDescription = null,
        modifier = Modifier.width(width).height(height),
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None,
    )
}

/** One ARGB pixel per dot — black where the head burns, white where it does not. */
private fun MonoRaster.toImageBitmap(): ImageBitmap {
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            pixels[y * width + x] = if (isBlack(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

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
