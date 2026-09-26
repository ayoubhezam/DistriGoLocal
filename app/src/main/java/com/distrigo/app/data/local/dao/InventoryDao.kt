package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import com.distrigo.app.data.local.entity.InventoryItemEntity
import com.distrigo.app.data.local.entity.InventorySessionEntity
import com.distrigo.app.data.model.InventorySession
import com.distrigo.app.data.model.InventorySessionHistory
import com.distrigo.app.data.model.InventorySessionSummary
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
interface InventoryDao {

    // ── Sessions ──
    @Query("SELECT * FROM inventory_sessions WHERE status = 'draft' LIMIT 1")
    suspend fun getActiveSession(): InventorySessionEntity?

    @Query("SELECT * FROM inventory_sessions WHERE id = :id")
    suspend fun getSessionById(id: Int): InventorySessionEntity?

    /** A page of the history, each session with its totals — see `InventoryListSql.sessionPage`. */
    @RawQuery
    suspend fun pageSessions(query: SupportSQLiteQuery): List<InventorySessionListRow>

    @Insert
    suspend fun insertSession(session: InventorySessionEntity): Long

    @Update
    suspend fun updateSession(session: InventorySessionEntity)

    // ── Items ──
    @Query("SELECT * FROM inventory_items WHERE session_id = :sessionId ORDER BY created_at DESC")
    suspend fun getItemsForSession(sessionId: Int): List<InventoryItemEntity>

    /**
     * A session's figures — lines counted, lines with an écart, the écarts' value — in one pass over
     * its lines. Read when a count opens and when it is finished; between the two, the count keeps
     * them up to date line by line (see InventoryViewModel.counts) rather than re-summing each scan.
     */
    @Query("""
        SELECT COUNT(*) AS total_products,
               COALESCE(SUM(CASE WHEN ecart != 0 THEN 1 ELSE 0 END), 0) AS total_ecarts,
               COALESCE(SUM(ABS(valeur_ecart)), 0.0) AS total_value_ecarts
        FROM inventory_items WHERE session_id = :sessionId
    """)
    suspend fun getSessionSummary(sessionId: Int): InventorySessionSummary

    /** A page of one session's lines — see `InventoryListSql.itemPage`. */
    @RawQuery
    suspend fun pageItems(query: SupportSQLiteQuery): List<InventoryItemEntity>

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

/** A row of the inventory history: a session, its date as the list sorts it, and its totals. */
data class InventorySessionListRow(
    val id: Int,
    val status: String,
    val started_at: String,
    val completed_at: String?,
    val sort_at: String,
    val total_products: Int,
    val total_ecarts: Int,
    val total_value_ecarts: Double
) {
    fun toHistory() = InventorySessionHistory(
        session = InventorySession(id = id, status = status, started_at = started_at, completed_at = completed_at),
        summary = InventorySessionSummary(total_products, total_ecarts, total_value_ecarts)
    )
}