package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.distrigo.app.data.local.entity.PriceHistoryEntity
import com.distrigo.app.data.local.entity.PurchaseOrderEntity
import com.distrigo.app.data.local.entity.PurchaseOrderItemEntity

@Dao
interface PurchaseDao {

    // ── Orders ──
    @Insert
    suspend fun insertOrder(order: PurchaseOrderEntity): Long

    @Query("SELECT * FROM purchase_orders ORDER BY id DESC")
    suspend fun getAllOrders(): List<PurchaseOrderEntity>

    @Query("SELECT * FROM purchase_orders WHERE id = :id")
    suspend fun getOrderById(id: Int): PurchaseOrderEntity?

    @Query("UPDATE purchase_orders SET status = :status WHERE id = :id")
    suspend fun updateOrderStatus(id: Int, status: String)

    @Query("""
        UPDATE purchase_orders
        SET note = :note, montant_paye = :montantPaye, total = :total
        WHERE id = :id
    """)
    suspend fun updateOrderFields(id: Int, note: String?, montantPaye: Double, total: Double)

    @Query("DELETE FROM purchase_orders WHERE id = :id")
    suspend fun deleteOrderById(id: Int)

    // ── Items ──
    @Insert
    suspend fun insertItems(items: List<PurchaseOrderItemEntity>)

    @Query("SELECT * FROM purchase_order_items WHERE purchase_order_id = :orderId")
    suspend fun getItemsForOrder(orderId: Int): List<PurchaseOrderItemEntity>

    @Query("DELETE FROM purchase_order_items WHERE purchase_order_id = :orderId")
    suspend fun deleteItemsForOrder(orderId: Int)

    // ── Price History ──
    @Insert
    suspend fun insertPriceHistory(entries: List<PriceHistoryEntity>)

    @Query("SELECT * FROM price_history WHERE product_id = :productId ORDER BY created_at DESC")
    suspend fun getPriceHistoryForProduct(productId: Int): List<PriceHistoryEntity>
}