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

    // ── Historique paginé (Achats & Paiements — Supplier Detail) ──
    @Query("""
        SELECT * FROM purchase_orders
        WHERE supplier_id = :supplierId
        AND (:cursorCreatedAt IS NULL OR created_at < :cursorCreatedAt)
        AND (:search = '' OR ('#' || CAST(id AS TEXT)) LIKE '%' || :search || '%' OR note LIKE '%' || :search || '%')
        AND (
            :statusFilter = 'TOUTES'
            OR (:statusFilter = 'PAYEE' AND montant_paye >= total AND total > 0)
            OR (:statusFilter = 'PARTIELLE' AND montant_paye > 0 AND montant_paye < total)
            OR (:statusFilter = 'IMPAYEE' AND montant_paye <= 0)
        )
        ORDER BY created_at DESC, id DESC
        LIMIT :limit
    """)
    suspend fun pageOrdersForSupplier(
        supplierId: Int,
        cursorCreatedAt: String?,
        search: String,
        statusFilter: String,
        limit: Int
    ): List<PurchaseOrderEntity>

    @Query("""
        SELECT COUNT(*) FROM purchase_orders
        WHERE supplier_id = :supplierId
        AND (:search = '' OR ('#' || CAST(id AS TEXT)) LIKE '%' || :search || '%' OR note LIKE '%' || :search || '%')
        AND (
            :statusFilter = 'TOUTES'
            OR (:statusFilter = 'PAYEE' AND montant_paye >= total AND total > 0)
            OR (:statusFilter = 'PARTIELLE' AND montant_paye > 0 AND montant_paye < total)
            OR (:statusFilter = 'IMPAYEE' AND montant_paye <= 0)
        )
    """)
    suspend fun countOrdersForSupplier(supplierId: Int, search: String, statusFilter: String): Int

    @Query("""
        SELECT poi.product_id AS product_id, SUM(poi.quantity) AS total_quantity
        FROM purchase_order_items poi
        INNER JOIN purchase_orders po ON po.id = poi.purchase_order_id
        WHERE po.supplier_id = :supplierId AND po.status = :receivedStatus
        GROUP BY poi.product_id
    """)
    suspend fun getPurchasedQuantitiesForSupplier(supplierId: Int, receivedStatus: String): List<ProductQuantitySum>
}