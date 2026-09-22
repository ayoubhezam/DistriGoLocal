package com.distrigo.app.data.print

import com.distrigo.app.data.print.lang.PrinterCodePage
import com.distrigo.app.data.print.lang.ReceiptEncoding
import com.distrigo.app.data.print.lang.ThermalRaster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two halves of the image-and-accent problem that can be settled without a printer.
 *
 * [ThermalRaster.fromGrayscale] takes no Android type precisely so it can be checked here rather than
 * on a device with a roll of paper in it.
 */
class ThermalRasterTest {

    private fun flat(value: Int, w: Int, h: Int) = IntArray(w * h) { value }

    @Test
    fun `width is rounded down to whole bytes and height follows the aspect ratio`() {
        val raster = ThermalRaster.fromGrayscale(flat(0, 100, 50), 100, 50, 387)
        assertEquals("width must be a multiple of 8", 384, raster.width)
        assertEquals(0, raster.width % 8)
        assertEquals(192, raster.height)
        assertEquals(raster.bytesPerRow * raster.height, raster.bits.size)
    }

    @Test
    fun `solid black burns every dot and solid white burns none`() {
        val black = ThermalRaster.fromGrayscale(flat(0, 16, 16), 16, 16, 16)
        assertTrue("black image left white dots", (0 until black.width).all { black.isBlack(it, 0) })

        val white = ThermalRaster.fromGrayscale(flat(255, 16, 16), 16, 16, 16)
        assertTrue("white image burnt dots", (0 until white.width).none { white.isBlack(it, 0) })
    }

    @Test
    fun `mid grey dithers to a mix rather than to one flat tone`() {
        // The whole reason Floyd-Steinberg is here: a plain threshold would turn this into all-black or
        // all-white, and a logo's shading with it.
        val raster = ThermalRaster.fromGrayscale(flat(128, 64, 64), 64, 64, 64)
        val burnt = (0 until raster.height).sumOf { y -> (0 until raster.width).count { raster.isBlack(it, y) } }
        val total = raster.width * raster.height
        assertTrue("mid grey came out flat ($burnt of $total)", burnt in (total / 5)..(total * 4 / 5))
    }

    @Test
    fun `isBlack reads outside the image as white`() {
        val raster = ThermalRaster.fromGrayscale(flat(0, 8, 8), 8, 8, 8)
        assertTrue(!raster.isBlack(-1, 0))
        assertTrue(!raster.isBlack(0, -1))
        assertTrue(!raster.isBlack(raster.width, 0))
        assertTrue(!raster.isBlack(0, raster.height))
    }

    @Test
    fun `equal rasters compare equal despite the byte array`() {
        val a = ThermalRaster.fromGrayscale(flat(90, 32, 32), 32, 32, 32)
        val b = ThermalRaster.fromGrayscale(flat(90, 32, 32), 32, 32, 32)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `CP1252 carries French accents unchanged`() {
        val bytes = ReceiptEncoding.encode("Détail à Alger", PrinterCodePage.CP1252)
        assertEquals("Détail à Alger", String(bytes, charset("windows-1252")))
    }

    @Test
    fun `characters the code page cannot hold are transliterated, never turned into question marks`() {
        // A curly apostrophe and an em dash are what a pasted note actually brings in, and CP437 has
        // neither — while it does hold é, so only the typography is reduced. Nothing becomes "?".
        val bytes = ReceiptEncoding.encode("L’année — café", PrinterCodePage.CP437)
        val out = String(bytes, charset("IBM437"))
        assertEquals("L'année - café", out)

        // A character with no byte anywhere in the page is dropped rather than replaced, because "?"
        // in the middle of a product name reads as data loss to the client holding the receipt.
        val withEmoji = ReceiptEncoding.encode("Café 🙂 chaud", PrinterCodePage.CP1252)
        val emojiOut = String(withEmoji, charset("windows-1252"))
        assertTrue("question marks in \"$emojiOut\"", !emojiOut.contains("?"))
        assertEquals("Café  chaud", emojiOut)
    }

    @Test
    fun `transliterate reduces every accent to ASCII`() {
        val out = ReceiptEncoding.transliterate(PrinterCodePage.ACCENT_PROBE)
        assertTrue("non-ASCII survived: \"$out\"", out.all { it.code in 0x20..0x7E })
        assertEquals("EEACEUOI aeeuceaoii \"\"deg", out)
    }
}
