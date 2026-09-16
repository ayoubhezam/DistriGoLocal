package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.distrigo.app.data.local.entity.MarqueEntity

@Dao
interface MarqueDao {

    @Query("SELECT * FROM marques WHERE deleted_at IS NULL ORDER BY sort_order ASC, name ASC")
    suspend fun getAllMarques(): List<MarqueEntity>

    @Query("SELECT * FROM marques WHERE id = :marqueId AND deleted_at IS NULL")
    suspend fun getMarqueById(marqueId: Int): MarqueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarque(marque: MarqueEntity): Long

    @Update
    suspend fun updateMarque(marque: MarqueEntity)

    @Query("UPDATE marques SET deleted_at = CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER) WHERE id = :marqueId AND deleted_at IS NULL")
    suspend fun softDeleteMarqueById(marqueId: Int)
}