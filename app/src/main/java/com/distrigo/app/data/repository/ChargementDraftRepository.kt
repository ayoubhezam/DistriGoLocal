package com.distrigo.app.data.repository

import androidx.room.withTransaction
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.ChargementDraftEntity
import com.distrigo.app.data.model.ChargementDraft
import com.distrigo.app.data.model.ChargementDraftLine
import com.distrigo.app.data.model.ChargementDraftSnapshot
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Storage for unfinished stock movements, of both kinds.
 *
 * Like [TourneeVenteDraftRepository] and unlike the two older ones, there is no source record to
 * resolve against: the chargement form only ever creates. What it has instead is the split between
 * a listed Brouillon and the single-product card's private editing state, and the reads enforce it
 * — [observeDrafts] and [observeCount] never return the latter.
 */
class ChargementDraftRepository(private val db: AppDatabase) {

    private val dao  = db.chargementDraftDao()
    private val gson = Gson()

    private val lineListType = object : TypeToken<List<ChargementDraftLine>>() {}.type

    // ── Reads ────────────────────────────────────────────────────────────────

    /** Brouillons only, newest first. The Modifier card's editing state is excluded by the query. */
    fun observeDrafts(): Flow<List<ChargementDraft>> =
        dao.observeDrafts().map { rows -> rows.map { it.toDraft() } }

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun getDraft(id: Int): ChargementDraft? = dao.getById(id)?.toDraft()

    /**
     * The pending edit for one product, restored silently when its card is reopened.
     *
     * This is the whole point of persisting that state: after a crash, a power-off or a swipe-away,
     * the card comes back holding what the user had typed rather than the truck's stock figure.
     */
    suspend fun draftForProduct(productId: Int): ChargementDraft? =
        dao.getForProduct(productId)?.toDraft()

    // ── Writes ───────────────────────────────────────────────────────────────

    /**
     * Inserts on the first write of a session and updates thereafter, returning the row id the
     * session owns. `created_at` is preserved across updates so the card can distinguish when a
     * draft was started from when it was last touched.
     */
    suspend fun upsert(draftId: Int?, snapshot: ChargementDraftSnapshot): Int = db.withTransaction {
        val now      = java.time.Instant.now().toString()
        val existing = draftId?.let { dao.getById(it) }

        if (existing == null) {
            return@withTransaction dao.insert(
                ChargementDraftEntity(
                    single_product_id = snapshot.singleProductId,
                    items_json        = gson.toJson(snapshot.lines),
                    note              = snapshot.note,
                    user_name         = snapshot.userName,
                    item_count        = snapshot.lines.size,
                    created_at        = now,
                    updated_at        = now
                )
            ).toInt()
        }

        dao.update(
            existing.copy(
                single_product_id = snapshot.singleProductId,
                items_json        = gson.toJson(snapshot.lines),
                note              = snapshot.note,
                user_name         = snapshot.userName,
                item_count        = snapshot.lines.size,
                updated_at        = now
            )
        )
        existing.id
    }

    suspend fun delete(id: Int) = dao.deleteById(id)

    suspend fun deleteAll(ids: List<Int>) = dao.deleteByIds(ids)

    /** Called when the user chooses to discard a single-product edit rather than save it. */
    suspend fun deleteForProduct(productId: Int) = dao.deleteForProduct(productId)

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun ChargementDraftEntity.toDraft() = ChargementDraft(
        id              = id,
        singleProductId = single_product_id,
        lines           = runCatching {
            gson.fromJson<List<ChargementDraftLine>>(items_json, lineListType)
        }.getOrNull() ?: emptyList(),
        note            = note,
        userName        = user_name,
        itemCount       = item_count,
        createdAt       = created_at,
        updatedAt       = updated_at
    )
}
