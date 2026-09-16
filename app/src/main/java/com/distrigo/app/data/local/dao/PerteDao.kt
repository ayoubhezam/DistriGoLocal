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

    /**
     * Each type's count and totals within [start, end) — one month, as `monthRange` builds it.
     * Types without a perte in that range have no row.
     *
     * A half-open range rather than `substr(date_time, 1, 7) = :month`, which wrapped the column in
     * a function so `index_pertes_type_id_date_time` could not serve it. The same rows match either
     * way, because date_time is ISO-8601 and so sorts as text in date order.
     */
    @Query("""
        SELECT type_id, COUNT(*) AS count, SUM(valeur_totale) AS total_value, SUM(quantity) AS total_qty
        FROM pertes
        WHERE date_time >= :start AND date_time < :end
        GROUP BY type_id
    """)
    suspend fun getMonthStatsByType(start: String, end: String): List<PerteTypeMonthStats>

    @Query("SELECT * FROM pertes WHERE type_id = :typeId ORDER BY date_time DESC")
    suspend fun getPertesForType(typeId: Int): List<PerteEntity>

    /**
     * One type's pertes within [start, end) — the month its list screen shows. The month used to be
     * filtered in Kotlin after loading that type's whole history.
     */
    @Query("""
        SELECT * FROM pertes
        WHERE type_id = :typeId AND date_time >= :start AND date_time < :end
        ORDER BY date_time DESC
    """)
    suspend fun getPertesForTypeInRange(typeId: Int, start: String, end: String): List<PerteEntity>

    @Query("SELECT * FROM pertes WHERE id = :id")
    suspend fun getPerteById(id: Int): PerteEntity?

    @Query("SELECT * FROM pertes WHERE source_type = :sourceType AND source_id = :sourceId")
    suspend fun getPertesBySource(sourceType: String, sourceId: Int): List<PerteEntity>

    @Insert
    suspend fun insertPerte(perte: PerteEntity): Long

    @Query("DELETE FROM pertes WHERE id = :id")
    suspend fun deletePerteById(id: Int)

    @Update
    suspend fun updatePerte(perte: PerteEntity)
}

/** One perte type's figures for a month, from [PerteDao.getMonthStatsByType]. */
data class PerteTypeMonthStats(
    val type_id: Int,
    val count: Int,
    val total_value: Double,
    val total_qty: Double
)