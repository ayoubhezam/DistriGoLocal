package com.distrigo.app.data.print

import com.distrigo.app.data.print.lang.EscPosRenderer
import com.distrigo.app.data.print.lang.MonoRaster
import com.distrigo.app.data.print.lang.PrinterCodePage
import com.distrigo.app.data.print.lang.TestLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ESC/POS byte stream, checked without a printer.
 *
 * Two streams now. The receipt goes out as **rasters**, because the printer's own text mode cannot
 * shape or reorder Arabic; the self-test still goes out as **text**, because that is the only thing
 * that can tell a printer which ignores `GS v 0` from one that is switched off — both print nothing.
 *
 * These pin the parts that fail invisibly on paper: a raster header whose width and height are
 * swapped, which prints a band of noise rather than failing outright, and a code page selected after
 * the text it was meant to encode.
 */
class EscPosRendererTest {

    private val paper = PaperProfile.MM58

    /** Finds [needle] in [this], or -1. */
    private fun ByteArray.indexOfBytes(vararg needle: Int): Int {
        val n = needle.map { it.toByte() }
        outer@ for (i in 0..size - n.size) {
            for (j in n.indices) if (this[i + j] != n[j]) continue@outer
            return i
        }
        return -1
    }

    private fun raster(height: Int) = MonoRaster(384, height, ByteArray(48 * height))

    // ───────────────────────────── the receipt ─────────────────────────────

    @Test
    fun `a raster stream initialises and cancels Kanji mode before anything else`() {
        val bytes = EscPosRenderer.renderRaster(listOf(raster(2)))

        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals('@'.code.toByte(), bytes[1])
        // Kanji mode has to be cancelled *after* init, which restores the factory state and would
        // re-enable it. It costs two bytes and protects anything that later prints text.
        assertEquals(0x1C.toByte(), bytes[2])
        assertEquals('.'.code.toByte(), bytes[3])
    }

    @Test
    fun `a raster carries GS v 0 with width in bytes and height in dot rows`() {
        val bytes = EscPosRenderer.renderRaster(listOf(raster(2)))

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
        val bytes = EscPosRenderer.renderRaster(listOf(raster(400)))

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
    fun `rows follow each other with no feed between them`() {
        // A row's height is its own spacing — the renderer drew the gap. A line feed here would open
        // one it never drew, and every row would sit further down the paper than the preview shows.
        val bytes = EscPosRenderer.renderRaster(listOf(raster(1), raster(1)))
        val first = bytes.indexOfBytes(0x1D, 'v'.code, '0'.code, 0)
        val secondOffset = bytes.copyOfRange(first + 4, bytes.size).indexOfBytes(0x1D, 'v'.code, '0'.code, 0)
        val between = bytes.copyOfRange(first + 8 + 48, first + 4 + secondOffset)
        assertTrue("a line feed sits between two rows", between.none { it == 0x0A.toByte() })
    }

    @Test
    fun `the stream ends with a cut`() {
        val bytes = EscPosRenderer.renderRaster(listOf(raster(1)))
        val tail = bytes.takeLast(4).map { it.toInt() and 0xFF }
        assertEquals(listOf(0x1D, 'V'.code, 66, 4), tail)
    }

    // ───────────────────────────── the self-test ─────────────────────────────

    @Test
    fun `the self-test selects code page, font and line spacing before any text`() {
        val bytes = EscPosRenderer.renderText(
            listOf(TestLine("Café")), paper, PrinterCodePage.CP858,
        )
        val text = bytes.indexOfBytes('C'.code, 'a'.code, 'f'.code)

        // ESC t 19 — CP858. Selected before the text it encodes, or the first line prints in whatever
        // page the previous app left behind.
        assertTrue("code page not selected", bytes.indexOfBytes(0x1B, 't'.code, 19) in 0 until text)
        // ESC M 0 — Font A, the width the ruler was measured against.
        assertTrue("font not selected", bytes.indexOfBytes(0x1B, 'M'.code, 0) in 0 until text)
        // ESC 3 27 — Font A's 24-dot glyphs plus three of gap.
        assertTrue("line spacing not set", bytes.indexOfBytes(0x1B, '3'.code, 27) in 0 until text)
    }

    @Test
    fun `the self-test turns bold and centring back off after the line that used them`() {
        val bytes = EscPosRenderer.renderText(
            listOf(TestLine("TITRE", bold = true, centered = true)), paper, PrinterCodePage.CP1252,
        )
        assertTrue(
            "bold must be cleared after the line, or the whole strip prints bold",
            bytes.indexOfBytes(0x1B, 'E'.code, 0) > bytes.indexOfBytes(0x1B, 'E'.code, 1),
        )
        assertTrue(
            "alignment must return to left",
            bytes.indexOfBytes(0x1B, 'a'.code, 0) > bytes.indexOfBytes(0x1B, 'a'.code, 1),
        )
    }

    @Test
    fun `the self-test prints rulers of exactly 32 and 48 characters`() {
        val rulers = TestPrint.lines(paper, PrintLanguage.ESC_POS, PrinterCodePage.CP1252)
            .map { it.text }
            .filter { it.endsWith("|") && it.first().isDigit() }

        assertEquals("both rulers should be present", listOf(32, 48), rulers.map { it.length })
        // They go out un-truncated even on 58 mm paper: the 48-character line wrapping onto a second
        // row is exactly the signal the user reads the width off.
        assertTrue(rulers.all { it.dropLast(1).all(Char::isDigit) })
    }
}
