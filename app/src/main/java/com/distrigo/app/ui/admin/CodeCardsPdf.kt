package com.distrigo.app.ui.admin

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.distrigo.app.R
import com.distrigo.app.data.license.NewCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * A new batch as cards to print and cut (docs/activation_codes.md, step C4): ten to an A4 page, two across and
 * five down, each about 93 × 52 mm, with a dashed line to cut along. A card says what it is, its code, what the
 * code gives and until when, and how to use it.
 */
internal object CodeCardsPdf {

    // A4 in points (1/72 inch), as PdfDocument draws
    private const val PAGE_W = 595f
    private const val PAGE_H = 842f
    private const val MARGIN = 28f
    private const val GAP = 14f
    const val COLUMNS = 2
    const val ROWS = 5
    const val PER_PAGE = COLUMNS * ROWS
    val CARD_W = (PAGE_W - 2 * MARGIN - (COLUMNS - 1) * GAP) / COLUMNS
    val CARD_H = (PAGE_H - 2 * MARGIN - (ROWS - 1) * GAP) / ROWS

    // DsBrand, for print, as ARGB (no android.graphics here, so the layout is testable on the JVM); the deep
    // orange reads better on paper
    private const val INK = 0xFF0F2D6B.toInt()
    private const val BLUE = 0xFF1E6BE5.toInt()
    private const val SLATE = 0xFF5A6F94.toInt()
    private const val MIST = 0xFFF6F8FC.toInt()
    private const val CUT = 0xFFB8C4D9.toInt()
    private const val ORANGE = 0xFFEE6A04.toInt()

    /** How many pages [count] cards take. */
    fun pages(count: Int): Int = (count + PER_PAGE - 1) / PER_PAGE

    /** Where the [index]th card of a page starts: its left and top, in points. */
    fun cardOrigin(index: Int): Pair<Float, Float> {
        val column = index % COLUMNS
        val row = (index / COLUMNS) % ROWS
        return (MARGIN + column * (CARD_W + GAP)) to (MARGIN + row * (CARD_H + GAP))
    }

    /** Draws the cards into `cache/codes/`, the last file only: it holds live codes. File work: off the main thread. */
    suspend fun write(context: Context, batch: CreatedBatch): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "codes").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val file = File(dir, "codes-distrigo-${slug(batch.order.batch)}.pdf")
        val document = PdfDocument()
        try {
            val paints = Paints(context)
            val chunks = batch.codes.chunked(PER_PAGE)
            chunks.forEachIndexed { pageIndex, codes ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageIndex + 1).create())
                codes.forEachIndexed { i, code ->
                    val (left, top) = cardOrigin(i)
                    drawCard(page.canvas, left, top, code, batch, paints)
                }
                centered(page.canvas, "Lot « ${batch.order.batch} » · page ${pageIndex + 1}/${chunks.size}", PAGE_W / 2, PAGE_H - 12f, paints.footer)
                document.finishPage(page)
            }
            FileOutputStream(file).use(document::writeTo)
        } finally {
            document.close()
        }
        file
    }

    private fun drawCard(canvas: Canvas, x: Float, y: Float, code: NewCode, batch: CreatedBatch, p: Paints) {
        val card = RectF(x, y, x + CARD_W, y + CARD_H)
        canvas.drawRoundRect(card, 10f, 10f, p.cut)
        val pad = 14f
        val center = x + CARD_W / 2

        // « DistriGo », the logo's two colours, and what the card is
        val brandBase = y + pad + 12f
        canvas.drawText("Distri", x + pad, brandBase, p.brandInk)
        canvas.drawText("Go", x + pad + p.brandInk.measureText("Distri"), brandBase, p.brandOrange)
        canvas.drawText("CODE D'ACTIVATION", x + CARD_W - pad, brandBase - 1f, p.label)

        canvas.drawText(code.code, center, y + 64f, p.code)
        centered(canvas, "DistriGo Solo · ${duration(batch.order.days)}", center, y + 84f, p.offer)
        centered(canvas, code.expiresOn?.let { "À activer avant le ${day(it)}" } ?: "Sans date limite", center, y + 97f, p.small)
        batch.order.businessName?.trim()?.takeIf { it.isNotEmpty() }?.let {
            centered(canvas, "Pour : $it", center, y + 109f, p.smallInk)
        }

        // How to use it, in a band at the bottom
        val band = RectF(x + 8f, y + CARD_H - 30f, x + CARD_W - 8f, y + CARD_H - 8f)
        canvas.drawRoundRect(band, 6f, 6f, p.band)
        centered(canvas, "Ouvrez DistriGo → « Activer avec un code »", center, band.centerY() + 2.7f, p.instruction)
    }

    /**
     * [text] centred on [x], its words placed one by one. Into a PDF, the variable font's lighter weights give the
     * space almost no width (« À activeravant le14octobre2027 »), so the space here is a third of the size.
     */
    private fun centered(canvas: Canvas, text: String, x: Float, baseline: Float, paint: Paint) {
        val words = text.split(' ')
        val space = paint.textSize / 3
        val widths = words.map { paint.measureText(it) }
        var left = x - (widths.sum() + space * (words.size - 1)) / 2
        val align = paint.textAlign
        paint.textAlign = Paint.Align.LEFT
        words.forEachIndexed { i, word ->
            canvas.drawText(word, left, baseline, paint)
            left += widths[i] + space
        }
        paint.textAlign = align
    }

    /** Plus Jakarta Sans at each weight the cards use; the system font if it can't be read. */
    private class Paints(context: Context) {
        private val font = runCatching { ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) }.getOrNull() ?: Typeface.DEFAULT

        private fun weight(w: Int): Typeface =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(font, w, false)
            else Typeface.create(font, if (w >= 600) Typeface.BOLD else Typeface.NORMAL)

        private fun text(size: Float, w: Int, color: Int, align: Paint.Align = Paint.Align.CENTER, spacing: Float = 0f) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                typeface = weight(w)
                this.color = color
                textAlign = align
                letterSpacing = spacing
            }

        val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            color = CUT
            pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
        }
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MIST }
        val brandInk = text(14f, 800, INK, Paint.Align.LEFT)
        val brandOrange = text(14f, 800, ORANGE, Paint.Align.LEFT)
        val label = text(6.5f, 700, SLATE, Paint.Align.RIGHT, spacing = 0.08f)
        val code = text(19f, 800, INK, spacing = 0.06f)
        val offer = text(9f, 700, INK)
        val small = text(7.5f, 500, SLATE)
        val smallInk = text(7.5f, 700, INK)
        val instruction = text(7.5f, 600, BLUE)
        val footer = text(7f, 500, SLATE)
    }

    /** A file name from the batch's: letters and digits, the rest as dashes. */
    private fun slug(name: String): String =
        name.lowercase().map { if (it.isLetterOrDigit() && it.code < 128) it else '-' }.joinToString("")
            .replace(Regex("-+"), "-").trim('-').take(40).ifEmpty { "lot" }
}

/** The cards, to WhatsApp, Drive, a printer: Android's share sheet. */
internal fun shareCodesPdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Cartes des codes (PDF)"))
}
