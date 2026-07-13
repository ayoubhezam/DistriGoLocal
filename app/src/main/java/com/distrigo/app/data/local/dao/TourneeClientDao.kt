package com.distrigo.app.data.local.dao

import androidx.room.*
import com.distrigo.app.data.local.entity.TourneeClientEntity

@Dao
interface TourneeClientDao {

    @Insert
    suspend fun insertAll(items: List<TourneeClientEntity>)

    @Query("SELECT * FROM tournee_clients WHERE tournee_id = :tourneeId ORDER BY order_index ASC")
    suspend fun getForTournee(tourneeId: Int): List<TourneeClientEntity>

    @Query("SELECT client_id FROM tournee_clients WHERE tournee_id = :tourneeId")
    suspend fun getClientIdsForTournee(tourneeId: Int): List<Int>

    @Query("UPDATE tournee_clients SET status = :status, visited_at = :visitedAt WHERE tournee_id = :tourneeId AND client_id = :clientId")
    suspend fun updateStatus(tourneeId: Int, clientId: Int, status: String, visitedAt: String?)

    @Query("UPDATE tournee_clients SET status = 'a_visiter' WHERE tournee_id = :tourneeId AND status = 'en_cours'")
    suspend fun clearCurrent(tourneeId: Int)

    @Query("DELETE FROM tournee_clients WHERE tournee_id = :tourneeId AND client_id = :clientId")
    suspend fun remove(tourneeId: Int, clientId: Int)
}