package com.distrigo.app.data.print

import com.distrigo.app.data.print.lang.CpclRenderer
import com.distrigo.app.data.print.lang.MonoRaster
import com.distrigo.app.data.print.lang.TsplRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two page languages, checked without a printer.
 *
 * What fails invisibly on paper is what these pin: a page declared shorter than the dots sent to it
 * (the bottom of the receipt is cut off, silently), a band placed at the wrong height (rows overprint
 * each other), and TSPL's inverted bit sense (the receipt comes out as a black negative).
 */
class PageRenderersTest {

    private val paper = PaperProfile.MM58

    private fun white(height: Int) = MonoRaster(384, height, ByteArray(48 * height))
    private fun black(height: Int) = MonoRaster(384, height, ByteArray(48 * height) { -1 })

    /** The ASCII command lines of a stream, with any binary payload cut off at the first non-text byte. */
    private fun ByteArray.commandLines(): List<String> =
        String(this, Charsets.ISO_8859_1).split("\r\n").map { line -> line.takeWhile { it.code in 0x20..0x7E } }

    // ───────────────────────────── TSPL ─────────────────────────────

    @Test
    fun `TSPL declares the page from the rows it carries, white ones included`() {
        // 10 + 120 + 30 = 160 dots = 20 mm at 8 dots/mm.
        val lines = TsplRenderer.render(listOf(black(10), white(120), black(30)), paper).commandLines()
        assertEquals("SIZE 48 mm, 20 mm", lines[0])
        assertEquals("GAP 0 mm, 0 mm", lines[1])
        assertEquals("CLS", lines[4])
        assertEquals("PRINT 1,1", lines.last { it.isNotEmpty() })
    }

    @Test
    fun `TSPL rounds the page up, so the last dot row is never cut off`() {
        val lines = TsplRenderer.render(listOf(black(9)), paper).commandLines()
        assertEquals("SIZE 48 mm, 2 mm", lines[0])
    }

    @Test
    fun `TSPL places each band at its row's height and skips white ones`() {
        val bitmaps = TsplRenderer.render(listOf(black(10), white(120), black(30)), paper)
            .commandLines().filter { it.startsWith("BITMAP") }
        // The white row costs nothing: the next band starts where it ends.
        assertEquals(listOf("BITMAP 0,0,48,10,0,", "BITMAP 0,130,48,30,0,"), bitmaps)
    }

    @Test
    fun `TSPL splits a tall row into bands of at most 128 dot rows`() {
        val bitmaps = TsplRenderer.render(listOf(black(300)), paper)
            .commandLines().filter { it.startsWith("BITMAP") }
        assertEquals(listOf("BITMAP 0,0,48,128,0,", "BITMAP 0,128,48,128,0,", "BITMAP 0,256,48,44,0,"), bitmaps)
    }

    @Test
    fun `TSPL sends a burnt dot as a cleared bit`() {
        // TSPL reads 1 as white. A black row must therefore go out as all zeros.
        val bytes = TsplRenderer.render(listOf(black(1)), paper)
        val header = "BITMAP 0,0,48,1,0,".toByteArray(Charsets.US_ASCII)
        val at = String(bytes, Charsets.ISO_8859_1).indexOf(String(header, Charsets.ISO_8859_1))
        assertTrue("BITMAP not emitted", at >= 0)
        val payload = bytes.copyOfRange(at + header.size, at + header.size + 48)
        assertTrue("black was sent as set bits: a negative", payload.all { it.toInt() == 0 })
    }

    @Test
    fun `TSPL declares the page even when there is nothing to draw`() {
        val lines = TsplRenderer.render(listOf(white(16)), paper).commandLines()
        assertEquals("SIZE 48 mm, 2 mm", lines[0])
        assertFalse(lines.any { it.startsWith("BITMAP") })
    }

    // ───────────────────────────── CPCL ─────────────────────────────

    @Test
    fun `CPCL opens a page exactly as tall as the rows, in dots`() {
        val lines = CpclRenderer.render(listOf(black(10), white(120), black(30)), paper).commandLines()
        assertEquals("! 0 200 200 160 1", lines[0])
        assertEquals("PAGE-WIDTH 384", lines[1])
        assertEquals("PRINT", lines.last { it.isNotEmpty() })
    }

    @Test
    fun `CPCL places each band at its row's height and skips white ones`() {
        val graphics = CpclRenderer.render(listOf(black(10), white(120), black(30)), paper)
            .commandLines().filter { it.startsWith("CG") }
        assertEquals(listOf("CG 48 10 0 0 ", "CG 48 30 0 130 "), graphics)
    }

    @Test
    fun `CPCL sends a burnt dot as a set bit, as ESC-POS does`() {
        val bytes = CpclRenderer.render(listOf(black(1)), paper)
        val header = "CG 48 1 0 0 "
        val at = String(bytes, Charsets.ISO_8859_1).indexOf(header)
        assertTrue("CG not emitted", at >= 0)
        val payload = bytes.copyOfRange(at + header.length, at + header.length + 48)
        assertTrue(payload.all { it.toInt() == -1 })
    }

    @Test
    fun `CPCL takes the paper's width from the profile`() {
        val lines = CpclRenderer.render(listOf(MonoRaster(576, 1, ByteArray(72) { -1 })), PaperProfile.MM80).commandLines()
        assertEquals("PAGE-WIDTH 576", lines[1])
    }

    // ───────────────────────────── the test strip ─────────────────────────────

    @Test
    fun `the ruler is as wide as the paper and marks each printable edge it can reach`() {
        val narrow = TestPrint.widthRuler(PaperProfile.MM58)
        assertEquals(384, narrow.width)
        // The 48 mm bar runs the ruler's full height on the last dots inside the edge.
        assertTrue(narrow.isBlack(383, 0))
        assertFalse("no 48 mm bar before the edge", narrow.isBlack(380, 0))

        val wide = TestPrint.widthRuler(PaperProfile.MM80)
        assertEquals(576, wide.width)
        assertTrue("48 mm bar missing on 80 mm", wide.isBlack(383, 0))
        assertTrue("72 mm bar missing", wide.isBlack(575, 0))
    }

    @Test
    fun `the ruler ticks every millimetre along the baseline`() {
        val ruler = TestPrint.widthRuler(PaperProfile.MM58)
        val bottom = ruler.height - 1
        val tickRow = ruler.height - 5
        // A tick every 8 dots, nothing between them above the baseline.
        assertTrue(ruler.isBlack(8, tickRow))
        assertFalse(ruler.isBlack(12, tickRow))
        assertTrue("baseline broken", (0 until ruler.width).all { ruler.isBlack(it, bottom) })
    }
}
