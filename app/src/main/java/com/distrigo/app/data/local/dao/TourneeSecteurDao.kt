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

    @Query("DELETE FROM tournee_secteurs WHERE tournee_id = :tourneeId")
    suspend fun deleteForTournee(tourneeId: Int)
}
