package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.distrigo.app.data.local.entity.SupplierEntity

@Dao
interface SupplierDao {

    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    suspend fun getAllSuppliers(): List<SupplierEntity>

    @Query("SELECT * FROM suppliers WHERE id = :supplierId")
    suspend fun getSupplierById(supplierId: Int): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: SupplierEntity): Long

    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)

    @Query("DELETE FROM suppliers WHERE id = :supplierId")
    suspend fun deleteSupplierById(supplierId: Int)
}
