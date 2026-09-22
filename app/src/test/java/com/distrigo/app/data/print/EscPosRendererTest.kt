package com.distrigo.app.data.print

import com.distrigo.app.data.print.lang.EscPosRenderer
import com.distrigo.app.data.print.lang.MonoRaster
import com.distrigo.app.data.print.lang.PrinterCodePage
import com.distrigo.app.data.print.lang.ReceiptRow
import com.distrigo.app.data.print.lang.RowAlign
import com.distrigo.app.data.print.lang.RowWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ESC/POS byte stream, checked without a printer.
 *
 * These pin the parts that fail invisibly on paper: a code page selected too late, a formatting flag
 * left on so the rest of the receipt prints bold, and a raster header whose width and height are
 * swapped — which prints a band of noise rather than nothing, so it is not obviously a bug.
 */
class EscPosRendererTest {

    private val NEWLINE = Char(0x0A)

    private val paper = PaperProfile.MM58

    private fun render(vararg rows: ReceiptRow, codePage: PrinterCodePage = PrinterCodePage.CP1252) =
        EscPosRenderer.render(rows.toList(), paper, codePage)

    /** Finds [needle] in [this], or -1. */
    private fun ByteArray.indexOfBytes(vararg needle: Int): Int {
        val n = needle.map { it.toByte() }
        outer@ for (i in 0..size - n.size) {
            for (j in n.indices) if (this[i + j] != n[j]) continue@outer
            return i
        }
        return -1
    }

    @Test
    fun `the stream initialises and selects the code page before any text`() {
        val bytes = render(ReceiptRow.Line("Café"), codePage = PrinterCodePage.CP858)

        // ESC @ then ESC t 19.
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals('@'.code.toByte(), bytes[1])
        assertEquals(0x1B.toByte(), bytes[2])
        assertEquals('t'.code.toByte(), bytes[3])
        assertEquals(19.toByte(), bytes[4])

        // INIT resets the printer to its factory page, so selecting ours afterwards is the only order
        // that works — and text must come after that, not between.
        val text = bytes.indexOfBytes('C'.code, 'a'.code, 'f'.code)
        assertTrue("text at $text should follow the code page at 2", text > 4)
    }

    @Test
    fun `bold and centring are turned back off after the line that used them`() {
        val bytes = render(ReceiptRow.Line("TITRE", RowAlign.Center, RowWeight.Bold))

        assertTrue("no bold on", bytes.indexOfBytes(0x1B, 'E'.code, 1) >= 0)
        assertTrue("no bold off", bytes.indexOfBytes(0x1B, 'E'.code, 0) >= 0)
        assertTrue(
            "bold must be cleared after the line, or the whole receipt prints bold",
            bytes.indexOfBytes(0x1B, 'E'.code, 0) > bytes.indexOfBytes(0x1B, 'E'.code, 1),
        )
        assertTrue(
            "alignment must return to left",
            bytes.indexOfBytes(0x1B, 'a'.code, 0) > bytes.indexOfBytes(0x1B, 'a'.code, 1),
        )
    }

    @Test
    fun `a rule spans exactly the paper width`() {
        val bytes = render(ReceiptRow.Rule('-'))
        val dashes = bytes.count { it == '-'.code.toByte() }
        assertEquals(paper.charsPerLine, dashes)
    }

    /**
     * The printed line that starts with [prefix], read from there to the newline that ends it.
     *
     * Measured this way rather than by stripping control bytes out of the whole stream: an ESC
     * sequence's *parameters* are ordinary printable ASCII — the '@' of `ESC @`, the 't' of `ESC t` —
     * so a filter on byte values leaves three stray characters on the first line and quietly reports a
     * 32-character line as 35.
     */
    private fun printedLineFrom(bytes: ByteArray, prefix: String): String {
        val start = bytes.indexOfBytes(*prefix.map { it.code }.toIntArray())
        assertTrue("\"$prefix\" never printed", start >= 0)
        var end = start
        while (end < bytes.size && bytes[end] != NEWLINE.code.toByte()) end++
        return String(bytes, start, end - start, charset("windows-1252"))
    }

