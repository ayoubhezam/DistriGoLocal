package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.distrigo.app.data.local.entity.ClientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {

    @Query("SELECT * FROM clients ORDER BY id DESC")
    suspend fun getAllClients(): List<ClientEntity>

    // نسخة مُراقَبة: Room يعيد إصدار القائمة تلقائياً عند أي كتابة على جدول العملاء
    // (إضافة/تعديل/حذف عميل، تحديث الرصيد بعد بيع أو دفعة…) مهما كان مصدر الكتابة
    @Query("SELECT * FROM clients ORDER BY id DESC")
    fun observeAllClients(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getClientById(id: Int): ClientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: ClientEntity): Long

    @Update
    suspend fun updateClient(client: ClientEntity)

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun deleteClientById(id: Int)

    @Query("SELECT * FROM clients WHERE id IN (:ids)")
    suspend fun getClientsByIds(ids: List<Int>): List<ClientEntity>
}