package com.distrigo.app.data.print

import com.distrigo.app.data.print.lang.MonoRaster

/**
 * A dummy image the size of a rasterised receipt, for finding out what one would actually cost.
 *
 * Printing the whole receipt as a bitmap is the only way to get Arabic right — ESC/POS has no bidi
 * and no contextual shaping, and CP864 holds presentation forms, so using it means writing a text
 * engine to avoid the one Android already ships. The question is not whether it works but whether it
 * is *fast enough*: a receipt is ~1.5 KB as text and ~40 KB as dots, and the link behind these
 * Bluetooth modules is often a 9 600-baud serial bridge. That is the difference between four seconds
 * and a minute, and no amount of arithmetic settles it — the printer does.
 *
 * So this exists to be run once, on the real hardware, before anything is built on the answer.
 *
 * The pattern is chosen to make **stutter** visible, not to look like a receipt. A thermal head's
 * density varies with temperature and dwell, so a link that cannot keep up with the mechanism leaves
 * horizontal bands of lighter or darker grey. A 50 % checkerboard is the most demanding thing to
 * print — every other dot, maximum switching — and shows those bands most clearly; the solid bars
 * every hundred rows give something to count, so a pause can be located rather than just noticed.
 */
object RasterBenchmark {

    /** What a rasterised receipt of ordinary length comes to on 80 mm paper. */
    const val DEFAULT_TARGET_BYTES = 40 * 1024

    /** Solid black at the top: the heaviest load a head ever sees, and where stutter shows first. */
    private const val SOLID_HEAD_ROWS = 32

    /** A countable marker every this many rows. */
    private const val MARKER_PERIOD = 100
    private const val MARKER_ROWS = 4

    /**
     * A [MonoRaster] of about [targetBytes], as wide as [paper] and as tall as that makes it.
     *
     * On 80 mm that is ~570 dot rows, about 71 mm of paper; on 58 mm the same bytes are ~850 rows and
     * about 107 mm, because narrower paper spends fewer bytes per row and so buys more of them.
     */
    fun pattern(paper: PaperProfile, targetBytes: Int = DEFAULT_TARGET_BYTES): MonoRaster {
        val width = paper.rasterWidthDots
        val bytesPerRow = width / 8
        val height = (targetBytes / bytesPerRow).coerceAtLeast(SOLID_HEAD_ROWS + MARKER_ROWS)

        val bits = ByteArray(bytesPerRow * height)
        for (y in 0 until height) {
            val solid = y < SOLID_HEAD_ROWS || (y % MARKER_PERIOD) < MARKER_ROWS
            for (x in 0 until width) {
                val black = if (solid) true else (x + y) % 2 == 0
                if (black) {
                    val i = y * bytesPerRow + (x shr 3)
                    bits[i] = (bits[i].toInt() or (1 shl (7 - (x and 7)))).toByte()
                }
            }
        }
        return MonoRaster(width = width, height = height, bits = bits)
    }

    /** How much paper [pattern] will use, for warning the user before it starts. */
    fun paperMillimetres(paper: PaperProfile, targetBytes: Int = DEFAULT_TARGET_BYTES): Int {
        val rows = pattern(paper, targetBytes).height
        // 203 dpi is 8 dots per millimetre.
        return rows / 8
    }
}

/**
 * What one benchmark run measured.
 *
 * [pacingOverheadMs] is ours, not the printer's: the transport writes in chunks with a deliberate
 * pause between them, because these printers apply no back-pressure and a whole receipt handed over
 * at once overflows the buffer silently. At 1.5 KB of text that pacing costs ~120 ms and nobody
 * notices. At 40 KB it can be several seconds, and reporting it separately is the difference between
 * "the link is too slow" and "our own pacing is too cautious for payloads this size" — which have
 * opposite fixes.
 */
data class RasterBenchmarkResult(
    val bytes            : Int,
    val elapsedMs        : Long,
    val pacingOverheadMs : Long,
) {
    /** Bytes per second as the user experiences it, pacing included. */
    val wallBytesPerSecond: Double
        get() = if (elapsedMs <= 0) 0.0 else bytes * 1000.0 / elapsedMs

    /** Bytes per second with our own pauses discounted — the link's own rate, near enough. */
    val linkBytesPerSecond: Double
        get() = (elapsedMs - pacingOverheadMs).let { if (it <= 0) 0.0 else bytes * 1000.0 / it }
}

/** A benchmark run's outcome: a measurement, or the reason there was not one. */
sealed interface RasterBenchmarkOutcome {
    data class Measured(val result: RasterBenchmarkResult) : RasterBenchmarkOutcome
    data class Failed(val failure: com.distrigo.app.data.print.transport.PrintFailure) : RasterBenchmarkOutcome
}
