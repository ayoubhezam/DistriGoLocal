package com.distrigo.app.ui.common

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.distrigo.app.data.image.ImageStore

/**
 * The bitmap behind an entity's image column, whichever form that column is in.
 *
 * Every image column in the app — `products.image_uri`, `clients.image_uri`,
 * `suppliers.image_uri`, and the four denormalised copies on `ventes`, `purchase_orders`,
 * `inventory_items` and `pertes` — now holds one of three things:
 *
 *  - `img:<sha256>`, a reference into [ImageStore]; the normal case going forward.
 *  - `data:image/jpeg;base64,...`, a payload written before images moved to disk. Still read, so
 *    an upgraded device renders correctly from the first launch, before the backfill has run and
 *    whether or not it ever finishes.
 *  - null, or something unrecognised.
 *
 * All three, plus a reference whose file has gone missing, resolve to null here, and every call
 * site already draws its own placeholder in that case. Nothing throws.
 *
 * Keyed on the column value, so the decode happens once per image rather than once per
 * recomposition — the property the inline decodes this replaced did not have.
 *
 * Still a synchronous, full-size decode during composition: a 400px bitmap is 640 KB whatever it
 * is drawn into, and a 42dp avatar needs a sixteenth of that. Decoding at display size, caching
 * across screens, and getting the work off the composition thread are all the image loader's job,
 * in the change after this one.
 */
@Composable
fun rememberEntityBitmap(ref: String?): Bitmap? {
    val context = LocalContext.current
    return remember(ref) { ImageStore.loadBitmap(context, ref) }
}
