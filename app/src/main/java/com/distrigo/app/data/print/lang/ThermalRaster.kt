package com.distrigo.app.data.print.lang

/**
 * A 1-bit image in the form a thermal head takes it: row-major bytes, 8 horizontal dots each, most
 * significant bit leftmost, a set bit meaning a burnt (black) dot.
 *
 * That is exactly ESC/POS `GS v 0`'s payload layout, so the renderer writes [bits] straight out. It is
 * also what the preview decodes back into pixels, which is the point — the logo on screen is the logo
 * the paper will carry, speckles and all.
 */
data class MonoRaster(
    val width : Int,
    val height: Int,
    val bits  : ByteArray,
) {
    /** Bytes per row. [width] is always a multiple of 8, so this divides exactly. */
    val bytesPerRow: Int get() = width / 8

    /** Whether the dot at ([x], [y]) is burnt. Out-of-bounds reads as white, so callers need no guard. */
    fun isBlack(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= width || y >= height) return false
        val byte = bits[y * bytesPerRow + (x shr 3)].toInt()
        return (byte shr (7 - (x and 7))) and 1 == 1
    }

    // Generated equals/hashCode would compare ByteArray by identity, which makes two identical
    // rasters unequal and silently breaks Compose's remember/skipping as well as any test assertion.
    override fun equals(other: Any?): Boolean =
        this === other || (other is MonoRaster && width == other.width && height == other.height && bits.contentEquals(other.bits))

    override fun hashCode(): Int = (width * 31 + height) * 31 + bits.contentHashCode()
}

/**
 * Turns an image into something a thermal printer can burn.
 *
 * Two steps, and the second is the one that matters. Scaling is obvious. **Dithering** is why a logo
 * stays legible: a thermal head has no grey, so a plain threshold flattens a photographic logo — a
 * gradient, a shadow, anti-aliased type — into a black blob or an empty outline. Floyd–Steinberg
 * spreads each pixel's rounding error into its neighbours, which reads as texture rather than loss.
 *
 * The grayscale entry point is pure Kotlin and takes no Android type, so the algorithm is unit-tested
 * on the desk; [fromBitmap] is the thin adapter over it.
 */
object ThermalRaster {

    /**
     * Above this, a dot stays white. Chosen on the dark side of the middle because thermal paper
     * develops wider than the dot commanded — a receipt printed at a true 50% threshold comes out
     * noticeably heavier than it looks on screen.
     */
    private const val THRESHOLD = 128

    /**
     * Dithers [gray] (one byte per pixel, 0 black … 255 white, row-major) into a [MonoRaster] scaled to
     * [targetWidth].
     *
     * [targetWidth] is rounded down to a multiple of 8 — a raster whose width is not whole bytes has no
     * representation in `GS v 0`, and padding it instead of rounding would shear the image.
     *
     * Height follows the source aspect ratio. Scaling is nearest-neighbour: the source has already been
     * downscaled once by `ImageCapture` on its way into the ImageStore, and a second smooth resample
     * before a 1-bit quantisation buys blur rather than detail.
     */
    fun fromGrayscale(gray: IntArray, srcWidth: Int, srcHeight: Int, targetWidth: Int): MonoRaster {
        val width = (targetWidth / 8) * 8
        require(width > 0) { "targetWidth must be at least 8 dots, was $targetWidth" }
        require(srcWidth > 0 && srcHeight > 0) { "empty source image: ${srcWidth}x$srcHeight" }
        require(gray.size >= srcWidth * srcHeight) { "grayscale buffer too small for ${srcWidth}x$srcHeight" }

        val height = ((srcHeight.toLong() * width) / srcWidth).toInt().coerceAtLeast(1)

        // The error diffuses across a working copy in the target's own resolution, so a scaled-down
        // logo dithers against the dots that will actually be burnt rather than against source pixels
        // that two of them share.
        val work = IntArray(width * height)
        for (y in 0 until height) {
            val srcY = (y.toLong() * srcHeight / height).toInt().coerceIn(0, srcHeight - 1)
            for (x in 0 until width) {
                val srcX = (x.toLong() * srcWidth / width).toInt().coerceIn(0, srcWidth - 1)
                work[y * width + x] = gray[srcY * srcWidth + srcX]
            }
        }

        val bytesPerRow = width / 8
        val bits = ByteArray(bytesPerRow * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val old = work[i]
                val new = if (old < THRESHOLD) 0 else 255
                if (new == 0) {
                    val byteIndex = y * bytesPerRow + (x shr 3)
                    bits[byteIndex] = (bits[byteIndex].toInt() or (1 shl (7 - (x and 7)))).toByte()
                }
                val error = old - new
                // Floyd–Steinberg: 7/16 right, 3/16 below-left, 5/16 below, 1/16 below-right.
                if (x + 1 < width)                 work[i + 1]             += error * 7 / 16
                if (y + 1 < height) {
                    if (x > 0)                     work[i + width - 1]     += error * 3 / 16
                                                   work[i + width]         += error * 5 / 16
                    if (x + 1 < width)             work[i + width + 1]     += error * 1 / 16
                }
            }
        }

        return MonoRaster(width = width, height = height, bits = bits)
    }

    /**
     * [fromGrayscale] for an Android bitmap, flattening transparency onto white first.
     *
     * A logo saved as a transparent PNG would otherwise dither its own empty background into noise:
     * an unset alpha channel reads as black in the luminance below, so the paper would come out with a
     * dark rectangle around the mark.
     */
    fun fromBitmap(bitmap: android.graphics.Bitmap, targetWidth: Int): MonoRaster {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            // Rec. 601 luma, which is what the eye weights and what every dithering reference assumes.
            val luma = (r * 299 + g * 587 + b * 114) / 1000
            gray[i] = if (a == 255) luma else (luma * a + 255 * (255 - a)) / 255
        }
        return fromGrayscale(gray, w, h, targetWidth)
    }
}
