package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.distrigo.app.data.local.entity.RetourFournisseurEntity
import com.distrigo.app.data.local.entity.RetourFournisseurItemEntity

@Dao
interface RetourFournisseurDao {

    @Insert
    suspend fun insertRetour(retour: RetourFournisseurEntity): Long

    @Query("SELECT * FROM retour_fournisseur WHERE supplier_id = :supplierId ORDER BY date DESC, id DESC")
    suspend fun getRetoursForSupplier(supplierId: Int): List<RetourFournisseurEntity>

    @Query("SELECT * FROM retour_fournisseur ORDER BY date DESC, id DESC")
    suspend fun getAllRetours(): List<RetourFournisseurEntity>

    @Query("SELECT * FROM retour_fournisseur WHERE id = :id")
    suspend fun getRetourById(id: Int): RetourFournisseurEntity?

    @Query("DELETE FROM retour_fournisseur WHERE id = :id")
    suspend fun deleteRetourById(id: Int)

    @Insert
    suspend fun insertItems(items: List<RetourFournisseurItemEntity>)

    @Query("SELECT * FROM retour_fournisseur_items WHERE retour_id = :retourId")
    suspend fun getItemsForRetour(retourId: Int): List<RetourFournisseurItemEntity>

    @Query("DELETE FROM retour_fournisseur_items WHERE retour_id = :retourId")
    suspend fun deleteItemsForRetour(retourId: Int)
}