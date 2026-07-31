package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.distrigo.app.data.local.entity.MarqueEntity

@Dao
interface MarqueDao {

    @Query("SELECT * FROM marques ORDER BY sort_order ASC, name ASC")
    suspend fun getAllMarques(): List<MarqueEntity>

    @Query("SELECT * FROM marques WHERE id = :marqueId")
    suspend fun getMarqueById(marqueId: Int): MarqueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarque(marque: MarqueEntity): Long

    @Update
    suspend fun updateMarque(marque: MarqueEntity)

    @Query("DELETE FROM marques WHERE id = :marqueId")
    suspend fun deleteMarqueById(marqueId: Int)
}