package com.distrigo.app.data.repository

import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.TourneeVenteDraftEntity
import com.distrigo.app.data.model.TourneeVenteDraft
import com.distrigo.app.data.model.TourneeVenteDraftLine
import com.distrigo.app.data.model.TourneeVenteDraftSnapshot
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Storage for unfinished van sales.
 *
 * Noticeably thinner than [VenteDraftRepository] and [PurchaseDraftRepository], and the missing
 * parts are all the same part: the tournée form is create-only, so there is no committed record a
 * draft can be an unsaved edit of. No `draftForX`, no fingerprint capture, no `resolveBaseState`,
 * and no combine against a second observed query to badge a source that has since moved — the
 * whole class of "the thing underneath you changed" cannot arise here.
 *
 * What it gains is a tournée: every read is scoped to one.
 */
class TourneeVenteDraftRepository(private val db: AppDatabase) {

    private val dao  = db.tourneeVenteDraftDao()
    private val gson = Gson()

    private val lineListType = object : TypeToken<List<TourneeVenteDraftLine>>() {}.type

    // ── Reads ────────────────────────────────────────────────────────────────

    /** Newest first. A plain map: with no source record, there is nothing to cross-check. */
    fun observeDrafts(tourneeId: Int): Flow<List<TourneeVenteDraft>> =
        dao.observeForTournee(tourneeId).map { rows -> rows.map { it.toDraft() } }

    fun observeCount(tourneeId: Int): Flow<Int> = dao.observeCountForTournee(tourneeId)

    suspend fun getDraft(id: Int): TourneeVenteDraft? = dao.getById(id)?.toDraft()

    // ── Writes ───────────────────────────────────────────────────────────────

    /**
     * Inserts on the first write of a session and updates thereafter, returning the row id the
     * session owns. `created_at` is preserved across updates so the card can distinguish when a
     * draft was started from when it was last touched.
     */
    suspend fun upsert(draftId: Int?, snapshot: TourneeVenteDraftSnapshot): Int {
        val now      = java.time.Instant.now().toString()
        val existing = draftId?.let { dao.getById(it) }

        if (existing == null) {
            return dao.insert(
                TourneeVenteDraftEntity(
                    tournee_id   = snapshot.tourneeId,
                    client_id    = snapshot.clientId,
                    client_name  = snapshot.clientName,
                    items_json   = gson.toJson(snapshot.lines),
                    note         = snapshot.note,
                    montant_paye = snapshot.montantPaye,
                    user_name    = snapshot.userName,
                    item_count   = snapshot.lines.size,
                    total        = snapshot.total,
                    last_step    = snapshot.lastStep,
                    created_at   = now,
                    updated_at   = now
                )
            ).toInt()
        }

        dao.update(
            existing.copy(
                tournee_id   = snapshot.tourneeId,
                client_id    = snapshot.clientId,
                client_name  = snapshot.clientName,
                items_json   = gson.toJson(snapshot.lines),
                note         = snapshot.note,
                montant_paye = snapshot.montantPaye,
                user_name    = snapshot.userName,
                item_count   = snapshot.lines.size,
                total        = snapshot.total,
                last_step    = snapshot.lastStep,
                updated_at   = now
            )
        )
        return existing.id
    }

    suspend fun delete(id: Int) = dao.deleteById(id)

    suspend fun deleteAll(ids: List<Int>) = dao.deleteByIds(ids)

    /** Called when a tournée is deleted, so its unfinished sales do not outlive it. */
    suspend fun deleteForTournee(tourneeId: Int) = dao.deleteForTournee(tourneeId)

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun TourneeVenteDraftEntity.toDraft() = TourneeVenteDraft(
        id          = id,
        tourneeId   = tournee_id,
        clientId    = client_id,
        clientName  = client_name,
        lines       = runCatching {
            gson.fromJson<List<TourneeVenteDraftLine>>(items_json, lineListType)
        }.getOrNull() ?: emptyList(),
        note        = note,
        montantPaye = montant_paye,
        userName    = user_name,
        itemCount   = item_count,
        total       = total,
        lastStep    = last_step,
        createdAt   = created_at,
        updatedAt   = updated_at
    )
}
