package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.distrigo.app.data.local.entity.ProductEntity

@Dao
interface ProductDao {

    // 1. جلب جميع المنتجات (رتبناها تنازلياً حسب الـ ID لتظهر الأحدث أولاً، ويمكن تعديلها لاحقاً)
    @Query("SELECT * FROM products ORDER BY id DESC")
    suspend fun getAllProducts(): List<ProductEntity>

    // 2. جلب منتج واحد بواسطة الـ ID
    @Query("SELECT * FROM products WHERE id = :productId")
    suspend fun getProductById(productId: Int): ProductEntity?

    // جلب المنتجات المرتبطة بمورد معين
    @Query("SELECT * FROM products WHERE supplier_id = :supplierId ORDER BY id DESC")
    suspend fun getProductsBySupplier(supplierId: Int): List<ProductEntity>

    // 3. إضافة منتج جديد (ترجع الـ ID الخاص بالمنتج الجديد)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    // 4. تحديث منتج موجود
    @Update
    suspend fun updateProduct(product: ProductEntity)

    // 5. حذف منتج بواسطة الـ ID
    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteProductById(productId: Int)
}