    @Test
    fun `columns are padded to the paper width with the right-hand value flush right`() {
        val bytes = render(ReceiptRow.Columns("TOTAL", "15230.00 DA"))
        val line = printedLineFrom(bytes, "TOTAL")
        assertEquals(paper.charsPerLine, line.length)
        assertTrue("right value not flush right: \"$line\"", line.endsWith("15230.00 DA"))
    }

    @Test
    fun `a raster carries GS v 0 with width in bytes and height in dot rows`() {
        val raster = MonoRaster(width = 384, height = 2, bits = ByteArray(48 * 2))
        val bytes = render(ReceiptRow.Raster(raster))

        val at = bytes.indexOfBytes(0x1D, 'v'.code, '0'.code, 0)
        assertTrue("GS v 0 not emitted", at >= 0)
        // xL xH is the width in *bytes* (48), yL yH the height in dot rows (2). Swapping them is the
        // classic raster bug and prints a band of noise rather than failing outright.
        assertEquals(48.toByte(), bytes[at + 4])
        assertEquals(0.toByte(),  bytes[at + 5])
        assertEquals(2.toByte(),  bytes[at + 6])
        assertEquals(0.toByte(),  bytes[at + 7])
    }

    @Test
    fun `a tall raster is split into bands rather than sent as one image`() {
        // 400 rows is over the 128-row band, so it must come out as four GS v 0 calls: an image longer
        // than the printer's buffer is dropped whole, with nothing reported.
        val raster = MonoRaster(width = 384, height = 400, bits = ByteArray(48 * 400))
        val bytes = render(ReceiptRow.Raster(raster))

        var count = 0
        var from = 0
        while (true) {
            val at = bytes.copyOfRange(from, bytes.size).indexOfBytes(0x1D, 'v'.code, '0'.code, 0)
            if (at < 0) break
            count++
            from += at + 4
        }
        assertEquals(4, count)
    }

    @Test
    fun `the QR goes out as native commands, not as pixels`() {
        val bytes = render(ReceiptRow.Qr("https://example.test/v/27", 192))

        // Model, size, error-correction, store, print — the five GS ( k calls.
        assertTrue("model not set",  bytes.indexOfBytes(0x1D, '('.code, 'k'.code, 4, 0, 49, 65, 50, 0) >= 0)
        assertTrue("size not set",   bytes.indexOfBytes(0x1D, '('.code, 'k'.code, 3, 0, 49, 67) >= 0)
        assertTrue("ec not set",     bytes.indexOfBytes(0x1D, '('.code, 'k'.code, 3, 0, 49, 69, 50) >= 0)
        assertTrue("not printed",    bytes.indexOfBytes(0x1D, '('.code, 'k'.code, 3, 0, 49, 81, 48) >= 0)

        // The payload is carried literally, which is the whole saving over a raster.
        assertTrue("payload missing", String(bytes, Charsets.ISO_8859_1).contains("https://example.test/v/27"))
    }

    @Test
    fun `the stream ends with a cut`() {
        val bytes = render(ReceiptRow.Line("x"))
        val tail = bytes.takeLast(4).map { it.toInt() and 0xFF }
        assertEquals(listOf(0x1D, 'V'.code, 66, 4), tail)
    }

    @Test
    fun `the test strip prints rulers of exactly 32 and 48 characters`() {
        val rows = TestPrint.rows(PaperProfile.MM58, PrintLanguage.ESC_POS, PrinterCodePage.CP1252)
        val rulers = rows.filterIsInstance<ReceiptRow.Line>()
            .map { it.text }
            .filter { it.endsWith("|") && it.first().isDigit() }

        assertEquals("both rulers should be present", listOf(32, 48), rulers.map { it.length })
        // They must be emitted un-truncated even on 58 mm paper: the 48-character line wrapping onto a
        // second row is exactly the signal the user reads the width off.
        assertTrue(rulers.all { it.dropLast(1).all(Char::isDigit) })
    }
}
