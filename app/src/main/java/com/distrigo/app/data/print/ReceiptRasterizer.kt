package com.distrigo.app.data.print

import android.graphics.BitmapFactory
import com.distrigo.app.data.print.lang.MonoRaster
import com.distrigo.app.data.print.lang.ReceiptRow
import com.distrigo.app.data.print.lang.ThermalLayout
import com.distrigo.app.data.print.lang.ThermalRaster
import com.distrigo.app.ui.components.ReceiptData
import java.io.File

/**
 * A receipt, laid out and with its logo turned into dots.
 *
 * The one place that decodes the business logo for a thermal printer. The settings preview and the
 * print button both need exactly this, and a receipt whose logo is dithered by two different callers
 * is a receipt that eventually looks different on screen from on paper — which is the whole thing
 * [ThermalLayout] exists to prevent.
 */
object ReceiptRasterizer {

    /**
     * The logo scaled and dithered to [paper]'s dot width, or null when there is none to print.
     *
     * Null covers three cases that all end the same way — no logo on the paper, which is what the A4
     * renderer already does in the same situation: the business never set one, the file is missing
     * (a database restored without its photos keeps the reference and loses the image), or the image
     * cannot be decoded.
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

    /** [ThermalLayout.layout] with the logo already rasterised. */
    fun rows(receipt: ReceiptData, paper: PaperProfile): List<ReceiptRow> =
        ThermalLayout.layout(receipt, paper, logo(receipt.businessLogoPath, paper))
}
