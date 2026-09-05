package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.distrigo.app.data.local.entity.PurchaseDraftEntity
import kotlinx.coroutines.flow.Flow

/** `id`/`status` projection for the drafts list — see [PurchaseDraftDao.observeOrderStatuses]. */
data class DraftOrderStatus(val id: Int, val status: String)

@Dao
interface PurchaseDraftDao {

    // ── Reads ──
    @Query("SELECT * FROM purchase_drafts ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<PurchaseDraftEntity>>

    @Query("SELECT COUNT(*) FROM purchase_drafts")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM purchase_drafts")
    suspend fun count(): Int

    @Query("SELECT * FROM purchase_drafts WHERE id = :id")
    suspend fun getById(id: Int): PurchaseDraftEntity?

    /** At most one row can match — the unique index on `source_order_id` guarantees it. */
    @Query("SELECT * FROM purchase_drafts WHERE source_order_id = :orderId LIMIT 1")
    suspend fun getForOrder(orderId: Int): PurchaseDraftEntity?

    // ── Writes ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(draft: PurchaseDraftEntity): Long

    @Update
    suspend fun update(draft: PurchaseDraftEntity)

    @Query("DELETE FROM purchase_drafts WHERE id = :id")
    suspend fun deleteById(id: Int)

    /**
     * Bulk delete for the Brouillons screen's selection mode.
     *
     * One statement rather than a loop of [deleteById]: Room invalidates `purchase_drafts` once, so the
     * list recomposes once instead of N times, and the whole selection goes or none of it does.
     */
    @Query("DELETE FROM purchase_drafts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    @Query("DELETE FROM purchase_drafts WHERE source_order_id = :orderId")
    suspend fun deleteForOrder(orderId: Int)

    /**
     * Reads `purchase_orders` so the drafts list can badge an edit draft whose bon has since been
     * received or deleted. It lives here rather than on `PurchaseDao` because it exists purely for
     * drafts, and keeping it here means this feature adds no query to the purchase DAO at all.
     *
     * One statement for the whole list, not one per row — the badge needs only the status, so the
     * expensive check (comparing contents through DraftFingerprint) is left to resume, which is
     * the only moment it changes what happens.
     *
     * Observed rather than fetched inside the mapping, and unfiltered rather than
     * `WHERE id IN (:ids)`, because **this is what makes the badge live**. Room invalidates a Flow
     * only when a table the query itself reads is written, so a suspend call made inside
     * `observeDrafts`'s `map` would never re-run when a bon was received or deleted — the badge
     * would go on claiming the draft was fine until something else touched `purchase_drafts`.
     * Reading two columns for every bon is the price of the list telling the truth.
     */
    @Query("SELECT id, status FROM purchase_orders")
    fun observeOrderStatuses(): Flow<List<DraftOrderStatus>>
}
