package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.distrigo.app.data.local.entity.InventoryItemEntity
import com.distrigo.app.data.local.entity.InventorySessionEntity

@Dao
interface InventoryDao {

    // ── Sessions ──
    @Query("SELECT * FROM inventory_sessions WHERE status = 'draft' LIMIT 1")
    suspend fun getActiveSession(): InventorySessionEntity?

    @Query("SELECT * FROM inventory_sessions WHERE id = :id")
    suspend fun getSessionById(id: Int): InventorySessionEntity?

    @Query("SELECT * FROM inventory_sessions ORDER BY started_at DESC")
    suspend fun getAllSessions(): List<InventorySessionEntity>

    @Insert
    suspend fun insertSession(session: InventorySessionEntity): Long

    @Update
    suspend fun updateSession(session: InventorySessionEntity)

    // ── Items ──
    @Query("SELECT * FROM inventory_items WHERE session_id = :sessionId ORDER BY created_at DESC")
    suspend fun getItemsForSession(sessionId: Int): List<InventoryItemEntity>

    /**
     * Each session's item count, number of items with a non-zero écart, and sum of the absolute
     * écart values. Sessions without items have no row.
     */
    @Query("""
        SELECT session_id,
               COUNT(*) AS total_products,
               SUM(CASE WHEN ecart != 0 THEN 1 ELSE 0 END) AS total_ecarts,
               SUM(ABS(valeur_ecart)) AS total_value_ecarts
        FROM inventory_items
        GROUP BY session_id
    """)
    suspend fun getSessionTotals(): List<InventorySessionTotals>

    @Query("SELECT * FROM inventory_items WHERE session_id = :sessionId AND product_id = :productId LIMIT 1")
    suspend fun getItemForSessionAndProduct(sessionId: Int, productId: Int): InventoryItemEntity?

    @Insert
    suspend fun insertItem(item: InventoryItemEntity): Long

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    suspend fun getItemById(id: Int): InventoryItemEntity?

    @Update
    suspend fun updateItem(item: InventoryItemEntity)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteItem(id: Int)
}

/** One inventory session's totals, from [InventoryDao.getSessionTotals]. */
data class InventorySessionTotals(
    val session_id: Int,
    val total_products: Int,
    val total_ecarts: Int,
    val total_value_ecarts: Double
)