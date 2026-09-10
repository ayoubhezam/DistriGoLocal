package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.distrigo.app.data.local.entity.ChargementDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargementDraftDao {

    // ── Reads ──
    /**
     * The Brouillons, and only those.
     *
     * `single_product_id IS NULL` is the filter that keeps the Modifier card's private editing
     * state out of the list, and it is repeated on the count below rather than factored out: the
     * two queries are what "invisible" actually means, so each one says so where it can be read.
     */
    @Query("SELECT * FROM chargement_drafts WHERE single_product_id IS NULL ORDER BY updated_at DESC")
    fun observeDrafts(): Flow<List<ChargementDraftEntity>>

    @Query("SELECT COUNT(*) FROM chargement_drafts WHERE single_product_id IS NULL")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM chargement_drafts WHERE id = :id")
    suspend fun getById(id: Int): ChargementDraftEntity?

    /**
     * The pending edit for one product, if any. At most one row can match — the unique index on
     * `single_product_id` guarantees it.
     */
    @Query("SELECT * FROM chargement_drafts WHERE single_product_id = :productId LIMIT 1")
    suspend fun getForProduct(productId: Int): ChargementDraftEntity?

    // ── Writes ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(draft: ChargementDraftEntity): Long

    @Update
    suspend fun update(draft: ChargementDraftEntity)

    @Query("DELETE FROM chargement_drafts WHERE id = :id")
    suspend fun deleteById(id: Int)

    /**
     * Bulk delete for the Brouillons screen's selection mode.
     *
     * One statement rather than a loop of [deleteById]: Room invalidates the table once, so the
     * list recomposes once instead of N times, and the whole selection goes or none of it does.
     */
    @Query("DELETE FROM chargement_drafts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    @Query("DELETE FROM chargement_drafts WHERE single_product_id = :productId")
    suspend fun deleteForProduct(productId: Int)
}
