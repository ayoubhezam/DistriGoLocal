package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.distrigo.app.data.local.entity.TourneeEntity

@Dao
interface TourneeDao {

    @Insert
    suspend fun insertTournee(tournee: TourneeEntity): Long

    @Query("SELECT * FROM tournees ORDER BY id DESC")
    suspend fun getAllTournees(): List<TourneeEntity>

    @Query("SELECT * FROM tournees WHERE id = :id")
    suspend fun getTourneeById(id: Int): TourneeEntity?

    @Query("SELECT * FROM tournees WHERE status = 'ouverte' LIMIT 1")
    suspend fun getOpenTournee(): TourneeEntity?

    @Query("UPDATE tournees SET status = :status, date_fin = :dateFin WHERE id = :id")
    suspend fun updateTourneeStatus(id: Int, status: String, dateFin: String?)

    @Query("""
        UPDATE tournees
        SET nom = :nom, wilaya_name = :wilayaName, commune_name = :communeName,
            secteur_id = :secteurId, secteur_name = :secteurName, note = :note
        WHERE id = :id
    """)
    suspend fun updateTourneeFields(
        id: Int, nom: String, wilayaName: String?, communeName: String?,
        secteurId: Int?, secteurName: String?, note: String?
    )

    @Query("DELETE FROM tournees WHERE id = :id")
    suspend fun deleteTourneeById(id: Int)
}