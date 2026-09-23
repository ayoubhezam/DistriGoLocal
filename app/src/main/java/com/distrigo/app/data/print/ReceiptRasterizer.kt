package com.distrigo.app.data.print

import android.graphics.BitmapFactory
import com.distrigo.app.data.print.lang.CanvasReceiptRenderer
import com.distrigo.app.data.print.lang.MonoRaster
import com.distrigo.app.data.print.lang.ThermalLayout
import com.distrigo.app.data.print.lang.ThermalRaster
import com.distrigo.app.ui.components.ReceiptData
import java.io.File

/**
 * A receipt already drawn for one paper: what the preview shows, kept so that printing it does not
 * draw it a second time.
 *
 * [receipt] and [paper] are the key. A print for a different receipt, or on a printer whose paper is
 * not the one this was drawn for, cannot use it — see [ReceiptRasterizer.rastersFor].
 */
data class DrawnReceipt(
    val receipt: ReceiptData,
    val paper  : PaperProfile,
    val rasters: List<MonoRaster>,
)

/**
 * A receipt, all the way to dots.
 *
 * The one place that turns a [ReceiptData] into the rasters that go to the printer — and the same
 * ones the preview draws. That is the whole architecture in one function: there is no second path
 * that could lay the receipt out differently, so the screen cannot promise what the paper will not
 * deliver.
 */
object ReceiptRasterizer {

    /**
     * The tallest a logo is printed, in millimetres.
     *
     * A logo used to take the full printable width and whatever height followed from it, so a square
     * mark cost 72 mm of 80 mm paper — on every receipt, before the first line of text. Twenty
     * millimetres is a clearly legible mark and about the size a printed receipt's logo usually is.
     */
    const val LOGO_MAX_HEIGHT_MM = 20

    /** 203 dpi is 8 dots per millimetre. */
    private const val DOTS_PER_MM = 8

    /**
     * Logos already dithered, by file and width.
     *
     * Decoding and dithering is the most expensive thing a receipt does, and its input almost never
     * changes: the same logo, on one or two paper widths. Two entries hold both widths; each is a few
     * kilobytes. The file's size and date are part of the key so a replaced logo is never served stale,
     * though the ImageStore names files by their contents and so never rewrites one in place.
     */
    private val logoCache = object : LinkedHashMap<LogoKey, MonoRaster>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LogoKey, MonoRaster>) = size > 2
    }

    private data class LogoKey(val path: String, val length: Long, val modified: Long, val width: Int)

    /**
     * The logo scaled, dithered and centred on [paper]'s dot width, or null when there is none to print.
     *
     * Null covers three cases that all end the same way — no logo on the paper: the business never
     * set one, the file is missing (a database restored without its photos keeps the reference and
     * loses the image), or the image cannot be decoded.
     *
     * Dithered, unlike the text around it. Floyd–Steinberg is right for a photographic mark and wrong
     * for glyph edges, which is why the logo and the receipt reach [MonoRaster] by different routes.
     *
     * 384 dots and 576 dots are different images rather than one image at two sizes, so the caller
     * must re-ask when the paper changes.
     */
    fun logo(path: String?, paper: PaperProfile): MonoRaster? {
        val file = path?.let(::File)?.takeIf { it.isFile } ?: return null
        val key = LogoKey(file.path, file.length(), file.lastModified(), paper.rasterWidthDots)
        synchronized(logoCache) { logoCache[key] }?.let { return it }

        // Outside the lock: two screens asking at once may both decode, which costs a little work
        // once, where holding the lock would make one of them wait for the other's decode.
        val logo = runCatching { decodeLogo(file, paper.rasterWidthDots) }.getOrNull() ?: return null
        synchronized(logoCache) { logoCache[key] = logo }
        return logo
    }

    /**
     * Decodes at no more than the width the logo will be printed at, then dithers.
     *
     * A 1024-pixel logo printed 160 dots wide does not need 1024 pixels decoded: the decoder skips
     * whole powers of two while the result is still at least the printed width, which is where most of
     * the memory went — four bytes a pixel for the bitmap, and four more for its pixels.
     */
    private fun decodeLogo(file: File, paperWidth: Int): MonoRaster? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val maxHeight = LOGO_MAX_HEIGHT_MM * DOTS_PER_MM
        val printedWidth = ThermalRaster.logoWidthFor(bounds.outWidth, bounds.outHeight, paperWidth, maxHeight)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= printedWidth) sample *= 2

        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return try {
            ThermalRaster.logoFromBitmap(bitmap, paperWidth, maxHeight)
        } finally {
            bitmap.recycle()
        }
    }

    /** The receipt laid out, drawn and thresholded — one raster per row, in order. */
    fun rasters(receipt: ReceiptData, paper: PaperProfile): List<MonoRaster> {
        val rows = ThermalLayout.layout(receipt, paper, logo(receipt.businessLogoPath, paper))
        return CanvasReceiptRenderer(paper).render(rows)
    }

    /** [rasters], kept with what it was drawn from. */
    fun draw(receipt: ReceiptData, paper: PaperProfile): DrawnReceipt =
        DrawnReceipt(receipt, paper, rasters(receipt, paper))

    /**
     * [drawn]'s rasters when they are this receipt on this paper, otherwise the receipt drawn now.
     *
     * The check is what makes reuse safe: the printer's paper is read when the print starts, and a
     * preview drawn a minute earlier for another printer, or for a receipt that has since changed, is
     * simply not used.
     */
    fun rastersFor(receipt: ReceiptData, paper: PaperProfile, drawn: DrawnReceipt?): List<MonoRaster> =
        drawn?.takeIf { it.receipt == receipt && it.paper == paper }?.rasters ?: rasters(receipt, paper)
}
