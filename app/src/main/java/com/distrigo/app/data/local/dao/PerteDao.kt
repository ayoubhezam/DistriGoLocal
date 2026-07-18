package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.distrigo.app.data.local.entity.PerteEntity
import com.distrigo.app.data.local.entity.PerteTypeEntity

@Dao
interface PerteDao {

    // ── Perte Types ──
    @Query("SELECT * FROM perte_types ORDER BY id ASC")
    suspend fun getAllPerteTypes(): List<PerteTypeEntity>

    @Query("SELECT * FROM perte_types WHERE id = :id")
    suspend fun getPerteTypeById(id: Int): PerteTypeEntity?

    @Insert
    suspend fun insertPerteType(type: PerteTypeEntity): Long

    @Query("DELETE FROM perte_types WHERE id = :id")
    suspend fun deletePerteTypeById(id: Int)

    // ── Pertes ──
    @Query("SELECT * FROM pertes ORDER BY date_time DESC")
    suspend fun getAllPertes(): List<PerteEntity>

    @Query("SELECT * FROM pertes WHERE type_id = :typeId ORDER BY date_time DESC")
    suspend fun getPertesForType(typeId: Int): List<PerteEntity>

    @Query("SELECT * FROM pertes WHERE id = :id")
    suspend fun getPerteById(id: Int): PerteEntity?

    @Insert
    suspend fun insertPerte(perte: PerteEntity): Long

    @Query("DELETE FROM pertes WHERE id = :id")
    suspend fun deletePerteById(id: Int)

    @Update
    suspend fun updatePerte(perte: PerteEntity)
}