package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.distrigo.app.data.model.NUMBER_LABEL_SQL
import com.distrigo.app.data.local.entity.PriceHistoryEntity
import com.distrigo.app.data.local.entity.PurchaseOrderEntity
import com.distrigo.app.data.local.entity.PurchaseOrderItemEntity
import com.distrigo.app.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow

/** The supplier's name as the Achats list shows it; the same expression as `PurchaseOrderListSql.SUPPLIER_NAME`. */
const val PURCHASE_LIST_SUPPLIER_NAME = "COALESCE(o.supplier_name, s.name, 'Fournisseur supprimé')"

/** One row of the Achats list: the bon, the name to show for its supplier, and how many lines it has. */
data class PurchaseOrderListRow(
    @Embedded val order: PurchaseOrderEntity,
    val display_supplier_name: String,
    val items_count: Int,
)

/** A choice in the Achats supplier filter. */
data class OrderSupplierChoice(val id: Int, val name: String)

@Dao
interface PurchaseDao {

    // ── Orders ──
    @Insert
    suspend fun insertOrder(order: PurchaseOrderEntity): Long

    /** Every bon. No screen reads this — the Achats list is paged, see [pageOrders] — but the database tests do. */
    @Query("SELECT * FROM purchase_orders ORDER BY id DESC")
    suspend fun getAllOrders(): List<PurchaseOrderEntity>

    /**
     * A page of the Achats list — see `PurchaseOrderListSql` for the SQL and why it is built per request.
     *
     * One query per page: the bon, its supplier's name and its line count come back together, where the
     * list used to load every bon and then run two more queries for each of them.
     */
    @RawQuery
    suspend fun pageOrders(query: SupportSQLiteQuery): List<PurchaseOrderListRow>

    /** How many bons a `PurchaseOrderListSql.count` query matches, re-counted whenever a bon changes. */
    @RawQuery(observedEntities = [PurchaseOrderEntity::class, SupplierEntity::class])
    fun observeOrderCount(query: SupportSQLiteQuery): Flow<Int>

    /**
     * Every supplier that has at least one bon, once, by the name its bons show: the Achats supplier
     * filter's choices. Read from the table, since the list no longer holds every bon.
     */
    @Query("""
        SELECT o.supplier_id AS id, MIN(${PURCHASE_LIST_SUPPLIER_NAME}) AS name
        FROM purchase_orders o LEFT JOIN suppliers s ON s.id = o.supplier_id
        GROUP BY o.supplier_id
        ORDER BY name
    """)
    fun observeOrderSuppliers(): Flow<List<OrderSupplierChoice>>

    /** One bon, live: the detail screen's subject, now that it is not taken from a loaded list. */
    @Query("SELECT * FROM purchase_orders WHERE id = :id")
    fun observeOrderById(id: Int): Flow<PurchaseOrderEntity?>

    /** The supplier's purchase orders counted, with totals and amounts paid summed: its detail screen's figures. */
    @Query("""
        SELECT COUNT(*) AS count, COALESCE(SUM(total), 0.0) AS total, COALESCE(SUM(montant_paye), 0.0) AS paid
        FROM purchase_orders
        WHERE supplier_id = :supplierId
    """)
    suspend fun getInvoiceTotalsForSupplier(supplierId: Int): InvoiceTotals

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
        AND (:search = '' OR ($NUMBER_LABEL_SQL) LIKE '%' || :search || '%' OR note LIKE '%' || :search || '%')
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
        AND (:search = '' OR ($NUMBER_LABEL_SQL) LIKE '%' || :search || '%' OR note LIKE '%' || :search || '%')
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

    /** The number printed on it, or null if it has none — see DocumentNumberTriggers. */
    @Query("SELECT numero FROM purchase_orders WHERE id = :id")
    suspend fun getNumero(id: Int): String?
}