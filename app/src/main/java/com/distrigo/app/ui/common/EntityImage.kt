package com.distrigo.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.distrigo.app.data.image.ImageStore
import java.io.File
import java.nio.ByteBuffer

/**
 * An entity's photo, drawn at the size it is actually displayed at, or [placeholder] if there
 * isn't one.
 *
 * ### Why this exists
 *
 * Every image column in the app — `products.image_uri`, `clients.image_uri`,
 * `suppliers.image_uri`, and the denormalised copies on `ventes`, `purchase_orders`,
 * `inventory_items` and `pertes` — holds one of three things: an [ImageStore] reference
 * (`img:<sha256>`), a `data:image/jpeg;base64,...` payload written before photos moved to disk, or
 * nothing. This resolves all three, and draws [placeholder] for the third — and for a payload that
 * will not decode, and for a reference whose file has gone missing. Nothing here throws; a missing
 * file reads as "this entity has no picture", which is what a restored device sees, since the
 * database is backed up and the image directory deliberately is not.
 *
 * ### Why Coil rather than BitmapFactory
 *
 * Stored images are 1024px, which is 4 MB of ARGB_8888. Most of them are drawn into a 34–44dp
 * avatar — about 126px on a 3x screen, or 64 KB. Decoding the whole thing to fill a sixtieth of it
 * was the cost the hand-rolled decodes all paid, repeated per screen because a `remember` is scoped
 * to one composable and thrown away when a list row scrolls off. That ratio is why the stored edge
 * could be raised at all: nothing here decodes a file at its stored size.
 *
 * Coil sizes the decode to the measured layout, keeps an LRU memory cache across screens and
 * scrolls, and does the work off the composition thread. The first load of an image shows
 * [placeholder] for a frame; every later one is served from cache.
 *
 * Every image surface in the app goes through here or [FileImage], the full-screen viewer and the
 * receipt logo included. The viewer used to decode whole files by hand, and the logo was decoded
 * with BitmapFactory in composition — on its settings screen, once per keystroke. Both are gone, and
 * so is `ImageStore.loadBitmap`, the full-size decode the viewer's helper was built on.
 *
 * [rememberAsyncImagePainter] rather than `AsyncImage`/`SubcomposeAsyncImage`: the callers each
 * draw their own placeholder — a tinted icon, initials, a "add a photo" prompt — and reading the
 * painter's state directly picks between them without the subcomposition `SubcomposeAsyncImage`
 * would add to every avatar in a list.
 */
@Composable
fun EntityImage(
    ref: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Coil takes a File for a stored reference and the raw bytes for a legacy payload; both are
    // sized and cached the same way from there. A null model leaves the painter Empty, which falls
    // through to the placeholder below.
    val model = remember(ref) {
        when {
            ImageStore.isStoredRef(ref) -> ImageStore.fileFor(context, ref)?.takeIf { it.isFile }
            ImageStore.isLegacyDataUri(ref) -> ImageStore.legacyBytes(ref)?.let(ByteBuffer::wrap)
            else -> null
        }
    }

    ModelImage(model, contentDescription, modifier, contentScale, placeholder)
}

/**
 * An image held in a plain file — the business logo — drawn the way [EntityImage] draws an entity's
 * photo: sized to its layout, off the main thread, cached, and [placeholder] when there is no file
 * or it will not decode.
 *
 * Coil's memory cache keys a File by its path and last-modified time, so a file rewritten under the
 * same name, as the logo is, is decoded afresh rather than served from the cache.
 */
@Composable
fun FileImage(
    file: File?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit
) {
    val model = remember(file) { file?.takeIf { it.isFile } }
    ModelImage(model, contentDescription, modifier, contentScale, placeholder)
}

@Composable
private fun ModelImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    placeholder: @Composable () -> Unit
) {
    val context = LocalContext.current

    if (model == null) {
        placeholder()
        return
    }

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context).data(model).build()
    )

    // The Image is always in the tree, even while the painter has nothing to draw. That is not a
    // detail — it is what makes the load happen at all.
    //
    // rememberAsyncImagePainter sizes its request from the painter's *draw* bounds, so the request
    // suspends until the painter has been drawn once. An earlier version here drew the Image only
    // once the state was Success, which cannot happen: Success needs the load, the load needs a
    // size, and the size needs the draw that was being withheld. Every photo in the app sat on its
    // placeholder forever, and nothing in the logs said so.
    //
    // Drawing it unconditionally breaks the cycle. Until the load finishes the painter draws
    // nothing, so the placeholder below shows through; once it succeeds the image covers it.
    Box(contentAlignment = Alignment.Center) {
        // Loading, Error and Empty all show the caller's own placeholder: a decode that fails is
        // the same outcome to the reader as no photo, and briefly showing initials beats a gap.
        if (painter.state !is AsyncImagePainter.State.Success) {
            placeholder()
        }
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}
