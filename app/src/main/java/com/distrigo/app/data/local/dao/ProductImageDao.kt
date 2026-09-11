package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.distrigo.app.data.local.entity.ProductImageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes one product's gallery.
 *
 * Every query is scoped to a `product_id` — the gallery is only ever asked about one product at a
 * time, and the index on `(product_id, position)` serves both the filter and the ordering, so a
 * read stays proportional to that product's photos rather than to the table.
 */
@Dao
interface ProductImageDao {

    /**
     * The product's photos, cover first.
     *
     * Observed rather than fetched: adding, deleting or re-covering a photo has to be visible on
     * the detail screen the moment the transaction commits, and Room re-emits on any write to this
     * table without the screen having to ask.
     */
    @Query("SELECT * FROM product_images WHERE product_id = :productId ORDER BY position ASC")
    fun observeForProduct(productId: Int): Flow<List<ProductImageEntity>>

    @Query("SELECT * FROM product_images WHERE product_id = :productId ORDER BY position ASC")
    suspend fun getForProduct(productId: Int): List<ProductImageEntity>

    @Query("SELECT COUNT(*) FROM product_images WHERE product_id = :productId")
    suspend fun countForProduct(productId: Int): Int

    @Query("SELECT * FROM product_images WHERE id = :id")
    suspend fun getById(id: Int): ProductImageEntity?

    /** The cover, or null for a product with no photos. */
    @Query("SELECT * FROM product_images WHERE product_id = :productId ORDER BY position ASC LIMIT 1")
    suspend fun getCover(productId: Int): ProductImageEntity?

    /** At most one row can match — a product never holds the same reference twice. */
    @Query("SELECT * FROM product_images WHERE product_id = :productId AND image_ref = :ref LIMIT 1")
    suspend fun findByRef(productId: Int, ref: String): ProductImageEntity?

    @Insert
    suspend fun insert(image: ProductImageEntity): Long

    @Query("UPDATE product_images SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Int, position: Int)

    @Query("DELETE FROM product_images WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM product_images WHERE product_id = :productId")
    suspend fun deleteForProduct(productId: Int)
}
