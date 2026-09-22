package com.distrigo.app.data.print.lang

import com.distrigo.app.data.print.PaperProfile
import java.io.ByteArrayOutputStream

/**
 * Turns laid-out rows into ESC/POS bytes.
 *
 * ESC/POS is a streaming language: commands and text are interleaved and the printer acts on them as
 * they arrive, with no page and no total height to declare. That is why this renderer can emit row by
 * row, and why [PrintLanguage.isPageBased][com.distrigo.app.data.print.PrintLanguage.isPageBased]
 * exists — the label languages in phase 5 cannot.
 *
 * Nothing here decides *what* the receipt says. [ThermalLayout] already measured every line against
 * the paper, so this only attaches formatting and encodes.
 */
object EscPosRenderer {

    // ── The command vocabulary, named once so the call sites read as intent ──
    private const val ESC: Byte = 0x1B
    private const val GS : Byte = 0x1D
    private const val LF : Byte = 0x0A

    private val INIT          = byteArrayOf(ESC, '@'.code.toByte())
    private val ALIGN_LEFT    = byteArrayOf(ESC, 'a'.code.toByte(), 0)
    private val ALIGN_CENTER  = byteArrayOf(ESC, 'a'.code.toByte(), 1)
    private val ALIGN_RIGHT   = byteArrayOf(ESC, 'a'.code.toByte(), 2)
    private val BOLD_ON       = byteArrayOf(ESC, 'E'.code.toByte(), 1)
    private val BOLD_OFF      = byteArrayOf(ESC, 'E'.code.toByte(), 0)
    private val SIZE_NORMAL   = byteArrayOf(GS, '!'.code.toByte(), 0x00)
    private val SIZE_DOUBLE   = byteArrayOf(GS, '!'.code.toByte(), 0x11)
    /** Feed four lines and partial-cut. Ignored by the many mobile printers with no cutter. */
    private val CUT           = byteArrayOf(GS, 'V'.code.toByte(), 66, 0x04)

