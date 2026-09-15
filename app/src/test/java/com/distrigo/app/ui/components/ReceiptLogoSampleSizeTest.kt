package com.distrigo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The receipt logo is decoded at a sample size that keeps both sides at least the target, and no
 * larger than it needs to be: halving once more would take a side below the target.
 */
class ReceiptLogoSampleSizeTest {

    private val target = 256

    @Test
    fun `a logo already at or below the target is decoded whole, as before`() {
        for ((w, h) in listOf(1 to 1, 64 to 64, 256 to 256, 511 to 511, 300 to 200, 200 to 4000, 0 to 0)) {
            assertEquals("$w x $h", 1, ReceiptPdfGenerator.logoSampleSize(w, h, target))
        }
    }

    @Test
    fun `camera-sized logos are sampled down to just above the target`() {
        assertEquals(2, ReceiptPdfGenerator.logoSampleSize(512, 512, target))
        assertEquals(8, ReceiptPdfGenerator.logoSampleSize(4000, 3000, target))
        assertEquals(16, ReceiptPdfGenerator.logoSampleSize(4624, 4624, target))
        // The shorter side decides.
        assertEquals(4, ReceiptPdfGenerator.logoSampleSize(8000, 1100, target))
    }

    @Test
    fun `for any size the result is a power of two, keeps both sides, and is the largest that does`() {
        val r = Random(15)
        repeat(20_000) {
            val w = 1 + r.nextInt(9000)
            val h = 1 + r.nextInt(9000)
            val s = ReceiptPdfGenerator.logoSampleSize(w, h, target)
            assertTrue("power of two: $s", s > 0 && (s and (s - 1)) == 0)
            if (w >= target && h >= target) {
                assertTrue("keeps both sides at $w x $h / $s", w / s >= target && h / s >= target)
            } else {
                assertEquals("too small to sample: $w x $h", 1, s)
            }
            assertTrue("largest at $w x $h / $s", w / (s * 2) < target || h / (s * 2) < target)
        }
    }
}
