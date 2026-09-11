package com.distrigo.app.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Decodes one of the `data:image/jpeg;base64,...` values the app stores in its entity rows.
 *
 * Returns null for anything it cannot read — a malformed payload, a truncated row — so callers can
 * fall through to whatever placeholder they already draw.
 */
fun decodeBase64Bitmap(imageUri: String): Bitmap? = try {
    val bytes = Base64.decode(imageUri.substringAfter("base64,"), Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
} catch (e: Exception) {
    null
}

/**
 * [decodeBase64Bitmap], keyed on the payload so it runs once per image rather than once per
 * recomposition.
 *
 * Seven call sites decoded inline in the composable body with no `remember` at all — including the
 * client list row, inside a `LazyColumn`. A 400x400 bitmap is 640 KB of ARGB_8888, so each of those
 * allocated 640 KB and threw the previous one away on every recomposition: on a scrolling list,
 * continuously, on the main thread. Memoizing does not make the decode cheaper, it makes it happen
 * once.
 *
 * Two things this deliberately does not do. It does not downsample to the display size, so a 42 dp
 * avatar still decodes at full 400 px — fixing that needs a size-aware image loader rather than a
 * `remember`. And the decode still runs synchronously during composition, because returning null
 * first and the bitmap later would flash a placeholder on every screen that uses this. Both are
 * Coil's job, in the change that moves these payloads onto disk; this one only stops the repeat
 * work, without altering a single layout or placeholder.
 */
@Composable
fun rememberBase64Bitmap(imageUri: String?): Bitmap? =
    remember(imageUri) { imageUri?.let(::decodeBase64Bitmap) }
