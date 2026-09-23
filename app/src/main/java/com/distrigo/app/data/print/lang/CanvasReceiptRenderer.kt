package com.distrigo.app.data.print.lang

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.distrigo.app.data.print.PaperProfile
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Draws a receipt row by row on an Android Canvas and hands back dots.
 *
 * **Why the printer's own text mode was abandoned.** ESC/POS has no bidirectional layout and no
 * contextual shaping: Arabic comes out unjoined, in isolated forms, running left to right —
 * illegible rather than merely ugly. The apparent escape, CP864, holds *presentation* forms, so
 * using it means doing the shaping and reordering yourself and mapping into those code points,
 * which is writing a text engine to avoid the one Android already ships. `StaticLayout` is that
 * engine — HarfBuzz for shaping, ICU for bidi — and it costs nothing to use.
 *
 * The cost is bytes: a receipt is ~1.5 KB as text and ~40 KB as dots. The benchmark settled whether
 * that matters — 40 KB went out in 4.0 s of which 3.5 s was our own pacing, the link ran at ~70 KB/s,
 * and the paper came out in one motion with no banding. See docs/print_architecture.md.
 *
 * **Row by row, not receipt by receipt.** Each row becomes its own small bitmap, which is emitted and
 * released before the next is drawn. A 200-item receipt as one image would be a 14 MB bitmap; this
 * way memory is bounded by the tallest single row whatever the receipt's length, and the rows stay
 * the unit that the preview also consumes.
 *
 * **Thresholded, not dithered.** Floyd–Steinberg is right for a photograph and wrong for text: it
 * speckles the edges of glyphs and makes small type look fuzzy. Text is drawn with anti-aliasing off
 * and reduced by a plain threshold, so the dots are exactly where the renderer put them. The logo
 * still arrives pre-dithered, by the other path.
 */
class CanvasReceiptRenderer(private val paper: PaperProfile) {

    private val width = paper.rasterWidthDots

    /**
     * Turns one row into dots, or null for a row that occupies no paper.
     *
     * [ReceiptRow.Cut] draws nothing — it is a command, emitted by the ESC/POS renderer rather than
     * printed — and a blank row is a height with nothing in it, which is still a raster because the
     * feed has to come from somewhere.
     */
    fun render(row: ReceiptRow): MonoRaster? = when (row) {
        ReceiptRow.Cut         -> null
        is ReceiptRow.Raster   -> row.raster
        is ReceiptRow.Blank    -> blank(row.dots)
        is ReceiptRow.Rule     -> rule(row.thickness)
        is ReceiptRow.Line     -> line(row)
        is ReceiptRow.Columns  -> columns(row)
        is ReceiptRow.Cells    -> cells(row)
    }

    /** Every row of a receipt, in order, skipping the ones that draw nothing. */
    fun render(rows: List<ReceiptRow>): List<MonoRaster> = rows.mapNotNull(::render)

    // ───────────────────────────── rows ─────────────────────────────

    private fun line(row: ReceiptRow.Line): MonoRaster {
        val paint = paintFor(row.weight, row.scale)
        val layout = layoutOf(row.text, paint, width, row.align)
        return draw(layout.height + LINE_GAP) { canvas -> canvas.drawLayout(layout, 0) }
    }

    /**
     * A label and a value at opposite edges.
     *
     * The right-hand run is measured first and given exactly what it needs; the left takes the rest.
     * That is what keeps a column of amounts flush while the labels beside them vary in length — a
     * proportional split would let "TOTAL" and "Payé" pull their figures out of line.
     */
    private fun columns(row: ReceiptRow.Columns): MonoRaster {
        val paint = paintFor(row.weight, row.scale)
        val rightWidth = ceil(paint.measureText(row.right)).toInt().coerceAtMost(width)
        val leftWidth = (width - rightWidth - GUTTER).coerceAtLeast(1)

        val left = layoutOf(row.left, paint, leftWidth, RowAlign.Start)
        val right = layoutOf(row.right, paint, rightWidth, RowAlign.End)

        return draw(maxOf(left.height, right.height) + LINE_GAP) { canvas ->
            canvas.drawLayout(left, 0)
            canvas.drawLayout(right, width - rightWidth)
        }
    }

    /**
     * Proportional cells, each wrapping inside its own width.
     *
     * The row is as tall as its tallest cell, which is how a long product name pushes its own row
     * down without disturbing the figures beside it.
     */
    private fun cells(row: ReceiptRow.Cells): MonoRaster {
        val paint = paintFor(row.weight, RowScale.Normal)
        var x = 0
        val placed = row.cells.mapIndexed { index, cell ->
            val last = index == row.cells.lastIndex
            val cellWidth = if (last) width - x else (width * cell.fraction).roundToInt()
            val textWidth = (cellWidth - GUTTER).coerceAtLeast(1)
            val layout = layoutOf(cell.text, paint, textWidth, cell.align)
            val at = x
            x += cellWidth
            layout to at
        }
        val height = placed.maxOfOrNull { it.first.height } ?: 0
        return draw(height + LINE_GAP) { canvas ->
            placed.forEach { (layout, at) -> canvas.drawLayout(layout, at) }
        }
    }

