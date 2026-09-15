package com.distrigo.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The size a picked image — a product photo, or the business logo — is stored at: never larger than
 * [ImageCapture.MAX_EDGE] on its longest edge, never enlarged, and the same shape.
 */
class ImageCaptureScaledDimensionsTest {

    private val max = ImageCapture.MAX_EDGE

    @Test
    fun `an image that already fits is kept as it is`() {
        for ((w, h) in listOf(1 to 1, 64 to 64, 1024 to 1024, 1024 to 1, 800 to 600, 1 to 1024)) {
            assertNull("$w x $h", ImageCapture.scaledDimensions(w, h))
        }
    }

    @Test
    fun `a camera photo or a large logo comes down to the longest edge`() {
        val (w, h) = ImageCapture.scaledDimensions(3000, 2000)!!
        assertEquals(1024, w)
        assertTrue("height $h", h in 682..683)
        val (pw, ph) = ImageCapture.scaledDimensions(3024, 4032)!!
        assertTrue("portrait $pw x $ph", ph in 1023..1024 && pw in 767..768)
    }

    @Test
    fun `for any size, the result fits, is never larger, and keeps the shape`() {
        val r = Random(8)
        repeat(50_000) {
            val w = 1 + r.nextInt(12_000)
            val h = 1 + r.nextInt(12_000)
            val d = ImageCapture.scaledDimensions(w, h)
            if (w <= max && h <= max) {
                assertNull("$w x $h fits already", d)
                return@repeat
            }
            val (sw, sh) = d!!
            assertTrue("fits: $w x $h -> $sw x $sh", sw <= max && sh <= max)
            assertTrue("not enlarged: $w x $h -> $sw x $sh", sw <= w && sh <= h && sw >= 1 && sh >= 1)
            assertTrue("longest edge used: $w x $h -> $sw x $sh", maxOf(sw, sh) >= max - 1)
            // Same shape, within the one pixel the truncation to Int can lose on each side.
            val expectedShort = minOf(w, h).toDouble() * max / maxOf(w, h)
            assertTrue("shape: $w x $h -> $sw x $sh", kotlin.math.abs(minOf(sw, sh) - expectedShort) <= 1.0 || minOf(sw, sh) == 1)
        }
    }
}
