package com.distrigo.app.data.repository

import androidx.room.withTransaction
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.VenteDraftEntity
import com.distrigo.app.data.local.entity.VenteEntity
import com.distrigo.app.data.local.entity.VenteItemEntity
import com.distrigo.app.data.model.DraftBaseState
import com.distrigo.app.data.model.DraftBlock
import com.distrigo.app.data.model.VenteDraft
import com.distrigo.app.data.model.VenteDraftLine
import com.distrigo.app.data.model.VenteDraftSnapshot
import com.distrigo.app.data.model.numberLabel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.util.Locale

class VenteDraftRepository(private val db: AppDatabase) {

    private val dao      = db.venteDraftDao()
    private val venteDao = db.venteDao()
    private val gson     = Gson()

    private val lineListType = object : TypeToken<List<VenteDraftLine>>() {}.type

    // ── Reads ────────────────────────────────────────────────────────────────

    /**
     * Newest first, each edit draft badged from whether its vente still exists.
     *
     * Both halves are *observed* queries combined, not one query plus a lookup inside the mapping.
     * Room re-runs a Flow only when a table that query reads is written, so a suspend lookup in the
     * `map` would never re-run when a vente was deleted — see [VenteDraftDao.observeVenteIds].
     */
    fun observeDrafts(): Flow<List<VenteDraft>> =
        combine(dao.observeAll(), dao.observeVenteIds()) { rows, venteIds ->
            val existing = venteIds.toSet()
            rows.map { row ->
                val block = when {
                    row.source_vente_id == null      -> DraftBlock.NONE
                    row.source_vente_id !in existing -> DraftBlock.DELETED
                    else                             -> DraftBlock.NONE
                }
                row.toDraft(block)
            }
        }

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun count(): Int = dao.count()

    suspend fun getDraft(id: Int): VenteDraft? = dao.getById(id)?.toDraft()

    /** The pending edit draft for a vente, if any. Every entry point into edit mode must check. */
    suspend fun draftForVente(venteId: Int): VenteDraft? = dao.getForVente(venteId)?.toDraft()

    // ── Writes ───────────────────────────────────────────────────────────────

    /**
     * Inserts on the first write of a session and updates thereafter, returning the row id the
     * session owns. `created_at` is preserved across updates so the card can distinguish when a
     * draft was started from when it was last touched.
     */
    suspend fun upsert(draftId: Int?, snapshot: VenteDraftSnapshot): Int = db.withTransaction {
        val now      = Instant.now().toString()
        val existing = draftId?.let { dao.getById(it) }

        if (existing == null) {
            return@withTransaction dao.insert(
                VenteDraftEntity(
                    client_id        = snapshot.clientId,
                    client_name      = snapshot.clientName,
                    items_json       = gson.toJson(snapshot.lines),
                    note             = snapshot.note,
                    montant_paye     = snapshot.montantPaye,
                    user_name        = snapshot.userName,
                    item_count       = snapshot.lines.size,
                    total            = snapshot.total,
                    last_step        = snapshot.lastStep,
                    created_at       = now,
                    updated_at       = now,
                    source_vente_id  = snapshot.sourceVenteId,
                    base_fingerprint = snapshot.baseFingerprint,
                    base_captured_at = snapshot.sourceVenteId?.let { now }
                )
            ).toInt()
        }

        dao.update(
            existing.copy(
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
                // source_vente_id / base_fingerprint / base_captured_at are set once, at insert:
                // the base is what the session started from and must not drift under it.
            )
        )
        existing.id
    }

    suspend fun delete(id: Int) = dao.deleteById(id)

    /** Deletes a whole selection at once — see [VenteDraftDao.deleteByIds]. */
    suspend fun deleteAll(ids: List<Int>) = dao.deleteByIds(ids)

    suspend fun deleteForVente(venteId: Int) = dao.deleteForVente(venteId)

    // ── Edit-mode base ───────────────────────────────────────────────────────

    /**
     * The fingerprint to record when an edit session begins, and to compare against when it
     * resumes. Null if the vente is gone.
     */
    suspend fun baseFingerprintFor(venteId: Int): String? {
        val vente = venteDao.getVenteById(venteId) ?: return null
        return committedFingerprint(vente)
    }

    /**
     * The full answer at resume time. Offers no merge: a [DraftBaseState.CHANGED] result means the
     * caller must ask the user to choose between two states someone actually authored — their
     * draft, or the vente as it now stands.
     *
     * There is no [DraftBaseState.RECEIVED] branch. A delivered vente is still editable, because
     * `ProductRepository.updateVente` reverses every old line's stock before applying the new ones,
     * whatever the status says.
     */
    suspend fun resolveBaseState(draft: VenteDraft): DraftBaseState {
        val venteId = draft.sourceVenteId ?: return DraftBaseState.NONE
        val vente   = venteDao.getVenteById(venteId) ?: return DraftBaseState.DELETED
        return if (committedFingerprint(vente) == draft.baseFingerprint) DraftBaseState.UNCHANGED
               else DraftBaseState.CHANGED
    }

    private suspend fun committedFingerprint(vente: VenteEntity): String {
        val items = venteDao.getItemsForVente(vente.id)
        return VenteFingerprint.of(
            clientId    = vente.client_id,
            montantPaye = prefilledMontantPaye(vente),
            note        = vente.note.orEmpty(),
            userName    = prefilledUserName(),
            lines       = prefilledLinesFor(items)
        )
    }

    /**
     * Mirrors exactly what the Dépôt Vente edit prefill puts into the cart.
     *
     * The mirroring is the point, not the values: the fingerprint compares the *form as prefilled*
     * against the draft, so if the two sides ever read different fields, a freshly opened and
     * untouched edit form would read as dirty and spawn a phantom draft on sight.
     */
    private fun prefilledLinesFor(items: List<VenteItemEntity>): List<VenteDraftLine> =
        items.map { item ->
            VenteDraftLine(
                product_id   = item.product_id,
                product_name = item.product_name,
                unit_type    = item.unit_type,
                quantity     = item.quantity,
                unit_price   = item.unit_price
            )
        }

    private fun prefilledMontantPaye(vente: VenteEntity): String =
        montantPayeText(vente.montant_paye)

    /**
     * Always empty, because a committed vente has nowhere to hold it: "Effectué par" is written
     * onto the stock movements, not onto the `ventes` row, so the edit prefill has nothing to
     * restore it from either. See [VenteFingerprint].
     */
    private fun prefilledUserName(): String = ""

    // ── Mapping ──────────────────────────────────────────────────────────────

    private suspend fun VenteDraftEntity.toDraft(block: DraftBlock = DraftBlock.NONE) = VenteDraft(
        id              = id,
        clientId        = client_id,
        clientName      = client_name,
        lines           = gson.fromJson(items_json, lineListType) ?: emptyList(),
        note            = note,
        montantPaye     = montant_paye,
        userName        = user_name,
        itemCount       = item_count,
        total           = total,
        lastStep        = last_step,
        createdAt       = created_at,
        updatedAt       = updated_at,
        sourceVenteId   = source_vente_id,
        baseFingerprint = base_fingerprint,
        baseCapturedAt  = base_captured_at,
        blockState      = block,
        sourceNumber    = source_vente_id?.let { numberLabel(db.venteDao().getNumero(it), it) }
    )

    companion object {
        /** The form's own rendering of a stored amount — "" for zero, so the field starts empty. */
        fun montantPayeText(montantPaye: Double): String =
            if (montantPaye == 0.0) "" else String.format(Locale.ROOT, "%.2f", montantPaye)
    }
}
