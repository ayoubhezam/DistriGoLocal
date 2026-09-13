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

    /**
     * The wilaya most of this distributor's clients are in, used to prefill the field when adding
     * one — or null if no wilaya has been used at least twice, which is the point at which
     * guessing stops being helpful.
     *
     * Counted in SQL rather than over an observed list. The Kotlin version read
     * `clients.value` off a `StateFlow` it did not itself collect, so it silently returned null
     * whenever nothing else happened to be subscribed; correct only by the accident of which
     * screen was on top. It also pulled every client row into memory to count a string.
     *
     * The `wilaya_name ASC` tiebreak is what makes a tie deterministic; `maxByOrNull` over a map
     * resolved one by iteration order.
     */
    @Query("""
        SELECT wilaya_name FROM clients
        WHERE wilaya_name IS NOT NULL AND TRIM(wilaya_name) != ''
        GROUP BY wilaya_name
        HAVING COUNT(*) >= 2
        ORDER BY COUNT(*) DESC, wilaya_name ASC
        LIMIT 1
    """)
    suspend fun getMostCommonWilaya(): String?

    /**
     * Brings a client's stored balance back in line with their history, in one statement:
     *
     *     Σ(sale total − paid) − Σ payments − Σ returns
     *
     * Clients have no opening-balance column, so unlike SupplierDao.recomputeBalance there is no
     * initial term. Otherwise the same rules: the only copy of the formula, returns included, one
     * statement over indexes that lead with client_id, only `balance` written, every sale counted
     * whatever its status, a missing id a no-op.
     *
     * MIGRATION_40_41 applied this formula once to repair balances already stored; see the note on
     * SupplierDao.recomputeBalance about keeping that migration as it is.
     */
    @Query("""
        UPDATE clients SET balance =
              (SELECT COALESCE(SUM(total), 0.0) - COALESCE(SUM(montant_paye), 0.0)
                 FROM ventes          WHERE client_id = :clientId)
            - (SELECT COALESCE(SUM(amount), 0.0)
                 FROM client_payments WHERE client_id = :clientId)
            - (SELECT COALESCE(SUM(total), 0.0)
                 FROM retour_client   WHERE client_id = :clientId)
        WHERE id = :clientId
    """)
    suspend fun recomputeBalance(clientId: Int)
}