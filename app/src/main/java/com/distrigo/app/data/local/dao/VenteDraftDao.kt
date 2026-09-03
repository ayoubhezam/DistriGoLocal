package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.distrigo.app.data.local.entity.VenteDraftEntity
import kotlinx.coroutines.flow.Flow

/** `id` projection for the drafts list — see [VenteDraftDao.existingVenteIds]. */
data class DraftVenteId(val id: Int)

@Dao
interface VenteDraftDao {

    // ── Reads ──
    @Query("SELECT * FROM vente_drafts ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<VenteDraftEntity>>

    @Query("SELECT COUNT(*) FROM vente_drafts")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vente_drafts")
    suspend fun count(): Int

    @Query("SELECT * FROM vente_drafts WHERE id = :id")
    suspend fun getById(id: Int): VenteDraftEntity?

    /** At most one row can match — the unique index on `source_vente_id` guarantees it. */
    @Query("SELECT * FROM vente_drafts WHERE source_vente_id = :venteId LIMIT 1")
    suspend fun getForVente(venteId: Int): VenteDraftEntity?

    // ── Writes ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(draft: VenteDraftEntity): Long

    @Update
    suspend fun update(draft: VenteDraftEntity)

    @Query("DELETE FROM vente_drafts WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM vente_drafts WHERE source_vente_id = :venteId")
    suspend fun deleteForVente(venteId: Int)

    /**
     * Which of these ventes still exist, so the drafts list can badge an edit draft whose vente has
     * since been deleted. One statement for the whole list, not one per row.
     *
     * Achats asks for the *status* here, because a received bon is a second, separate block. Dépôt
     * Vente has no equivalent: `ProductRepository.updateVente` reverses every old line before
     * applying the new ones, so a delivered vente is still safely editable and the only thing that
     * can stop a draft being applied is the vente being gone. Existence is the whole question.
     */
    @Query("SELECT id FROM ventes WHERE id IN (:venteIds)")
    suspend fun existingVenteIds(venteIds: List<Int>): List<DraftVenteId>
}
