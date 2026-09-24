package com.distrigo.app.data.print.lang

import com.distrigo.app.data.print.PaperProfile
import java.io.ByteArrayOutputStream

/**
 * A receipt's rows stacked into one page: where each band of dots goes, and how tall the page is.
 *
 * TSPL and CPCL are **page** languages — unlike ESC/POS, which streams, they must be told the page's
 * height before the first dot arrives (docs/print_architecture.md §4). The rows already know their own
 * heights, so the page is simply their sum, and each row's top is the sum of those above it.
 *
 * A page starts blank, which ESC/POS paper does not: an all-white band costs nothing here and is left
 * out, so the tear-off feed and the gaps between sections are paid for in height, not in bytes.
 */
internal class RasterPage(rows: List<MonoRaster>) {

    /** A band of at most [BAND_ROWS] dot rows, placed [y] dots from the top of the page. */
    class Band(val y: Int, val width: Int, val height: Int, val bits: ByteArray) {
        val bytesPerRow: Int get() = width / 8
    }

    /** The page's height in dots: every row, white ones included. */
    val height: Int = rows.sumOf { it.height }

    /** The bands that carry at least one burnt dot, top to bottom. */
    val bands: List<Band> = buildList {
        var top = 0
        for (row in rows) {
            var y = 0
            while (y < row.height) {
                val count = minOf(BAND_ROWS, row.height - y)
                val from = y * row.bytesPerRow
                val to = from + count * row.bytesPerRow
                if ((from until to).any { row.bits[it].toInt() != 0 }) {
                    add(Band(top + y, row.width, count, row.bits.copyOfRange(from, to)))
                }
                y += count
            }
            top += row.height
        }
    }

    companion object {
        /**
         * Dot rows per graphics command, as `GS v 0` sends them for ESC/POS. A single command the height
         * of a whole receipt is allowed by both grammars and refused by printers with small buffers.
         */
        const val BAND_ROWS = 128
    }
}

/**
 * The receipt for a **TSPL** printer — the TSC family.
 *
 * One label as long as the receipt, on continuous paper: `GAP 0,0` tells the printer there are no
 * gaps to find. A receipt's length changes with every sale, so it cannot be fitted to pre-cut labels,
 * and a printer loaded with them will simply run the receipt across several.
 */
object TsplRenderer {

    fun render(rows: List<MonoRaster>, paper: PaperProfile): ByteArray {
        val page = RasterPage(rows)
        val out = ByteArrayOutputStream()
        out.line("SIZE ${paper.printableMm} mm, ${mm(page.height)} mm")
        out.line("GAP 0 mm, 0 mm")
        out.line("DIRECTION 0")
        out.line("REFERENCE 0,0")
        out.line("CLS")
        page.bands.forEach { band ->
            out.ascii("BITMAP 0,${band.y},${band.bytesPerRow},${band.height},0,")
            out.write(inverted(band.bits))
            out.write(CRLF)
        }
        out.line("PRINT 1,1")
        return out.toByteArray()
    }

    /**
     * TSPL reads its bitmap the other way round from ESC/POS and CPCL: **a set bit is a white dot**.
     * The rasters are built burnt-is-one, which is `GS v 0`'s layout, so they are flipped here and
     * only here. Sent unflipped, the receipt would come out as a black negative of itself.
     */
    internal fun inverted(bits: ByteArray): ByteArray = ByteArray(bits.size) { (bits[it].toInt() xor 0xFF).toByte() }

    /** Dots to whole millimetres at 203 dpi, rounded up so the last row is never cut off. */
    private fun mm(dots: Int): Int = (dots + DOTS_PER_MM - 1) / DOTS_PER_MM
}

/**
 * The receipt for a **CPCL** printer — Zebra and Citizen mobile printers, the belt units reps carry.
 *
 * `! 0 200 200 <height> 1` opens a page of that many dots at 200 dpi (the grammar's own name for the
 * 203 dpi head). The dots go out as `CG` — COMPRESSED-GRAPHICS, which in CPCL means *binary*, not
 * compressed — because the hex form, `EG`, is twice the bytes over a link that is often the slowest in
 * the chain. A set bit is a black dot, as in ESC/POS, so the rasters go out as they are.
 */
object CpclRenderer {

    fun render(rows: List<MonoRaster>, paper: PaperProfile): ByteArray {
        val page = RasterPage(rows)
        val out = ByteArrayOutputStream()
        out.line("! 0 200 200 ${page.height} 1")
        out.line("PAGE-WIDTH ${paper.rasterWidthDots}")
        page.bands.forEach { band ->
            out.ascii("CG ${band.bytesPerRow} ${band.height} 0 ${band.y} ")
            out.write(band.bits)
            out.write(CRLF)
        }
        out.line("PRINT")
        return out.toByteArray()
    }
}

/** 203 dpi, rounded as every thermal grammar rounds it. */
private const val DOTS_PER_MM = 8

/** Both grammars end a command with CR LF, and both accept it where some accept LF alone. */
private val CRLF = byteArrayOf(0x0D, 0x0A)

private fun ByteArrayOutputStream.ascii(text: String) = write(text.toByteArray(Charsets.US_ASCII))

private fun ByteArrayOutputStream.line(text: String) {
    ascii(text)
    write(CRLF)
}
