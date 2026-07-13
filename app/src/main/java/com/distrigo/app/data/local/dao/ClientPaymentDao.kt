package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.distrigo.app.data.local.entity.ClientPaymentEntity

@Dao
interface ClientPaymentDao {

    @Insert
    suspend fun insertPayment(payment: ClientPaymentEntity): Long

    @Query("SELECT * FROM client_payments WHERE client_id = :clientId ORDER BY created_at DESC")
    suspend fun getPaymentsForClient(clientId: Int): List<ClientPaymentEntity>

    @Query("UPDATE client_payments SET amount = :amount WHERE id = :id")
    suspend fun updatePaymentAmount(id: Int, amount: Double)

    @Query("DELETE FROM client_payments WHERE id = :id")
    suspend fun deletePaymentById(id: Int)
}