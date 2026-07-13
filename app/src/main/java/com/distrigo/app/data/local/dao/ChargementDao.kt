package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import com.distrigo.app.data.local.entity.ChargementEntity
import com.distrigo.app.data.local.entity.ChargementItemEntity
import com.distrigo.app.data.local.entity.ChargementSessionEntity

// ── نماذج علاقات Room (Session ← Chargements ← Items) ──
data class ChargementWithItems(
    @Embedded val chargement: ChargementEntity,
    @Relation(parentColumn = "id", entityColumn = "chargement_id")
    val items: List<ChargementItemEntity>
)

data class SessionWithChargements(
    @Embedded val session: ChargementSessionEntity,
    @Relation(entity = ChargementEntity::class, parentColumn = "id", entityColumn = "session_id")
    val chargements: List<ChargementWithItems>
)

@Dao
interface ChargementDao {

    // ── Sessions ──
    @Query("SELECT * FROM chargement_sessions WHERE session_date = :date LIMIT 1")
    suspend fun getSessionByDate(date: String): ChargementSessionEntity?

    @Insert
    suspend fun insertSession(session: ChargementSessionEntity): Long

    @Query("UPDATE chargement_sessions SET note = :note WHERE id = :sessionId")
    suspend fun updateSessionNote(sessionId: Int, note: String?)

    @Query("SELECT * FROM chargement_sessions ORDER BY session_date DESC")
    suspend fun getAllSessions(): List<ChargementSessionEntity>

    @Query("SELECT * FROM chargement_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Int): ChargementSessionEntity?

    // ── Chargements ──
    @Insert
    suspend fun insertChargement(chargement: ChargementEntity): Long

    @Query("SELECT * FROM chargements WHERE session_id = :sessionId ORDER BY id DESC")
    suspend fun getChargementsBySession(sessionId: Int): List<ChargementEntity>

    @Query("SELECT * FROM chargements ORDER BY id DESC")
    suspend fun getAllChargements(): List<ChargementEntity>

    @Query("SELECT * FROM chargements WHERE id = :id")
    suspend fun getChargementById(id: Int): ChargementEntity?

    @Query("DELETE FROM chargements WHERE id = :id")
    suspend fun deleteChargementById(id: Int)

    // ── Items ──
    @Insert
    suspend fun insertItems(items: List<ChargementItemEntity>)

    @Query("SELECT * FROM chargement_items WHERE chargement_id = :chargementId")
    suspend fun getItemsForChargement(chargementId: Int): List<ChargementItemEntity>

    @Query("DELETE FROM chargement_items WHERE chargement_id = :chargementId")
    suspend fun deleteItemsForChargement(chargementId: Int)
}