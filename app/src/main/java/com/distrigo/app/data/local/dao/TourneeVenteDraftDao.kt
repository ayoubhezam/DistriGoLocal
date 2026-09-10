package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.distrigo.app.data.local.entity.TourneeVenteDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TourneeVenteDraftDao {

    // ── Reads ──
    /**
     * Scoped to one tournée, unlike the other two draft DAOs' `observeAll`. A van sale belongs to
     * the round it was made on, so "the drafts" is always the drafts of a particular tournée —
     * there is no screen anywhere that wants them all mixed together.
     */
    @Query("SELECT * FROM tournee_vente_drafts WHERE tournee_id = :tourneeId ORDER BY updated_at DESC")
    fun observeForTournee(tourneeId: Int): Flow<List<TourneeVenteDraftEntity>>

    @Query("SELECT COUNT(*) FROM tournee_vente_drafts WHERE tournee_id = :tourneeId")
    fun observeCountForTournee(tourneeId: Int): Flow<Int>

    @Query("SELECT * FROM tournee_vente_drafts WHERE id = :id")
    suspend fun getById(id: Int): TourneeVenteDraftEntity?

    // ── Writes ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(draft: TourneeVenteDraftEntity): Long

    @Update
    suspend fun update(draft: TourneeVenteDraftEntity)

    @Query("DELETE FROM tournee_vente_drafts WHERE id = :id")
    suspend fun deleteById(id: Int)

    /**
     * Bulk delete for the Brouillons screen's selection mode.
     *
     * One statement rather than a loop of [deleteById]: Room invalidates the table once, so the
     * list recomposes once instead of N times, and the whole selection goes or none of it does.
     */
    @Query("DELETE FROM tournee_vente_drafts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    /**
     * Every draft of a tournée, dropped at once.
     *
     * Deleting a tournée leaves its unfinished sales with nothing to belong to — there is no
     * foreign key to cascade, so this is what stops them becoming rows no screen can ever reach.
     */
    @Query("DELETE FROM tournee_vente_drafts WHERE tournee_id = :tourneeId")
    suspend fun deleteForTournee(tourneeId: Int)
}
