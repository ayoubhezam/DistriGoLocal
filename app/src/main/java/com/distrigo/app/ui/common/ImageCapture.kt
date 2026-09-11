package com.distrigo.app.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
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
 * The output format is deliberately unchanged: a `data:image/jpeg;base64,...` URI, exactly what the
 * five call sites produced before. Moving that payload out of the database and onto disk is a
 * separate change, and this one is meant to be a drop-in that can ship on its own.
 */
object ImageCapture {

    /** Longest edge of the stored image, in pixels. The value the five call sites all used. */
    const val MAX_EDGE = 400

    /** JPEG quality of the stored image. Also unchanged from the call sites. */
    const val JPEG_QUALITY = 50

    private const val DATA_URI_PREFIX = "data:image/jpeg;base64,"

    /**
     * Reads [uri], downscales it to fit [MAX_EDGE], and returns it as a data URI — or null if the
     * image could not be read at all.
     *
     * Runs on [Dispatchers.IO]: the picker callback this is called from runs on the main thread,
     * and decode, scale, JPEG encode and base64 encode are all real work.
     */
    suspend fun encodeFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val decoded = decodeDownsampled(context, uri) ?: return@withContext null
        try {
            val scaled = scaleToFit(decoded)
            try {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                DATA_URI_PREFIX + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
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
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return null
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
