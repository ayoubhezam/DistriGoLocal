package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.distrigo.app.data.local.entity.ClientEntity

@Dao
interface ClientDao {

    @Query("SELECT * FROM clients ORDER BY id DESC")
    suspend fun getAllClients(): List<ClientEntity>

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getClientById(id: Int): ClientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: ClientEntity): Long

    @Update
    suspend fun updateClient(client: ClientEntity)

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun deleteClientById(id: Int)
}