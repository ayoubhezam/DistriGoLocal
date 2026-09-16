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
    @Query("SELECT * FROM charge_types WHERE deleted_at IS NULL ORDER BY id ASC")
    suspend fun getAllChargeTypes(): List<ChargeTypeEntity>

    @Query("SELECT * FROM charge_types WHERE id = :id AND deleted_at IS NULL")
    suspend fun getChargeTypeById(id: Int): ChargeTypeEntity?

    @Insert
    suspend fun insertChargeType(type: ChargeTypeEntity): Long

    @Update
    suspend fun updateChargeType(type: ChargeTypeEntity)

    @Query("UPDATE charge_types SET deleted_at = CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER) WHERE id = :id AND deleted_at IS NULL")
    suspend fun softDeleteChargeTypeById(id: Int)

    // ── Charge SubTypes ──
    @Query("SELECT * FROM charge_subtypes WHERE type_id = :typeId AND deleted_at IS NULL ORDER BY id ASC")
    suspend fun getSubTypesForType(typeId: Int): List<ChargeSubTypeEntity>

    @Query("SELECT * FROM charge_subtypes WHERE id = :id AND deleted_at IS NULL")
    suspend fun getSubTypeById(id: Int): ChargeSubTypeEntity?

    @Query("SELECT * FROM charge_subtypes WHERE deleted_at IS NULL ORDER BY id ASC")
    suspend fun getAllSubTypes(): List<ChargeSubTypeEntity>

    /** How many subtypes each type has. Types without subtypes have no row. */
    @Query("SELECT type_id, COUNT(*) AS count FROM charge_subtypes WHERE deleted_at IS NULL GROUP BY type_id")
    suspend fun getSubTypeCountsByType(): List<ChargeSubTypeCount>

    @Insert
    suspend fun insertSubType(subType: ChargeSubTypeEntity): Long

    @Update
    suspend fun updateSubType(subType: ChargeSubTypeEntity)

    @Query("UPDATE charge_subtypes SET deleted_at = CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER) WHERE id = :id AND deleted_at IS NULL")
    suspend fun softDeleteSubTypeById(id: Int)

    // ── Charges ──

    @Query("SELECT * FROM charges WHERE subtype_id = :subtypeId ORDER BY date_time DESC")
    suspend fun getChargesForSubType(subtypeId: Int): List<ChargeEntity>

    /**
     * Each type's total within [start, end) — one month, as `monthRange` builds it — by the
     * charge's own type_id. Types without a charge in that range have no row.
     *
     * A half-open range rather than `substr(date_time, 1, 7) = :month`, which wrapped the column in
     * a function so no index could serve it. The same rows match either way, because date_time is
     * ISO-8601 and so sorts as text in date order.
     */
    @Query("""
        SELECT type_id, SUM(montant) AS total
        FROM charges
        WHERE date_time >= :start AND date_time < :end
        GROUP BY type_id
    """)
    suspend fun getMonthTotalsByType(start: String, end: String): List<ChargeTypeMonthTotal>

    /**
     * The count and total within [start, end) of each subtype of type [typeId], by the charge's
     * subtype_id, with the same range test as [getMonthTotalsByType]. Subtypes without a charge in
     * that range have no row.
     */
    @Query("""
        SELECT subtype_id, COUNT(*) AS count, SUM(montant) AS total
        FROM charges
        WHERE subtype_id IN (SELECT id FROM charge_subtypes WHERE type_id = :typeId AND deleted_at IS NULL)
          AND date_time >= :start AND date_time < :end
        GROUP BY subtype_id
    """)
    suspend fun getMonthStatsBySubType(typeId: Int, start: String, end: String): List<ChargeSubTypeMonthStats>

    /**
     * One subtype's charges within [start, end) — the month its list screen shows. The month used
     * to be filtered in Kotlin after loading that subtype's whole history.
     */
    @Query("""
        SELECT * FROM charges
        WHERE subtype_id = :subtypeId AND date_time >= :start AND date_time < :end
        ORDER BY date_time DESC
    """)
    suspend fun getChargesForSubTypeInRange(subtypeId: Int, start: String, end: String): List<ChargeEntity>

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