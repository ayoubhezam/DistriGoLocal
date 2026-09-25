package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.distrigo.app.data.local.entity.ProductBarcodeEntity
import com.distrigo.app.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

/** A row of a paged product list: the product, and `LOWER(name)` exactly as SQLite computed it. */
data class ProductPageRow(
    @Embedded val product: ProductEntity,
    val sort_name: String,
)

@Dao
interface ProductDao {

    /** A page of a product list — see `ProductListSql` for the SQL and why it is built per request. */
    @RawQuery
    suspend fun pageProducts(query: SupportSQLiteQuery): List<ProductPageRow>

    /** One live product, or null once it is deleted or binned: the Produits detail, form and history screens. */
    @Query("SELECT * FROM products WHERE id = :id AND deleted_at IS NULL")
    fun observeLiveProduct(id: Int): Flow<ProductEntity?>

    /** The live products among [ids], observed: a form's cart lines. */
    @Query("SELECT * FROM products WHERE id IN (:ids) AND deleted_at IS NULL")
    fun observeLiveProductsByIds(ids: List<Int>): Flow<List<ProductEntity>>

    /** The live products among [ids], once: a draft's lines, hydrated. */
    @Query("SELECT * FROM products WHERE id IN (:ids) AND deleted_at IS NULL")
    suspend fun getLiveProductsByIds(ids: List<Int>): List<ProductEntity>

    /**
     * The newest live product with [code] among its barcodes, ignoring case - what a scan finds. Newest
     * first because that is the order the scanners searched the catalogue list in.
     */
    @Query("""
        SELECT p.* FROM products p
        WHERE p.deleted_at IS NULL
          AND (p.barcode = :code COLLATE NOCASE
               OR EXISTS (SELECT 1 FROM product_barcodes b WHERE b.product_id = p.id AND b.code = :code COLLATE NOCASE))
        ORDER BY p.id DESC
        LIMIT 1
    """)
    suspend fun findLiveByBarcode(code: String): ProductEntity?

    /** The camion's stock summed over every live product, live: the tournée's "empty truck" check. */
    @Query("SELECT COALESCE(SUM(camion_stock), 0.0) FROM products WHERE deleted_at IS NULL")
    fun observeCamionStockTotal(): Flow<Double>

    /** The highest id of a live product, for the form's generated barcode; null when there are none. */
    @Query("SELECT MAX(id) FROM products WHERE deleted_at IS NULL")
    suspend fun maxLiveId(): Int?

    /** How many products a `ProductListSql.count` query matches, re-counted when a product or barcode changes. */
    @RawQuery(observedEntities = [ProductEntity::class, ProductBarcodeEntity::class])
    fun observeProductCount(query: SupportSQLiteQuery): Flow<Int>

    // 1. جلب جميع المنتجات (رتبناها تنازلياً حسب الـ ID لتظهر الأحدث أولاً، ويمكن تعديلها لاحقاً)
    @Query("SELECT * FROM products WHERE deleted_at IS NULL ORDER BY id DESC")
    suspend fun getAllProducts(): List<ProductEntity>

    // نسخة مُراقَبة: Room يعيد إصدار القائمة تلقائياً عند أي كتابة على جدول المنتجات،
    // مهما كان الـ DAO أو الـ Repository الذي نفّذ الكتابة (بيع، chargement، perte، retour…)
    @Query("SELECT * FROM products WHERE deleted_at IS NULL ORDER BY id DESC")
    fun observeAllProducts(): Flow<List<ProductEntity>>

    // 2. جلب منتج واحد بواسطة الـ ID
    @Query("SELECT * FROM products WHERE id = :productId AND deleted_at IS NULL")
    suspend fun getProductById(productId: Int): ProductEntity?

    /**
     * The product whether it is live or in the bin: for documents that already name it — a bon received or a
     * sale edited after the product was binned — since the goods and the money are real either way.
     */
    @Query("SELECT * FROM products WHERE id = :productId")
    suspend fun getProductByIdIncludingBin(productId: Int): ProductEntity?

    /** A live product with this name, ignoring case and surrounding spaces, other than [excludeId]. */
    @Query("SELECT * FROM products WHERE deleted_at IS NULL AND id != :excludeId AND trim(name) = trim(:name) COLLATE NOCASE LIMIT 1")
    suspend fun findLiveByName(name: String, excludeId: Int): ProductEntity?

    // جلب المنتجات المرتبطة بمورد معين
    @Query("SELECT * FROM products WHERE supplier_id = :supplierId AND deleted_at IS NULL ORDER BY id DESC")
    suspend fun getProductsBySupplier(supplierId: Int): List<ProductEntity>

    // 3. إضافة منتج جديد (ترجع الـ ID الخاص بالمنتج الجديد)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    // 4. تحديث منتج موجود
    @Update
    suspend fun updateProduct(product: ProductEntity)

    // 5. حذف منتج بواسطة الـ ID — soft: the row stays, see MIGRATION_44_45
    @Query("UPDATE products SET deleted_at = CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER) WHERE id = :productId AND deleted_at IS NULL")
    suspend fun softDeleteProductById(productId: Int)
}