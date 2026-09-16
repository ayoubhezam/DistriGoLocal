package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.distrigo.app.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {

    @Query("SELECT * FROM suppliers WHERE deleted_at IS NULL ORDER BY name ASC")
    suspend fun getAllSuppliers(): List<SupplierEntity>

    // نسخة مُراقَبة: Room يعيد إصدار القائمة تلقائياً عند أي كتابة على جدول الموردين
    // (إضافة/تعديل/حذف، تحديث الرصيد بعد شراء أو دفعة…) مهما كان مصدر الكتابة
    @Query("SELECT * FROM suppliers WHERE deleted_at IS NULL ORDER BY name ASC")
    fun observeAllSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :supplierId AND deleted_at IS NULL")
    suspend fun getSupplierById(supplierId: Int): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: SupplierEntity): Long

    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)

    @Query("UPDATE suppliers SET deleted_at = CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER) WHERE id = :supplierId AND deleted_at IS NULL")
    suspend fun softDeleteSupplierById(supplierId: Int)

    /**
     * Brings a supplier's stored balance back in line with its history, in one statement:
     *
     *     initial_balance + Σ(order total − paid at creation) − Σ payments − Σ returns
     *
     * This is the only place that formula lives. It used to exist in two private copies that
     * disagreed — the one in ProductRepository left returns out — so every purchase or payment
     * saved after a return silently put the returned amount back on the balance.
     *
     * One UPDATE with SUM subqueries rather than rows read into Kotlin. Each subquery is a range
     * on an index that leads with supplier_id, so the cost follows this supplier's history, not
     * the table's — the purchase-order copy used to load every order ever made, inside the
     * write lock, to keep the ones for one supplier. And only `balance` is written, so no other
     * column can be put back to a stale copy of itself.
     *
     * Every order counts whatever its status, as it always has. A supplier id that does not
     * exist updates nothing.
     *
     * MIGRATION_40_41 applied this formula once, to repair balances already stored. If the
     * formula ever changes, existing balances need a migration of their own; that one is a record
     * of what was done at v41 and must not be edited to follow.
     */
    @Query("""
        UPDATE suppliers SET balance =
              initial_balance
            + (SELECT COALESCE(SUM(total), 0.0) - COALESCE(SUM(montant_paye), 0.0)
                 FROM purchase_orders    WHERE supplier_id = :supplierId)
            - (SELECT COALESCE(SUM(amount), 0.0)
                 FROM supplier_payments  WHERE supplier_id = :supplierId)
            - (SELECT COALESCE(SUM(total), 0.0)
                 FROM retour_fournisseur WHERE supplier_id = :supplierId)
        WHERE id = :supplierId
    """)
    suspend fun recomputeBalance(supplierId: Int)
}
