package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.distrigo.app.data.local.entity.RetourClientEntity
import com.distrigo.app.data.local.entity.RetourClientItemEntity

@Dao
interface RetourClientDao {

    @Insert
    suspend fun insertRetour(retour: RetourClientEntity): Long

    @Query("SELECT * FROM retour_client WHERE client_id = :clientId ORDER BY date DESC, id DESC")
    suspend fun getRetoursForClient(clientId: Int): List<RetourClientEntity>

    @Query("SELECT * FROM retour_client ORDER BY date DESC, id DESC")
    suspend fun getAllRetours(): List<RetourClientEntity>

    @Query("SELECT * FROM retour_client WHERE id = :id")
    suspend fun getRetourById(id: Int): RetourClientEntity?

    @Query("DELETE FROM retour_client WHERE id = :id")
    suspend fun deleteRetourById(id: Int)

    @Insert
    suspend fun insertItems(items: List<RetourClientItemEntity>)

    @Query("SELECT * FROM retour_client_items WHERE retour_id = :retourId")
    suspend fun getItemsForRetour(retourId: Int): List<RetourClientItemEntity>

    @Query("DELETE FROM retour_client_items WHERE retour_id = :retourId")
    suspend fun deleteItemsForRetour(retourId: Int)
}