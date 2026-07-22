package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.distrigo.app.data.local.entity.SecteurEntity

@Dao
interface SecteurDao {

    @Insert
    suspend fun insertSecteur(secteur: SecteurEntity): Long

    @Query("SELECT * FROM secteurs WHERE commune_name = :communeName ORDER BY nom ASC")
    suspend fun getSecteursForCommune(communeName: String): List<SecteurEntity>
}