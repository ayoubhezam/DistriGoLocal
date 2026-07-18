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
}