package com.distrigo.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The profile picture's square: the middle of the image, at most [ImageCapture.AVATAR_EDGE] a side, never enlarged. */
class ImageCaptureAvatarTest {

    private val edge = ImageCapture.AVATAR_EDGE

    @Test
    fun `a landscape photo gives its middle square`() {
        assertEquals(ImageCapture.SquareCrop(x = 500, y = 0, side = 1000, outSide = edge), ImageCapture.squareCrop(2000, 1000, edge))
    }

    @Test
    fun `a portrait photo gives its middle square`() {
        assertEquals(ImageCapture.SquareCrop(x = 0, y = 250, side = 756, outSide = edge), ImageCapture.squareCrop(756, 1256, edge))
    }

    @Test
    fun `a small image is not enlarged`() {
        assertEquals(ImageCapture.SquareCrop(x = 0, y = 20, side = 100, outSide = 100), ImageCapture.squareCrop(100, 140, edge))
    }

    @Test
    fun `for any size, the square lies inside the image and fits the edge`() {
        val r = Random(5)
        repeat(50_000) {
            val w = 1 + r.nextInt(12_000)
            val h = 1 + r.nextInt(12_000)
            val c = ImageCapture.squareCrop(w, h, edge)
            assertTrue("$w x $h → $c", c.x >= 0 && c.y >= 0 && c.x + c.side <= w && c.y + c.side <= h)
            assertEquals(minOf(w, h), c.side)
            assertTrue("$w x $h → $c", c.outSide in 1..edge && c.outSide <= c.side)
        }
    }

    @Test
    fun `the decode reduction keeps the shorter edge at or above the square`() {
        // A 12 MP camera photo: halved twice, 1008 x 756, still more than 512 on its short edge
        assertEquals(4, ImageCapture.squareSampleSize(4032, 3024, edge))
        assertEquals(1, ImageCapture.squareSampleSize(600, 4000, edge))
        assertEquals(1, ImageCapture.squareSampleSize(100, 100, edge))
        val r = Random(6)
        repeat(50_000) {
            val w = 1 + r.nextInt(12_000)
            val h = 1 + r.nextInt(12_000)
            val s = ImageCapture.squareSampleSize(w, h, edge)
            val short = minOf(w, h)
            if (s > 1) assertTrue("$w x $h / $s", short / s >= edge)
            assertTrue("$w x $h / $s: halving once more was possible", short / (s * 2) < edge)
        }
    }
}