    /**
     * @param paper the profile the rows were laid out against. Used for the raster width and for
     *   padding [ReceiptRow.Columns], which ESC/POS has no native concept of.
     */
    fun render(
        rows    : List<ReceiptRow>,
        paper   : PaperProfile,
        codePage: PrinterCodePage,
    ): ByteArray {
        val out = ByteArrayOutputStream()

        out.write(INIT)
        // Select the code page before any text. A printer that was last used by another app may hold
        // any page at all, and INIT restores the factory one rather than the one we want.
        out.write(byteArrayOf(ESC, 't'.code.toByte(), codePage.escPosPage.toByte()))

        rows.forEach { row -> out.writeRow(row, paper, codePage) }

        out.write(CUT)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeRow(
        row     : ReceiptRow,
        paper   : PaperProfile,
        codePage: PrinterCodePage,
    ) {
        when (row) {
            is ReceiptRow.Line -> writeText(row.text, row.align, row.weight, row.scale, codePage)

            // Padded to the paper's width with spaces. ESC/POS has no tab stops worth relying on —
            // the column positions set by ESC D are honoured inconsistently across clones — and in a
            // monospace font spaces land in exactly the same place.
            is ReceiptRow.Columns -> {
                val room = paper.charsPerLine / if (row.scale == RowScale.Double) 2 else 1
                val gap  = (room - row.left.length - row.right.length).coerceAtLeast(1)
                writeText(row.left + " ".repeat(gap) + row.right, RowAlign.Left, row.weight, row.scale, codePage)
            }

            is ReceiptRow.Rule -> writeText(
                row.char.toString().repeat(paper.charsPerLine),
                RowAlign.Left, RowWeight.Normal, RowScale.Normal, codePage,
            )

            is ReceiptRow.Blank -> repeat(row.count) { write(LF.toInt()) }

            is ReceiptRow.Raster -> {
                write(row.align.command())
                writeRaster(row.raster)
                write(ALIGN_LEFT)
            }

            is ReceiptRow.Qr -> {
                write(ALIGN_CENTER)
                writeQr(row.payload, row.sizeDots)
                write(ALIGN_LEFT)
            }

            ReceiptRow.Cut -> Unit // Emitted once at the end of render(), not per row.
        }
    }

    private fun ByteArrayOutputStream.writeText(
        text    : String,
        align   : RowAlign,
        weight  : RowWeight,
        scale   : RowScale,
        codePage: PrinterCodePage,
    ) {
        write(align.command())
        if (weight == RowWeight.Bold) write(BOLD_ON)
        if (scale == RowScale.Double) write(SIZE_DOUBLE)

        write(ReceiptEncoding.encode(text, codePage))
        write(LF.toInt())

        if (scale == RowScale.Double) write(SIZE_NORMAL)
        if (weight == RowWeight.Bold) write(BOLD_OFF)
        if (align != RowAlign.Left) write(ALIGN_LEFT)
    }

    /**
     * `GS v 0` — raster bit image.
     *
     * The payload is exactly [MonoRaster.bits]: rows of bytes, 8 horizontal dots each, MSB leftmost,
     * a set bit meaning burnt. That is why the raster is built in this shape rather than converted here.
     *
     * Sent in horizontal bands rather than as one image. The command's height field allows 65535 dots,
     * but a printer's image buffer does not, and an over-long raster is dropped whole — no logo at
     * all, with nothing reported.
     */
    private fun ByteArrayOutputStream.writeRaster(raster: MonoRaster) {
        val bytesPerRow = raster.bytesPerRow
        var row = 0
        while (row < raster.height) {
            val band = minOf(RASTER_BAND_ROWS, raster.height - row)
            write(byteArrayOf(GS, 'v'.code.toByte(), '0'.code.toByte(), 0))
            write(byteArrayOf(
                (bytesPerRow and 0xFF).toByte(), ((bytesPerRow shr 8) and 0xFF).toByte(),
                (band and 0xFF).toByte(), ((band shr 8) and 0xFF).toByte(),
            ))
            write(raster.bits, row * bytesPerRow, band * bytesPerRow)
            row += band
        }
    }

    /**
     * `GS ( k` — the printer's own QR encoder.
     *
     * Preferred over rasterising a ZXing bitmap: the printer draws the modules on its own dot grid, so
     * the result is sharp at any size, and the payload is the URL's worth of bytes instead of tens of
     * kilobytes of image. The preview rasterises instead, because it has no printer to ask.
     */
    private fun ByteArrayOutputStream.writeQr(payload: String, sizeDots: Int) {
        val data = payload.toByteArray(Charsets.UTF_8)

        // Model 2, the one every implementation supports.
        write(byteArrayOf(GS, '('.code.toByte(), 'k'.code.toByte(), 4, 0, 49, 65, 50, 0))

        // Module size in dots. A version-4-ish payload is about 33 modules across, so this is the
        // multiplier that lands the code near the requested width, clamped to what the command allows.
        val moduleSize = (sizeDots / 33).coerceIn(1, 16)
        write(byteArrayOf(GS, '('.code.toByte(), 'k'.code.toByte(), 3, 0, 49, 67, moduleSize.toByte()))

        // Error correction M (49=L, 50=M, 51=Q, 52=H). M survives the smudging a thermal receipt picks
        // up in a pocket without costing the density that H would.
        write(byteArrayOf(GS, '('.code.toByte(), 'k'.code.toByte(), 3, 0, 49, 69, 50))

        // Store the payload. The length includes the three header bytes that follow pL/pH.
        val len = data.size + 3
        write(byteArrayOf(
            GS, '('.code.toByte(), 'k'.code.toByte(),
            (len and 0xFF).toByte(), ((len shr 8) and 0xFF).toByte(),
            49, 80, 48,
        ))
        write(data)

        // Print what was stored.
        write(byteArrayOf(GS, '('.code.toByte(), 'k'.code.toByte(), 3, 0, 49, 81, 48))
    }

    private fun RowAlign.command(): ByteArray = when (this) {
        RowAlign.Left   -> ALIGN_LEFT
        RowAlign.Center -> ALIGN_CENTER
        RowAlign.Right  -> ALIGN_RIGHT
    }

    /** Dot rows per `GS v 0` call. 128 is comfortably inside every buffer we have seen. */
    private const val RASTER_BAND_ROWS = 128
}
