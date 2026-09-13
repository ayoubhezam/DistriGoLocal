package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.distrigo.app.data.local.entity.TourneeSecteurEntity

@Dao
interface TourneeSecteurDao {

    @Insert
    suspend fun insertAll(items: List<TourneeSecteurEntity>)

    @Query("SELECT * FROM tournee_secteurs WHERE tournee_id = :tourneeId ORDER BY order_index ASC")
    suspend fun getForTournee(tourneeId: Int): List<TourneeSecteurEntity>

    /**
     * Every tournée's secteurs in one query, for the list, grouped by tournée in the repository —
     * instead of one [getForTournee] per card. Ordered by (tournee_id, order_index), which is this
     * table's own index, so each tournée's secteurs keep the order they were picked in.
     */
    @Query("SELECT * FROM tournee_secteurs ORDER BY tournee_id ASC, order_index ASC")
    suspend fun getAll(): List<TourneeSecteurEntity>

    @Query("DELETE FROM tournee_secteurs WHERE tournee_id = :tourneeId")
    suspend fun deleteForTournee(tourneeId: Int)
}
