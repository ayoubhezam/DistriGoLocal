package com.distrigo.app.data.model

/**
 * One photo in a product's gallery, as the UI sees it.
 *
 * [position] is dense and zero-based, and position 0 is the cover — the one photo that every
 * single-image surface in the app shows, because it is what `products.image_uri` mirrors.
 */
data class ProductImage(
    val id: Int,
    val productId: Int,
    /** An `ImageStore` reference, or a legacy `data:` payload on a device mid-backfill. */
    val ref: String,
    val position: Int
) {
    val isCover: Boolean get() = position == 0
}
