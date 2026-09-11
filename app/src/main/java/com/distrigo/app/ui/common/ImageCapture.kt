package com.distrigo.app.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.distrigo.app.data.image.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * The one place a picked image becomes something the app can store.
 *
 * This replaces five copies of the same eighteen lines — one per photo picker — which between them
 * carried three problems that only a shared implementation can fix once:
 *
 *  1. **They decoded at full resolution.** `BitmapFactory.decodeStream` with no options allocates
 *     the entire source bitmap before anything is scaled down. A 12 MP photo is 46 MB of
 *     ARGB_8888, a 50 MP photo is 190 MB, and `largeHeap` is not set — so picking a photo straight
 *     off the camera roll could exhaust the heap on a catalogue of ten products. [sampleSizeFor]
 *     makes the decoder do the first reduction itself, so nothing larger than roughly twice
 *     [MAX_EDGE] is ever held.
 *  2. **They upscaled small images.** The scale ratio was never clamped, so a 64x64 source was
 *     enlarged to 400x400 — interpolated detail that JPEG then has to encode, measured at 5x the
 *     bytes of the original for no added information. Hence [coerceAtMost].
 *  3. **They assumed the decode succeeded.** `decodeStream` returns null for a corrupt or
 *     unsupported file and the next line read `.width` off it. Every failure path here returns
 *     null instead, and callers are expected to tell the user rather than appear to do nothing.
 *
 * The output is an [ImageStore] reference — `img:<sha256>` — which the caller puts in the same
 * column the base64 payload used to occupy, so a row now carries ~70 bytes where it carried ~14 KB.
 * The three fixes above are independent of that move and shipped ahead of it.
 */
object ImageCapture {

    /** Longest edge of the stored image, in pixels. The value the five call sites all used. */
    const val MAX_EDGE = 400

    /** JPEG quality of the stored image. Also unchanged from the call sites. */
    const val JPEG_QUALITY = 50

    /**
     * Reads [uri], downscales it to fit [MAX_EDGE], writes it to [ImageStore], and returns the
     * `img:<hash>` reference to put in the entity's column — or null if the image could not be
     * read or could not be written.
     *
     * Runs on [Dispatchers.IO]: the picker callback this is called from runs on the main thread,
     * and decode, scale, JPEG encode, hashing and the file write are all real work.
     *
     * This used to return a `data:image/jpeg;base64,...` payload for the caller to store in the
     * row. Callers are unchanged otherwise — they still take the returned string and put it in the
     * same column; it is simply ~70 bytes now instead of ~14 KB.
     */
    suspend fun captureToStore(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val jpeg = compressFromUri(context, uri) ?: return@withContext null
        ImageStore.put(context, jpeg)
    }

    /**
     * The decode-scale-compress half on its own, as JPEG bytes.
     *
     * Separate from [captureToStore] because the backfill needs the same guarantees about size
     * and orientation when it re-encodes nothing — and because a caller that already has bytes
     * should not have to round-trip through a Uri to store them.
     */
    suspend fun compressFromUri(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        val decoded = decodeDownsampled(context, uri) ?: return@withContext null
        try {
            val scaled = scaleToFit(decoded)
            try {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            } finally {
                if (scaled !== decoded) scaled.recycle()
            }
        } catch (e: Exception) {
            null
        } finally {
            decoded.recycle()
        }
    }

    /**
     * Two passes over the stream: the first reads only the header for the source dimensions, the
     * second decodes for real at the sample size those dimensions imply.
     *
     * The URI has to be opened twice because an `InputStream` from a `ContentResolver` is not
     * reliably resettable — `mark`/`reset` support is up to the provider.
     */
    private fun decodeDownsampled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            // Null-check the *stream*, not the decode. With inJustDecodeBounds the decoder returns
            // null by design — it fills outWidth/outHeight and allocates nothing — so an elvis on
            // the decode result treats every successful bounds read as a failure. That is exactly
            // what this did until it was caught on a device: every photo pick ended in
            // "Image illisible" and wrote nothing.
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (e: Exception) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The largest power-of-two reduction that still leaves the longer edge at or above [MAX_EDGE],
     * so the final [scaleToFit] always has enough source pixels to work from.
     *
     * A 4032x3024 photo comes back at 8: the decoder produces 504x378 (0.7 MB) instead of
     * 4032x3024 (46.5 MB).
     */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= MAX_EDGE) sample *= 2
        return sample
    }

    /**
     * Fits the bitmap inside a [MAX_EDGE] square, preserving aspect ratio.
     *
     * The ratio is clamped to 1, so an image already smaller than [MAX_EDGE] is returned untouched
     * rather than enlarged. Returns the receiver itself when no scaling is needed, which is why
     * callers compare by identity before recycling.
     */
    private fun scaleToFit(source: Bitmap): Bitmap {
        val ratio = minOf(
            MAX_EDGE.toFloat() / source.width,
            MAX_EDGE.toFloat() / source.height
        ).coerceAtMost(1f)

        if (ratio == 1f) return source

        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }
}
