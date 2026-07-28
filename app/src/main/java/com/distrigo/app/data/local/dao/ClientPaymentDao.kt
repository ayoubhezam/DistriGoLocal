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

    @Query("SELECT * FROM client_payments WHERE created_at >= :start AND created_at < :end")
    suspend fun getPaymentsBetween(start: String, end: String): List<ClientPaymentEntity>

    // ── Historique paginé (Factures & Paiements — Client Detail) ──
    @Query("""
        SELECT * FROM client_payments
        WHERE client_id = :clientId
        AND (:cursorCreatedAt IS NULL OR created_at < :cursorCreatedAt)
        AND (:search = '' OR note LIKE '%' || :search || '%')
        ORDER BY created_at DESC, id DESC
        LIMIT :limit
    """)
    suspend fun pagePaymentsForClient(
        clientId: Int,
        cursorCreatedAt: String?,
        search: String,
        limit: Int
    ): List<ClientPaymentEntity>

    @Query("""
        SELECT COUNT(*) FROM client_payments
        WHERE client_id = :clientId
        AND (:search = '' OR note LIKE '%' || :search || '%')
    """)
    suspend fun countPaymentsForClient(clientId: Int, search: String): Int
}