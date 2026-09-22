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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.distrigo.app.data.print.PaperProfile
import com.distrigo.app.data.print.lang.MonoRaster
import com.distrigo.app.data.print.lang.ReceiptRow
import com.distrigo.app.data.print.lang.RowAlign
import com.distrigo.app.data.print.lang.RowScale
import com.distrigo.app.data.print.lang.RowWeight
import com.distrigo.app.data.print.lang.ThermalRaster
import com.distrigo.app.ui.components.QrCodeGenerator
import com.distrigo.app.ui.designsystem.DsColors

/** Thermal paper is not white: a faint warm grey, which is also what keeps the card off the surface. */
private val PaperTint = Color(0xFFFDFCF8)
private val PaperInk  = Color(0xFF1A1A1A)

/** The widest roll we lay out for. Narrower paper is drawn to scale against it. */
private const val WIDEST_PAPER_MM = 80f

/**
 * Draws a laid-out receipt the way the paper will carry it.
 *
 * Not a mockup. It renders the very [ReceiptRow] list that the ESC/POS renderer will emit, in a
 * monospace face sized so that exactly [PaperProfile.charsPerLine] characters span the printable
 * width — so a product name that will be cut on 58 mm is cut here, and the logo is the dithered
 * 1-bit logo rather than the smooth original.
 *
 * Two things are to scale and worth the trouble:
 * - **Card width** follows the real roll width, so 58 mm is visibly narrower than 80 mm rather than
 *   just holding less text.
 * - **The inner margin** follows the gap between the roll width and the printable width — the head
 *   does not reach the paper's edges, and receipts from a real printer show that margin.
 */
