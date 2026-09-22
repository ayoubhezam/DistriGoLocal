package com.distrigo.app.data.print

import com.distrigo.app.data.print.transport.Pacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The benchmark's own arithmetic, so the number it reports can be trusted.
 *
 * The whole point of the experiment is a measurement someone will make an architectural decision on.
 * A pattern that is secretly half the size it claims, or a pacing figure that is out by a factor, is
 * worse than no measurement at all.
 */
class RasterBenchmarkTest {

    @Test
    fun `the pattern is about the size it was asked for`() {
        listOf(PaperProfile.MM58, PaperProfile.MM80).forEach { paper ->
            val raster = RasterBenchmark.pattern(paper, 40 * 1024)
            val bytes = raster.bits.size
            assertEquals("width should be the paper's", paper.rasterWidthDots, raster.width)
            // Height is a whole number of rows, so the size lands within one row of the target.
            assertTrue(
                "$bytes bytes for a 40 KB target on ${paper.size}",
                bytes in (40 * 1024 - raster.bytesPerRow)..(40 * 1024),
            )
        }
    }

    @Test
    fun `narrow paper buys more rows for the same bytes`() {
        // 48 bytes a row against 72, so the same payload is about half as long again on 58 mm — which
        // is why the menu quotes the paper length rather than only the byte count.
        val narrow = RasterBenchmark.pattern(PaperProfile.MM58).height
        val wide = RasterBenchmark.pattern(PaperProfile.MM80).height
        assertTrue("58 mm should be taller for the same bytes", narrow > wide)
        assertTrue(RasterBenchmark.paperMillimetres(PaperProfile.MM58) > RasterBenchmark.paperMillimetres(PaperProfile.MM80))
    }

    @Test
    fun `the pattern is a solid head, then checkerboard with countable markers`() {
        val raster = RasterBenchmark.pattern(PaperProfile.MM80)

        // Solid black at the top: the heaviest load a head sees, and where stutter shows first.
        assertTrue("top band not solid", (0 until raster.width).all { raster.isBlack(it, 0) })

        // Checkerboard in between — the most demanding pattern to print, and the one that shows
        // density banding most clearly.
        val y = 50
        assertTrue("row $y is not a checkerboard", (0 until 8).all { raster.isBlack(it, y) == ((it + y) % 2 == 0) })

        // A countable marker every hundred rows, so a pause can be located rather than just noticed.
        assertTrue("no marker at row 100", (0 until raster.width).all { raster.isBlack(it, 100) })
    }

    @Test
    fun `pacing overhead counts the pauses between chunks, not after the last one`() {
        val pacing = Pacing(chunkBytes = 256, pauseMs = 20, drainMs = 250)
        // Exactly one chunk: nothing to pause between, so only the drain.
        assertEquals(250L, pacing.overheadMs(256))
        // Four chunks: three pauses.
        assertEquals(3 * 20L + 250L, pacing.overheadMs(1024))
        // What 40 KB costs us on Bluetooth — the figure that separates "the link is slow" from "our
        // own chunking is too cautious for a payload this size".
        assertEquals(159 * 20L + 250L, pacing.overheadMs(40 * 1024))
    }

    @Test
    fun `the reported link rate discounts our own pauses`() {
        val result = RasterBenchmarkResult(bytes = 40 * 1024, elapsedMs = 8000, pacingOverheadMs = 3430)
        // Wall clock is what the user waited; the link rate is what the architecture would cost.
        assertEquals(5120.0, result.wallBytesPerSecond, 1.0)
        assertEquals(40 * 1024 * 1000.0 / (8000 - 3430), result.linkBytesPerSecond, 1.0)
        assertTrue(result.linkBytesPerSecond > result.wallBytesPerSecond)
    }

    @Test
    fun `a run that somehow took no longer than its own pauses reports zero rather than infinity`() {
        val result = RasterBenchmarkResult(bytes = 1024, elapsedMs = 100, pacingOverheadMs = 250)
        assertEquals(0.0, result.linkBytesPerSecond, 0.0)
    }
}
