package com.distrigo.app.data.print.lang

import com.distrigo.app.data.print.PaperProfile
import java.io.ByteArrayOutputStream

/**
 * Turns dots into ESC/POS bytes.
 *
 * ESC/POS is a streaming language: commands and data are interleaved and the printer acts on them as
 * they arrive, with no page and no total height to declare. That is why this can emit row by row.
 *
 * **It no longer renders the receipt's text.** The receipt is drawn on a Canvas and arrives here as
 * rasters, because the printer's own text mode cannot shape or reorder Arabic — see
 * [CanvasReceiptRenderer]. What remains of the text path is [renderText], used by the diagnostic
 * self-test and nothing else: it is the only thing that can still answer "does this printer speak
 * ESC/POS at all", which a raster cannot, since a printer that ignores `GS v 0` and a printer that
 * is switched off both print nothing.
 */
object EscPosRenderer {

    // ── The command vocabulary, named once so the call sites read as intent ──
    private const val ESC: Byte = 0x1B
    private const val FS : Byte = 0x1C
    private const val GS : Byte = 0x1D
    private const val LF : Byte = 0x0A

    private val INIT          = byteArrayOf(ESC, '@'.code.toByte())
    private val ALIGN_LEFT    = byteArrayOf(ESC, 'a'.code.toByte(), 0)
    private val ALIGN_CENTER  = byteArrayOf(ESC, 'a'.code.toByte(), 1)
    private val BOLD_ON       = byteArrayOf(ESC, 'E'.code.toByte(), 1)
    private val BOLD_OFF      = byteArrayOf(ESC, 'E'.code.toByte(), 0)

    /**
     * `FS .` — cancel Kanji character mode.
     *
     * The fix for French on printers built for the Chinese market, which ship with multi-byte mode
     * *on*: a byte at or above 0x80 is read as the lead byte of a GBK pair, so `é` (0xE9 in CP1252)
     * swallows the character after it and the two print as one unrelated CJK glyph. That is why
     * accents came out as Chinese rather than as the wrong accent.
     *
     * It must follow `ESC @`, which restores the factory state and would re-enable the mode. Only
     * [renderText] can still be bitten by it, but it costs two bytes and is emitted either way.
     */
    private val KANJI_OFF     = byteArrayOf(FS, '.'.code.toByte())

    /** Feed four lines and partial-cut. Ignored by the many mobile printers with no cutter. */
    private val CUT           = byteArrayOf(GS, 'V'.code.toByte(), 66, 0x04)

    /**
     * A receipt, as dots.
     *
     * Each raster is one row of the layout, already drawn and thresholded. They are emitted in order
     * with nothing between them: a row's height *is* its spacing, so a line feed between them would
     * open a gap the renderer never drew.
     */
    fun renderRaster(rows: List<MonoRaster>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(INIT)
        out.write(KANJI_OFF)
        out.write(ALIGN_LEFT)
        rows.forEach { out.writeRaster(it) }
        out.write(CUT)
        return out.toByteArray()
    }

    /**
     * Plain text in the printer's own font, for the self-test.
     *
     * Kept deliberately small. Its job is diagnosis — that the printer answers, speaks ESC/POS, holds
     * the code page it was told to and prints to the width it was told — not to lay out a document.
     */
    fun renderText(
        lines   : List<TestLine>,
        paper   : PaperProfile,
        codePage: PrinterCodePage,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(INIT)
        out.write(KANJI_OFF)
        out.write(byteArrayOf(ESC, 't'.code.toByte(), codePage.escPosPage.toByte()))
        out.write(byteArrayOf(ESC, 'M'.code.toByte(), paper.font.escPosSelector.toByte()))
        out.write(byteArrayOf(ESC, '3'.code.toByte(), paper.lineSpacingDots.toByte()))

        lines.forEach { line ->
            if (line.centered) out.write(ALIGN_CENTER)
            if (line.bold) out.write(BOLD_ON)
            out.write(ReceiptEncoding.encode(line.text, codePage))
            out.write(LF.toInt())
            if (line.bold) out.write(BOLD_OFF)
            if (line.centered) out.write(ALIGN_LEFT)
        }

        out.write(byteArrayOf(LF, LF))
        out.write(CUT)
        return out.toByteArray()
    }

    /**
     * `GS v 0` — raster bit image.
     *
     * The payload is exactly [MonoRaster.bits]: rows of bytes, 8 horizontal dots each, MSB leftmost,
     * a set bit meaning burnt. That is why the raster is built in this shape rather than converted here.
     *
     * Sent in horizontal bands rather than as one image. The command's height field allows 65535 dots,
     * but a printer's image buffer does not, and an over-long raster is dropped whole — no image at
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

    /** Dot rows per `GS v 0` call. 128 is comfortably inside every buffer we have seen. */
    private const val RASTER_BAND_ROWS = 128
}

/** One line of the diagnostic self-test, in the printer's own font. */
data class TestLine(val text: String, val bold: Boolean = false, val centered: Boolean = false)
