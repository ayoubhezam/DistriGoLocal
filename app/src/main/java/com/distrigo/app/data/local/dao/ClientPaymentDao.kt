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

    /** The client's payments counted and summed: part of its detail screen's figures. */
    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(amount), 0.0) AS total FROM client_payments WHERE client_id = :clientId")
    suspend fun getPaymentTotalsForClient(clientId: Int): PaymentTotals

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
        AND (:cursorCreatedAt IS NULL OR created_at < :cursorCreatedAt
             OR (created_at = :cursorCreatedAt AND id < :cursorId))
        AND (:s1 = '' OR note LIKE '%' || :s1 || '%')
        AND (:s2 = '' OR note LIKE '%' || :s2 || '%')
        AND (:s3 = '' OR note LIKE '%' || :s3 || '%')
        ORDER BY created_at DESC, id DESC
        LIMIT :limit
    """)
    suspend fun pagePaymentsForClientBySlots(
        clientId: Int,
        cursorCreatedAt: String?,
        /** The id of the row [cursorCreatedAt] came from: rows sharing its instant are told apart by it. */
        cursorId: Int?,
        s1: String, s2: String, s3: String,
        limit: Int
    ): List<ClientPaymentEntity>

    /** [search] split into words, every one of which a row must contain — see searchSlots. */
    suspend fun pagePaymentsForClient(clientId: Int, cursorCreatedAt: String?, cursorId: Int?, search: String, limit: Int): List<ClientPaymentEntity> {
        val slots = searchSlots(search)
        return pagePaymentsForClientBySlots(clientId, cursorCreatedAt, cursorId, slots[0], slots[1], slots[2], limit)
    }

    @Query("""
        SELECT COUNT(*) FROM client_payments
        WHERE client_id = :clientId
        AND (:s1 = '' OR note LIKE '%' || :s1 || '%')
        AND (:s2 = '' OR note LIKE '%' || :s2 || '%')
        AND (:s3 = '' OR note LIKE '%' || :s3 || '%')
    """)
    suspend fun countPaymentsForClientBySlots(clientId: Int, s1: String, s2: String, s3: String): Int

    /** [search] split into words, every one of which a row must contain — see searchSlots. */
    suspend fun countPaymentsForClient(clientId: Int, search: String): Int {
        val slots = searchSlots(search)
        return countPaymentsForClientBySlots(clientId, slots[0], slots[1], slots[2])
    }
}