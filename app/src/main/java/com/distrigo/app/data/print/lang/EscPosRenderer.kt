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
    private const val FS : Byte = 0x1C
    private const val GS : Byte = 0x1D
    private const val LF : Byte = 0x0A

    private val INIT          = byteArrayOf(ESC, '@'.code.toByte())

    /**
     * `FS .` — cancel Kanji character mode.
     *
     * The fix for French on printers built for the Chinese market, which ship with multi-byte mode
     * *on*: a byte at or above 0x80 is read as the lead byte of a GBK pair, so `é` (0xE9 in CP1252)
     * swallows the character after it and the two print as one unrelated CJK glyph. That is why
     * accents came out as Chinese rather than as the wrong accent.
     *
     * It must follow `ESC @`, which restores the factory state and would re-enable the mode, and it
     * costs two bytes on printers that were never in it.
     */
    private val KANJI_OFF     = byteArrayOf(FS, '.'.code.toByte())
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
        out.write(KANJI_OFF)
        // Select the code page before any text. A printer that was last used by another app may hold
        // any page at all, and INIT restores the factory one rather than the one we want.
        out.write(byteArrayOf(ESC, 't'.code.toByte(), codePage.escPosPage.toByte()))
        // The font the layout was measured against. Get this wrong and every line is laid out for a
        // width the printer is not using — 42 characters of text squeezed into a 32-character Font A
        // line comes back wrapped, which looks like a layout bug and is not one.
        out.write(byteArrayOf(ESC, 'M'.code.toByte(), paper.font.escPosSelector.toByte()))
        // Line spacing, in dots. The factory default is sized for Font A and generous even for it;
        // against Font B's 17-dot glyphs it spends a third of the roll on white space.
        out.write(byteArrayOf(ESC, '3'.code.toByte(), paper.lineSpacingDots.toByte()))

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

    private fun RowAlign.command(): ByteArray = when (this) {
        RowAlign.Left   -> ALIGN_LEFT
        RowAlign.Center -> ALIGN_CENTER
        RowAlign.Right  -> ALIGN_RIGHT
    }

    /** Dot rows per `GS v 0` call. 128 is comfortably inside every buffer we have seen. */
    private const val RASTER_BAND_ROWS = 128
}
