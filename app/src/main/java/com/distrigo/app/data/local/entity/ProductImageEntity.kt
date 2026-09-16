package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One photo of one product.
 *
 * ### Why a table rather than more columns
 *
 * A product carries up to [MAX_IMAGES_PER_PRODUCT] photos, ordered, with the first one acting as
 * the cover. Columns cannot express "ordered, variable length", and a JSON blob would put the
 * ordering beyond the reach of SQL — so this is a child table keyed on `product_id`, indexed on
 * `(product_id, position)` because every read of it is "the photos of one product, in order".
 *
 * ### `position` and the cover
 *
 * `position` is dense and zero-based within a product, and **position 0 is the cover**. That is not
 * a convention the UI happens to follow — it is what `products.image_uri` mirrors.
 *
 * `products.image_uri` stays exactly as it was: one reference, the cover. Everything that only ever
 * wants a single picture keeps reading it and is untouched by this table — the product list rows,
 * the cart lines, the Chargement and Inventaire pickers, and the four denormalised snapshot columns
 * on `ventes`, `purchase_orders`, `inventory_items` and `pertes` that record what a product looked
 * like at the moment of a transaction. Only the gallery reads this table.
 *
 * Keeping the two in step is [com.distrigo.app.data.repository.ProductRepository]'s job, and it
 * does it inside the same transaction as every mutation, so they cannot be observed apart.
 *
 * ### What a row holds
 *
 * `image_ref` is an [com.distrigo.app.data.image.ImageStore] reference — `img:<sha256>` — or, on a
 * device whose backfill has not finished, a legacy `data:` payload. Both resolve; see `EntityImage`.
 * Because the store is content-addressed, two products sharing a photo share one file, and deleting
 * a row never deletes a file.
 */
@Entity(
    tableName = "product_images",
    indices = [Index(value = ["product_id", "position"]), Index(value = ["uuid"], unique = true)]
)
data class ProductImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val product_id: Int,
    val image_ref: String,
    /** Dense, zero-based, unique within a product. 0 is the cover. */
    val position: Int,
    val created_at: String,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis()
)

/**
 * The ceiling on photos per product.
 *
 * Five is a product decision, not a technical limit, and it is enforced in the repository rather
 * than only in the UI so no caller can exceed it. It also keeps the thumbnail strip bounded: at
 * 384 dp — the width of the device this was built against — a centred strip fits exactly five
 * 50 dp thumbnails, so five is the largest count that needs no scrolling on the common case while
 * the strip still scrolls for narrower screens.
 */
const val MAX_IMAGES_PER_PRODUCT = 5
