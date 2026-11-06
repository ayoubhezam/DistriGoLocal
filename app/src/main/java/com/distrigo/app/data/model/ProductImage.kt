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

/**
 * What a batch of picked photos does to a gallery: [toAdd] goes in, in the order picked; [duplicates] were already
 * there (or picked twice), [overLimit] did not fit under the ceiling.
 */
data class ImageAddPlan(val toAdd: List<String>, val duplicates: Int, val overLimit: Int)

/**
 * [picked] against a gallery that holds [existing] and may hold [max]. A photo already there is counted as such even
 * when the gallery is full: "already there" is the truer answer, and nothing is lost by it.
 */
fun planImageAdds(existing: List<String>, picked: List<String>, max: Int): ImageAddPlan {
    val have = existing.toMutableSet()
    val toAdd = mutableListOf<String>()
    var duplicates = 0
    var overLimit = 0
    for (ref in picked) {
        when {
            ref in have -> duplicates++
            existing.size + toAdd.size >= max -> overLimit++
            else -> {
                toAdd += ref
                have += ref
            }
        }
    }
    return ImageAddPlan(toAdd, duplicates, overLimit)
}
