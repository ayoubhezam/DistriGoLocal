package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/** One row still holding a base64 payload: its id, and the payload to convert. */
data class LegacyImageRow(
    val id: Int,
    val payload: String
)

/**
 * Reads and rewrites the seven image columns, for the one-off conversion of `data:` payloads into
 * [com.distrigo.app.data.image.ImageStore] references.
 *
 * Seven pairs rather than one generic statement: Room will not parameterise a table or column name,
 * and a `@RawQuery` that built them by hand would trade compile-time checking for nothing. The
 * repetition is the price of every one of these being verified at build time.
 *
 * Every read is keyed on `id > :afterId ORDER BY id`, not `LIMIT/OFFSET`. Two reasons: the rows are
 * being rewritten as the walk proceeds, so an offset would drift past rows it never saw, and a row
 * that fails to convert is stepped over rather than fetched forever by a filter it still matches.
 * The walk terminates whatever any individual row does.
 */
@Dao
interface ImageBackfillDao {

    // ── Sources ──

    @Query("SELECT id, image_uri AS payload FROM products WHERE image_uri LIKE 'data:%' AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun products(afterId: Int, limit: Int): List<LegacyImageRow>

    @Query("UPDATE products SET image_uri = :ref WHERE id = :id")
    suspend fun setProduct(id: Int, ref: String)

    @Query("SELECT id, image_uri AS payload FROM clients WHERE image_uri LIKE 'data:%' AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun clients(afterId: Int, limit: Int): List<LegacyImageRow>

    @Query("UPDATE clients SET image_uri = :ref WHERE id = :id")
    suspend fun setClient(id: Int, ref: String)

    @Query("SELECT id, image_uri AS payload FROM suppliers WHERE image_uri LIKE 'data:%' AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun suppliers(afterId: Int, limit: Int): List<LegacyImageRow>

    @Query("UPDATE suppliers SET image_uri = :ref WHERE id = :id")
    suspend fun setSupplier(id: Int, ref: String)

    // ── Denormalised copies on the history tables ──

    @Query("SELECT id, client_image_uri AS payload FROM ventes WHERE client_image_uri LIKE 'data:%' AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun ventes(afterId: Int, limit: Int): List<LegacyImageRow>

    @Query("UPDATE ventes SET client_image_uri = :ref WHERE id = :id")
    suspend fun setVente(id: Int, ref: String)

    @Query("SELECT id, supplier_image_uri AS payload FROM purchase_orders WHERE supplier_image_uri LIKE 'data:%' AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun purchaseOrders(afterId: Int, limit: Int): List<LegacyImageRow>

    @Query("UPDATE purchase_orders SET supplier_image_uri = :ref WHERE id = :id")
    suspend fun setPurchaseOrder(id: Int, ref: String)

    @Query("SELECT id, product_image_uri AS payload FROM inventory_items WHERE product_image_uri LIKE 'data:%' AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun inventoryItems(afterId: Int, limit: Int): List<LegacyImageRow>

    @Query("UPDATE inventory_items SET product_image_uri = :ref WHERE id = :id")
    suspend fun setInventoryItem(id: Int, ref: String)

    @Query("SELECT id, product_image_uri AS payload FROM pertes WHERE product_image_uri LIKE 'data:%' AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun pertes(afterId: Int, limit: Int): List<LegacyImageRow>

    @Query("UPDATE pertes SET product_image_uri = :ref WHERE id = :id")
    suspend fun setPerte(id: Int, ref: String)

    // ── The gallery ──
    // Migration 37 -> 38 seeds this table from products.image_uri, copying whatever that column
    // held. On a device upgrading from before the files change that is a legacy payload, so the
    // walk has to cover it too. Content addressing means it converges on the same img: reference
    // as the products row whichever of the two is converted first.

    @Query("SELECT id, image_ref AS payload FROM product_images WHERE image_ref LIKE 'data:%' AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun productImages(afterId: Int, limit: Int): List<LegacyImageRow>

    @Query("UPDATE product_images SET image_ref = :ref WHERE id = :id")
    suspend fun setProductImage(id: Int, ref: String)
}