@Composable
fun ThermalReceiptPreview(
    rows    : List<ReceiptRow>,
    paper   : PaperProfile,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val available = maxWidth
        val cardWidth = available * (paper.paperMm / WIDEST_PAPER_MM)
        val margin    = cardWidth * ((paper.paperMm - paper.printableMm) / 2f / paper.paperMm)
        val contentWidth = cardWidth - margin * 2

        val density  = LocalDensity.current
        val measurer = rememberTextMeasurer()

        // The exact size at which charsPerLine monospace characters span contentWidth. Measured rather
        // than guessed from an advance ratio: the ratio differs between the platform monospace faces,
        // and a preview whose line width is 5% off is a preview that lies about what fits.
        //
        // The probe and the rows below must be measured and drawn in the *same* style, which is why
        // monoStyle() exists rather than a bare TextStyle here. Material3's Text otherwise inherits
        // LocalTextStyle, whose 0.5sp letterSpacing added about 2% across 48 characters — enough to
        // push the last column past the edge and have `softWrap = false` quietly clip it, so the
        // preview showed "Tota" and "2280.0" where the paper would carry "Total" and "2280.00".
        val fontSize: TextUnit = remember(contentWidth, paper.charsPerLine, density) {
            val base = 12.sp
            val probe = measurer.measure(
                text  = AnnotatedString("0".repeat(paper.charsPerLine)),
                style = monoStyle(base, base, FontWeight.Normal),
            )
            val targetPx = with(density) { contentWidth.toPx() }
            // The hair off the end absorbs the pixel rounding between measuring and laying out. A
            // preview a third of a character narrow is invisible; one a third wide loses a digit.
            if (probe.size.width <= 0) base else base * (targetPx / probe.size.width) * 0.997f
        }
        val lineHeight = fontSize * 1.18f

        Box(modifier = Modifier.width(cardWidth).align(Alignment.TopCenter)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaperTint)
                    .padding(horizontal = margin, vertical = 14.dp)
                    .padding(bottom = TornEdgeHeight),
            ) {
                rows.forEach { row ->
                    RenderRow(row, paper, fontSize, lineHeight, contentWidth)
                }
            }
            TornEdge(
                color = DsColors.SurfaceSunken,
                modifier = Modifier.fillMaxWidth().height(TornEdgeHeight).align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun RenderRow(
    row         : ReceiptRow,
    paper       : PaperProfile,
    fontSize    : TextUnit,
    lineHeight  : TextUnit,
    contentWidth: Dp,
) {
    when (row) {
        is ReceiptRow.Line -> MonoLine(
            text = row.text, align = row.align, weight = row.weight, scale = row.scale,
            fontSize = fontSize, lineHeight = lineHeight,
        )

        // Padded out to the full width here rather than in the layout, because the label languages
        // position the right-hand value by dots. In monospace the result is identical.
        is ReceiptRow.Columns -> {
            val room = paper.charsPerLine / if (row.scale == RowScale.Double) 2 else 1
            val gap  = (room - row.left.length - row.right.length).coerceAtLeast(1)
            MonoLine(
                text = row.left + " ".repeat(gap) + row.right,
                align = RowAlign.Left, weight = row.weight, scale = row.scale,
                fontSize = fontSize, lineHeight = lineHeight,
            )
        }

        is ReceiptRow.Rule -> MonoLine(
            text = row.char.toString().repeat(paper.charsPerLine),
            align = RowAlign.Left, weight = RowWeight.Normal, scale = RowScale.Normal,
            fontSize = fontSize, lineHeight = lineHeight,
        )

        is ReceiptRow.Blank -> Spacer(Modifier.height(with(LocalDensity.current) {
            (lineHeight.toPx() * row.count).toDp()
        }))

        is ReceiptRow.Raster -> RasterImage(row.raster, row.align, contentWidth)

        // Rasterised through the same path the printer's fallback would use, so a QR that comes out too
        // dense to scan looks too dense here too.
        is ReceiptRow.Qr -> {
            val raster = remember(row.payload, row.sizeDots) {
                runCatching {
                    ThermalRaster.fromBitmap(QrCodeGenerator.generate(row.payload, row.sizeDots), row.sizeDots)
                }.getOrNull()
            }
            raster?.let { RasterImage(it, RowAlign.Center, contentWidth * 0.5f) }
        }

        // The tear-off line. Shown because the roll really does advance and cut here, and the gap tells
        // the user how much paper each receipt costs them.
        ReceiptRow.Cut -> Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(with(LocalDensity.current) { lineHeight.toPx().toDp() }))
            MonoLine(
                text = "- ".repeat(paper.charsPerLine / 2),
                align = RowAlign.Center, weight = RowWeight.Normal, scale = RowScale.Normal,
                fontSize = fontSize, lineHeight = lineHeight, color = PaperInk.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun MonoLine(
    text      : String,
    align     : RowAlign,
    weight    : RowWeight,
    scale     : RowScale,
    fontSize  : TextUnit,
    lineHeight: TextUnit,
    color     : Color = PaperInk,
) {
    // Double scale is ESC/POS GS ! double-width-and-height: twice the glyph, half the characters per
    // line. Doubling the font size here reproduces both halves of that.
    val factor = if (scale == RowScale.Double) 2f else 1f
    Text(
        text  = text,
        style = monoStyle(
            fontSize   = fontSize * factor,
            lineHeight = lineHeight * factor,
            weight     = if (weight == RowWeight.Bold) FontWeight.Bold else FontWeight.Normal,
        ).copy(
            color     = color,
            textAlign = when (align) {
                RowAlign.Left   -> TextAlign.Start
                RowAlign.Center -> TextAlign.Center
                RowAlign.Right  -> TextAlign.End
            },
        ),
        softWrap = false,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The one style every measured and every drawn line uses.
 *
 * [letterSpacing] is pinned to zero rather than left to the ambient text style: the whole preview
 * rests on charsPerLine characters spanning the printable width exactly, and a themed 0.5sp would
 * make the drawn line wider than the measured one.
 */
private fun monoStyle(fontSize: TextUnit, lineHeight: TextUnit, weight: FontWeight) = TextStyle(
    fontFamily    = FontFamily.Monospace,
    fontSize      = fontSize,
    lineHeight    = lineHeight,
    fontWeight    = weight,
    letterSpacing = 0.sp,
)

/**
 * Draws a [MonoRaster] at its own dot resolution, scaled to [maxWidth] without smoothing.
 *
 * `filterQuality` is left at the default `Low` and the bitmap is deliberately not anti-aliased on the
 * way in: the dither pattern *is* the information, and a smoothed preview would show a cleaner logo
 * than the printer can produce.
 */
@Composable
private fun RasterImage(raster: MonoRaster, align: RowAlign, maxWidth: Dp) {
    val image: ImageBitmap = remember(raster) { raster.toImageBitmap() }
    val height = maxWidth * (raster.height.toFloat() / raster.width.toFloat())
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = when (align) {
            RowAlign.Left   -> Alignment.CenterStart
            RowAlign.Center -> Alignment.Center
            RowAlign.Right  -> Alignment.CenterEnd
        },
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.width(maxWidth).height(height),
        )
    }
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
 * A4 never reaches the thermal layout: it keeps the existing `ReceiptPdfGenerator` and Android's own
 * print dialog, whose printer discovery negotiates the media size itself. Showing a page shape and
 * saying so is more honest than rendering a fake A4 receipt this module would not produce.
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
