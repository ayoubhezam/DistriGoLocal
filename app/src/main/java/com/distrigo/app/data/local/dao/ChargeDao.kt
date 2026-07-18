package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.distrigo.app.data.local.entity.ChargeEntity
import com.distrigo.app.data.local.entity.ChargeSubTypeEntity
import com.distrigo.app.data.local.entity.ChargeTypeEntity

@Dao
interface ChargeDao {

    // ── Charge Types ──
    @Query("SELECT * FROM charge_types ORDER BY id ASC")
    suspend fun getAllChargeTypes(): List<ChargeTypeEntity>

    @Query("SELECT * FROM charge_types WHERE id = :id")
    suspend fun getChargeTypeById(id: Int): ChargeTypeEntity?

    @Insert
    suspend fun insertChargeType(type: ChargeTypeEntity): Long

    @Update
    suspend fun updateChargeType(type: ChargeTypeEntity)

    @Query("DELETE FROM charge_types WHERE id = :id")
    suspend fun deleteChargeTypeById(id: Int)

    // ── Charge SubTypes ──
    @Query("SELECT * FROM charge_subtypes WHERE type_id = :typeId ORDER BY id ASC")
    suspend fun getSubTypesForType(typeId: Int): List<ChargeSubTypeEntity>

    @Query("SELECT * FROM charge_subtypes WHERE id = :id")
    suspend fun getSubTypeById(id: Int): ChargeSubTypeEntity?

    @Query("SELECT * FROM charge_subtypes ORDER BY id ASC")
    suspend fun getAllSubTypes(): List<ChargeSubTypeEntity>

    @Insert
    suspend fun insertSubType(subType: ChargeSubTypeEntity): Long

    @Update
    suspend fun updateSubType(subType: ChargeSubTypeEntity)

    @Query("DELETE FROM charge_subtypes WHERE id = :id")
    suspend fun deleteSubTypeById(id: Int)

    // ── Charges ──
    @Query("SELECT * FROM charges ORDER BY date_time DESC")
    suspend fun getAllCharges(): List<ChargeEntity>

    @Query("SELECT * FROM charges WHERE subtype_id = :subtypeId ORDER BY date_time DESC")
    suspend fun getChargesForSubType(subtypeId: Int): List<ChargeEntity>

    @Query("SELECT * FROM charges WHERE id = :id")
    suspend fun getChargeById(id: Int): ChargeEntity?

    @Insert
    suspend fun insertCharge(charge: ChargeEntity): Long

    @Update
    suspend fun updateCharge(charge: ChargeEntity)

    @Query("DELETE FROM charges WHERE id = :id")
    suspend fun deleteChargeById(id: Int)
}