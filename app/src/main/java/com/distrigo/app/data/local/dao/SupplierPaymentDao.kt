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

    /** The supplier's payments counted and summed: part of its detail screen's figures. */
    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(amount), 0.0) AS total FROM supplier_payments WHERE supplier_id = :supplierId")
    suspend fun getPaymentTotalsForSupplier(supplierId: Int): PaymentTotals

    @Query("SELECT * FROM supplier_payments WHERE id = :id")
    suspend fun getPaymentById(id: Int): SupplierPaymentEntity?

    @Query("UPDATE supplier_payments SET amount = :amount WHERE id = :id")
    suspend fun updatePaymentAmount(id: Int, amount: Double)

    @Query("DELETE FROM supplier_payments WHERE id = :id")
    suspend fun deletePaymentById(id: Int)

    // ── Historique paginé (Achats & Paiements — Supplier Detail) ──
    @Query("""
        SELECT * FROM supplier_payments
        WHERE supplier_id = :supplierId
        AND (:cursorCreatedAt IS NULL OR created_at < :cursorCreatedAt
             OR (created_at = :cursorCreatedAt AND id < :cursorId))
        AND (:s1 = '' OR note LIKE '%' || :s1 || '%')
        AND (:s2 = '' OR note LIKE '%' || :s2 || '%')
        AND (:s3 = '' OR note LIKE '%' || :s3 || '%')
        ORDER BY created_at DESC, id DESC
        LIMIT :limit
    """)
    suspend fun pagePaymentsForSupplierBySlots(
        supplierId: Int,
        cursorCreatedAt: String?,
        /** The id of the row [cursorCreatedAt] came from: rows sharing its instant are told apart by it. */
        cursorId: Int?,
        s1: String, s2: String, s3: String,
        limit: Int
    ): List<SupplierPaymentEntity>

    /** [search] split into words, every one of which a row must contain — see searchSlots. */
    suspend fun pagePaymentsForSupplier(supplierId: Int, cursorCreatedAt: String?, cursorId: Int?, search: String, limit: Int): List<SupplierPaymentEntity> {
        val slots = searchSlots(search)
        return pagePaymentsForSupplierBySlots(supplierId, cursorCreatedAt, cursorId, slots[0], slots[1], slots[2], limit)
    }

    @Query("""
        SELECT COUNT(*) FROM supplier_payments
        WHERE supplier_id = :supplierId
        AND (:s1 = '' OR note LIKE '%' || :s1 || '%')
        AND (:s2 = '' OR note LIKE '%' || :s2 || '%')
        AND (:s3 = '' OR note LIKE '%' || :s3 || '%')
    """)
    suspend fun countPaymentsForSupplierBySlots(supplierId: Int, s1: String, s2: String, s3: String): Int

    /** [search] split into words, every one of which a row must contain — see searchSlots. */
    suspend fun countPaymentsForSupplier(supplierId: Int, search: String): Int {
        val slots = searchSlots(search)
        return countPaymentsForSupplierBySlots(supplierId, slots[0], slots[1], slots[2])
    }
}