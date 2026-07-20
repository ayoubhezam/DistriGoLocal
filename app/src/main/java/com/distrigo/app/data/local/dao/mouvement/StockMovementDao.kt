package com.distrigo.app.data.local.dao.mouvement

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.distrigo.app.data.local.entity.mouvement.StockMovementEntity

@Dao
interface StockMovementDao {

    @Insert
    suspend fun insert(movement: StockMovementEntity): Long

    @Insert
    suspend fun insertAll(movements: List<StockMovementEntity>)

    @Query("SELECT * FROM stock_movements WHERE product_id = :productId ORDER BY created_at DESC")
    suspend fun getMovementsForProduct(productId: Int): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements ORDER BY created_at DESC")
    suspend fun getAllMovements(): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements WHERE id = :id")
    suspend fun getMovementById(id: Int): StockMovementEntity?

    @Query("DELETE FROM stock_movements WHERE source_type = :sourceType AND source_id = :sourceId")
    suspend fun deleteBySource(sourceType: String, sourceId: Int)

    // ── Filtrage combiné : produit + période + type + source_label ──
    // NULL dans un paramètre = filtre ignoré (voir logique CASE ci-dessous)
    @Query("""
        SELECT * FROM stock_movements
        WHERE (:productId IS NULL OR product_id = :productId)
          AND (:dateFrom IS NULL OR created_at >= :dateFrom)
          AND (:dateTo IS NULL OR created_at <= :dateTo)
          AND (:direction IS NULL OR direction = :direction)
          AND (:sourceLabel IS NULL OR source_label = :sourceLabel)
        ORDER BY created_at DESC
    """)
    suspend fun getFilteredMovements(
        productId: Int?,
        dateFrom: String?,
        dateTo: String?,
        direction: String?,
        sourceLabel: String?
    ): List<StockMovementEntity>

    @Query("SELECT DISTINCT source_label FROM stock_movements WHERE product_id = :productId ORDER BY source_label ASC")
    suspend fun getDistinctSourcesForProduct(productId: Int): List<String>
}