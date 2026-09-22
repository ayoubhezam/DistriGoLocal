package com.distrigo.app.data.print

import android.graphics.BitmapFactory
import com.distrigo.app.data.print.lang.CanvasReceiptRenderer
import com.distrigo.app.data.print.lang.MonoRaster
import com.distrigo.app.data.print.lang.ThermalLayout
import com.distrigo.app.data.print.lang.ThermalRaster
import com.distrigo.app.ui.components.ReceiptData
import java.io.File

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
     * The logo scaled and dithered to [paper]'s dot width, or null when there is none to print.
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
        return runCatching {
            val bitmap = BitmapFactory.decodeFile(file.path) ?: return null
            // The full printable width. A mark that wants to be smaller is smaller in the source
            // image, which is the only place the user can control it from.
            ThermalRaster.fromBitmap(bitmap, paper.rasterWidthDots)
        }.getOrNull()
    }

    /** The receipt laid out, drawn and thresholded — one raster per row, in order. */
    fun rasters(receipt: ReceiptData, paper: PaperProfile): List<MonoRaster> {
        val rows = ThermalLayout.layout(receipt, paper, logo(receipt.businessLogoPath, paper))
        return CanvasReceiptRenderer(paper).render(rows)
    }
}
