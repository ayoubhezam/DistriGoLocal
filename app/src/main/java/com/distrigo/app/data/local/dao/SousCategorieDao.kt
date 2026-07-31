package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.distrigo.app.data.local.entity.SousCategorieEntity

@Dao
interface SousCategorieDao {

    @Query("SELECT * FROM sous_categories ORDER BY sort_order ASC, name ASC")
    suspend fun getAllSousCategories(): List<SousCategorieEntity>

    @Query("SELECT * FROM sous_categories WHERE category_id = :categoryId ORDER BY sort_order ASC, name ASC")
    suspend fun getSousCategoriesForCategory(categoryId: Int): List<SousCategorieEntity>

    @Query("SELECT * FROM sous_categories WHERE id = :id")
    suspend fun getSousCategorieById(id: Int): SousCategorieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSousCategorie(sousCategorie: SousCategorieEntity): Long

    @Update
    suspend fun updateSousCategorie(sousCategorie: SousCategorieEntity)

    @Query("DELETE FROM sous_categories WHERE id = :id")
    suspend fun deleteSousCategorieById(id: Int)
}