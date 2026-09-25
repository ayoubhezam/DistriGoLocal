package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.distrigo.app.data.local.entity.ProductBarcodeEntity
import kotlinx.coroutines.flow.Flow

/** Reads and writes products' barcodes — see ProductBarcodeEntity. */
@Dao
interface ProductBarcodeDao {

    /**
     * Every code of every product, each product's primary first. The catalogue is held in memory and
     * searched there, so its codes are read in one query and grouped, not one query per product.
     * Observed so that a code added or removed reaches every screen holding the catalogue.
     */
    @Query("SELECT * FROM product_barcodes ORDER BY product_id, position")
    fun observeAll(): Flow<List<ProductBarcodeEntity>>

    @Query("SELECT * FROM product_barcodes ORDER BY product_id, position")
    suspend fun getAll(): List<ProductBarcodeEntity>

    /** One product's codes, live, primary first. */
    @Query("SELECT * FROM product_barcodes WHERE product_id = :productId ORDER BY position ASC")
    fun observeForProduct(productId: Int): Flow<List<ProductBarcodeEntity>>

    /** The codes of [productIds] only, each product's primary first: one page of a paged list, in one query. */
    @Query("SELECT * FROM product_barcodes WHERE product_id IN (:productIds) ORDER BY product_id, position")
    suspend fun getForProducts(productIds: List<Int>): List<ProductBarcodeEntity>

    @Query("SELECT * FROM product_barcodes WHERE product_id = :productId ORDER BY position ASC")
    suspend fun getForProduct(productId: Int): List<ProductBarcodeEntity>

    /** A live product, other than [excludeId], that has [code] among its codes. */
    @Query(
        "SELECT b.product_id FROM product_barcodes b JOIN products p ON p.id = b.product_id " +
            "WHERE p.deleted_at IS NULL AND b.product_id != :excludeId AND b.code = trim(:code) LIMIT 1"
    )
    suspend fun findLiveOwner(code: String, excludeId: Int): Int?

    @Insert
    suspend fun insert(barcode: ProductBarcodeEntity): Long

    @Query("UPDATE product_barcodes SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Int, position: Int)

    @Query("DELETE FROM product_barcodes WHERE id = :id")
    suspend fun deleteById(id: Int)
}