    private fun rule(thickness: RuleThickness): MonoRaster =
        draw(thickness.dots + RULE_MARGIN * 2) { canvas ->
            val paint = Paint().apply { color = Color.BLACK; isAntiAlias = false }
            canvas.drawRect(
                0f,
                RULE_MARGIN.toFloat(),
                width.toFloat(),
                (RULE_MARGIN + thickness.dots).toFloat(),
                paint,
            )
        }

    private fun blank(dots: Int): MonoRaster? =
        if (dots <= 0) null else MonoRaster(width, dots, ByteArray(width / 8 * dots))

    // ───────────────────────────── drawing ─────────────────────────────

    /**
     * Draws into a throwaway bitmap of [height] dots and reduces it to one bit per dot.
     *
     * The bitmap is recycled immediately. It is the only large allocation in the whole path, and
     * bounding it to a single row is what lets a receipt of any length print in constant memory.
     */
    private inline fun draw(height: Int, block: (Canvas) -> Unit): MonoRaster {
        val safeHeight = height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, safeHeight, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            block(canvas)
            bitmap.toMonoRaster()
        } finally {
            bitmap.recycle()
        }
    }

    private fun Canvas.drawLayout(layout: StaticLayout, x: Int) {
        save()
        translate(x.toFloat(), 0f)
        layout.draw(this)
        restore()
    }

    /**
     * `StaticLayout` wraps, shapes and reorders in one step.
     *
     * All three matter and only the last is obvious: it breaks lines at the right places for the
     * script, joins Arabic letters into their contextual forms, and puts a mixed line — an Arabic
     * product name beside a Latin number — in the order a reader expects. Hand-wrapping by character
     * count, which is what the previous renderer did, can do none of them.
     */
    private fun layoutOf(text: String, paint: TextPaint, width: Int, align: RowAlign): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(align.toLayoutAlignment())
            // The paragraph's embedding direction, which is what actually decides which edge
            // ALIGN_NORMAL means. Left over a strong-RTL string overrides the run's own direction for
            // *placement* only — HarfBuzz still shapes the Arabic and it still reads right to left
            // within itself, it simply starts at the left edge. Everything else keeps the default
            // first-strong rule, so a paragraph of Arabic still behaves like one.
            .setTextDirection(
                if (align == RowAlign.Left) TextDirectionHeuristics.LTR
                else TextDirectionHeuristics.FIRSTSTRONG_LTR
            )
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .build()

    private fun paintFor(weight: RowWeight, scale: RowScale) = TextPaint().apply {
        // Off on purpose. Anti-aliased edges become grey, and grey has nowhere to go on a one-bit
        // head: thresholded it thickens the glyph, dithered it speckles it. Crisp is what prints.
        isAntiAlias = false
        color = Color.BLACK
        textSize = if (scale == RowScale.Large) paper.bodyTextDots * LARGE_SCALE else paper.bodyTextDots.toFloat()
        typeface = if (weight == RowWeight.Bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /**
     * One bit per dot, MSB leftmost, a set bit meaning burnt — `GS v 0`'s own payload layout.
     *
     * A plain threshold rather than a dither, for the reason in the class docs. The comparison is on
     * the red channel alone because everything drawn here is greyscale.
     */
    private fun Bitmap.toMonoRaster(): MonoRaster {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        val bytesPerRow = width / 8
        val bits = ByteArray(bytesPerRow * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                if (((pixel shr 16) and 0xFF) < THRESHOLD) {
                    val i = y * bytesPerRow + (x shr 3)
                    bits[i] = (bits[i].toInt() or (1 shl (7 - (x and 7)))).toByte()
                }
            }
        }
        return MonoRaster(width, height, bits)
    }

    /**
     * NORMAL and OPPOSITE are relative to the paragraph's direction, not to the page.
     *
     * Which is why [RowAlign.Left] maps to NORMAL as well and does its work in `setTextDirection`:
     * the absolute `ALIGN_LEFT` exists in the platform but is hidden, so forcing the left edge means
     * forcing the direction that makes NORMAL mean left.
     */
    private fun RowAlign.toLayoutAlignment(): Layout.Alignment = when (this) {
        RowAlign.Start, RowAlign.Left -> Layout.Alignment.ALIGN_NORMAL
        RowAlign.Center               -> Layout.Alignment.ALIGN_CENTER
        RowAlign.End                  -> Layout.Alignment.ALIGN_OPPOSITE
    }

    private companion object {
        /** Below this on a 0–255 channel, a dot is burnt. */
        const val THRESHOLD = 128

        /** Breathing room under each text row, in dots. */
        const val LINE_GAP = 3

        /** White space either side of a rule. */
        const val RULE_MARGIN = 4

        /**
         * Space between adjacent cells, in dots.
         *
         * Wider than it looks necessary in a Latin-only table, because a right-to-left cell meets its
         * neighbour in the middle rather than at one edge: an Arabic product name is flush *right*
         * against a left-aligned quantity, so the two run together at a gap that would be ample
         * between two left-aligned columns.
         */
        const val GUTTER = 12

        /** How much larger [RowScale.Large] draws. */
        const val LARGE_SCALE = 1.35f
    }
}
