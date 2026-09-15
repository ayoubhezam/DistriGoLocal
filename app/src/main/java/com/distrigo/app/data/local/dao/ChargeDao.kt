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

    /** How many subtypes each type has. Types without subtypes have no row. */
    @Query("SELECT type_id, COUNT(*) AS count FROM charge_subtypes GROUP BY type_id")
    suspend fun getSubTypeCountsByType(): List<ChargeSubTypeCount>

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

    /**
     * Each type's total for [month] ("yyyy-MM"), by the charge's own type_id. A charge belongs to
     * the month when the first seven characters of its date_time equal [month] — the same test as
     * `date_time.take(7) == month`. Types without a charge that month have no row.
     */
    @Query("""
        SELECT type_id, SUM(montant) AS total
        FROM charges
        WHERE substr(date_time, 1, 7) = :month
        GROUP BY type_id
    """)
    suspend fun getMonthTotalsByType(month: String): List<ChargeTypeMonthTotal>

    /**
     * The count and total for [month] of each subtype of type [typeId], by the charge's subtype_id,
     * with the same month test as [getMonthTotalsByType]. Subtypes without a charge that month have
     * no row.
     */
    @Query("""
        SELECT subtype_id, COUNT(*) AS count, SUM(montant) AS total
        FROM charges
        WHERE subtype_id IN (SELECT id FROM charge_subtypes WHERE type_id = :typeId)
          AND substr(date_time, 1, 7) = :month
        GROUP BY subtype_id
    """)
    suspend fun getMonthStatsBySubType(typeId: Int, month: String): List<ChargeSubTypeMonthStats>

    @Query("SELECT * FROM charges WHERE id = :id")
    suspend fun getChargeById(id: Int): ChargeEntity?

    @Insert
    suspend fun insertCharge(charge: ChargeEntity): Long

    @Update
    suspend fun updateCharge(charge: ChargeEntity)

    @Query("DELETE FROM charges WHERE id = :id")
    suspend fun deleteChargeById(id: Int)
}

/** A charge type's number of subtypes, from [ChargeDao.getSubTypeCountsByType]. */
data class ChargeSubTypeCount(val type_id: Int, val count: Int)

/** A charge type's total for a month, from [ChargeDao.getMonthTotalsByType]. */
data class ChargeTypeMonthTotal(val type_id: Int, val total: Double)

/** A charge subtype's count and total for a month, from [ChargeDao.getMonthStatsBySubType]. */
data class ChargeSubTypeMonthStats(val subtype_id: Int, val count: Int, val total: Double)