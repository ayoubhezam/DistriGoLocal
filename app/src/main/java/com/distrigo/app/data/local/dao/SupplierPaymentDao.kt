package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.distrigo.app.data.local.entity.SupplierPaymentEntity

@Dao
interface SupplierPaymentDao {

    @Insert
    suspend fun insertPayment(payment: SupplierPaymentEntity): Long

    @Query("SELECT * FROM supplier_payments WHERE supplier_id = :supplierId ORDER BY created_at DESC")
    suspend fun getPaymentsForSupplier(supplierId: Int): List<SupplierPaymentEntity>

    @Query("SELECT * FROM supplier_payments WHERE id = :id")
    suspend fun getPaymentById(id: Int): SupplierPaymentEntity?

    @Query("UPDATE supplier_payments SET amount = :amount WHERE id = :id")
    suspend fun updatePaymentAmount(id: Int, amount: Double)

    @Query("DELETE FROM supplier_payments WHERE id = :id")
    suspend fun deletePaymentById(id: Int)
}