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

    /**
     * A visited client goes back to "à visiter" once they have no sale left on the tournée — after
     * their last sale there was deleted. A client with another sale stays visited, and one never
     * marked visited is left as they are.
     */
    @Query("""
        UPDATE tournee_clients SET status = 'a_visiter', visited_at = NULL
        WHERE tournee_id = :tourneeId AND client_id = :clientId AND status = 'visite'
          AND NOT EXISTS (SELECT 1 FROM ventes WHERE tournee_id = :tourneeId AND client_id = :clientId)
    """)
    suspend fun unvisitIfNoSale(tourneeId: Int, clientId: Int)

    @Query("DELETE FROM tournee_clients WHERE tournee_id = :tourneeId AND client_id = :clientId")
    suspend fun remove(tourneeId: Int, clientId: Int)
}