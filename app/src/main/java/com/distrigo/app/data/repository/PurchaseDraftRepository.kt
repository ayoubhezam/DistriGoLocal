package com.distrigo.app.data.repository

import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.PurchaseDraftEntity
import com.distrigo.app.data.local.entity.PurchaseOrderEntity
import com.distrigo.app.data.local.entity.PurchaseOrderItemEntity
import com.distrigo.app.data.model.DraftBaseState
import com.distrigo.app.data.model.DraftBlock
import com.distrigo.app.data.model.DraftLine
import com.distrigo.app.data.model.DraftSnapshot
import com.distrigo.app.data.model.PurchaseDraft
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.Locale

class PurchaseDraftRepository(private val db: AppDatabase) {

    private val dao         = db.purchaseDraftDao()
    private val purchaseDao = db.purchaseDao()
    private val gson        = Gson()

    private val lineListType = object : TypeToken<List<DraftLine>>() {}.type

    // ── Reads ────────────────────────────────────────────────────────────────

    /**
     * Newest first. Each edit draft is badged from its bon's status in a single extra query for
     * the whole list, not one per row.
     */
    fun observeDrafts(): Flow<List<PurchaseDraft>> =
        dao.observeAll().map { rows ->
            val orderIds = rows.mapNotNull { it.source_order_id }.distinct()
            val statuses = if (orderIds.isEmpty()) emptyMap()
                           else dao.orderStatuses(orderIds).associate { it.id to it.status }
            rows.map { row ->
                val block = when {
                    row.source_order_id == null                     -> DraftBlock.NONE
                    !statuses.containsKey(row.source_order_id)      -> DraftBlock.DELETED
                    statuses[row.source_order_id] == STATUS_RECEIVED -> DraftBlock.RECEIVED
                    else                                            -> DraftBlock.NONE
                }
                row.toDraft(block)
            }
        }

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun count(): Int = dao.count()

    suspend fun getDraft(id: Int): PurchaseDraft? = dao.getById(id)?.toDraft()

    /** The pending edit draft for a bon, if any. Every entry point into edit mode must check. */
    suspend fun draftForOrder(orderId: Int): PurchaseDraft? = dao.getForOrder(orderId)?.toDraft()

    // ── Writes ───────────────────────────────────────────────────────────────

    /**
     * Inserts on the first write of a session and updates thereafter, returning the row id the
     * session owns. `created_at` is preserved across updates so the card can distinguish when a
     * draft was started from when it was last touched.
     */
    suspend fun upsert(draftId: Int?, snapshot: DraftSnapshot): Int {
        val now      = Instant.now().toString()
        val existing = draftId?.let { dao.getById(it) }

        if (existing == null) {
            return dao.insert(
                PurchaseDraftEntity(
                    supplier_id      = snapshot.supplierId,
                    supplier_name    = snapshot.supplierName,
                    items_json       = gson.toJson(snapshot.lines),
                    note             = snapshot.note,
                    montant_paye     = snapshot.montantPaye,
                    item_count       = snapshot.lines.size,
                    total            = snapshot.total,
                    last_step        = snapshot.lastStep,
                    created_at       = now,
                    updated_at       = now,
                    source_order_id  = snapshot.sourceOrderId,
                    base_fingerprint = snapshot.baseFingerprint,
                    base_captured_at = snapshot.sourceOrderId?.let { now }
                )
            ).toInt()
        }

        dao.update(
            existing.copy(
                supplier_id   = snapshot.supplierId,
                supplier_name = snapshot.supplierName,
                items_json    = gson.toJson(snapshot.lines),
                note          = snapshot.note,
                montant_paye  = snapshot.montantPaye,
                item_count    = snapshot.lines.size,
                total         = snapshot.total,
                last_step     = snapshot.lastStep,
                updated_at    = now
                // source_order_id / base_fingerprint / base_captured_at are set once, at insert:
                // the base is what the session started from and must not drift under it.
            )
        )
        return existing.id
    }

    suspend fun delete(id: Int) = dao.deleteById(id)

    suspend fun deleteForOrder(orderId: Int) = dao.deleteForOrder(orderId)

    // ── Edit-mode base ───────────────────────────────────────────────────────

    /**
     * The fingerprint to record when an edit session begins, and to compare against when it
     * resumes. Null if the bon is gone.
     */
    suspend fun baseFingerprintFor(orderId: Int): String? {
        val order = purchaseDao.getOrderById(orderId) ?: return null
        return committedFingerprint(order)
    }

    /**
     * The full answer at resume time. Offers no merge: a [DraftBaseState.CHANGED] result means the
     * caller must ask the user to choose between two states someone actually authored — their
     * draft, or the bon as it now stands.
     */
    suspend fun resolveBaseState(draft: PurchaseDraft): DraftBaseState {
        val orderId = draft.sourceOrderId ?: return DraftBaseState.NONE
        val order   = purchaseDao.getOrderById(orderId) ?: return DraftBaseState.DELETED
        if (order.status == STATUS_RECEIVED) return DraftBaseState.RECEIVED
        return if (committedFingerprint(order) == draft.baseFingerprint) DraftBaseState.UNCHANGED
               else DraftBaseState.CHANGED
    }

    private suspend fun committedFingerprint(order: PurchaseOrderEntity): String {
        val items = purchaseDao.getItemsForOrder(order.id)
        return DraftFingerprint.of(
            supplierId  = order.supplier_id,
            montantPaye = prefilledMontantPaye(order),
            note        = order.note.orEmpty(),
            lines       = prefilledLinesFor(items)
        )
    }

    /**
     * Mirrors exactly what `PurchaseFormSessionViewModel.prefillEditFromOrder` puts into the cart,
     * which since F14 is simply what the bon has stored — every field is carried across verbatim.
     *
     * The mirroring is the point, not the values: the fingerprint compares the *form as prefilled*
     * against the draft, so if the two sides ever read different fields, a freshly opened and
     * untouched edit would measure as modified and spawn a phantom draft on sight. **These two
     * functions move together** — they are two halves of one contract.
     */
    private fun prefilledLinesFor(items: List<PurchaseOrderItemEntity>): List<DraftLine> =
        items.map { item ->
            DraftLine(
                product_id      = item.product_id,
                product_name    = item.product_name,
                quantity        = item.quantity,
                unit_cost       = item.unit_cost,
                nb_colis        = item.nb_colis,
                unite_par_colis = item.unite_par_colis,
                has_expiry      = item.has_expiry,
                expiry_date     = item.expiry_date
            )
        }

    /** What the edit prefill puts in "Montant payé". Mirrors [montantPayeText] — see [prefilledLinesFor]. */
    private fun prefilledMontantPaye(order: PurchaseOrderEntity): String =
        montantPayeText(order.montant_paye)

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun PurchaseDraftEntity.toDraft(block: DraftBlock = DraftBlock.NONE) = PurchaseDraft(
        id              = id,
        supplierId      = supplier_id,
        supplierName    = supplier_name,
        lines           = runCatching { gson.fromJson<List<DraftLine>>(items_json, lineListType) }
                              .getOrNull().orEmpty(),
        note            = note,
        montantPaye     = montant_paye,
        itemCount       = item_count,
        total           = total,
        lastStep        = last_step,
        createdAt       = created_at,
        updatedAt       = updated_at,
        sourceOrderId   = source_order_id,
        baseFingerprint = base_fingerprint,
        baseCapturedAt  = base_captured_at,
        blockState      = block
    )

    companion object {
        private const val STATUS_RECEIVED = "received"

        /**
         * The string the edit prefill puts in the "Montant payé" field for a recorded amount.
         *
         * [Locale.ROOT] is not cosmetic: the field is parsed back with `String.toDoubleOrNull()`,
         * which accepts only a '.' decimal separator, so a French-formatted "80,00" would read as
         * 0 and the next save would write that over the recorded amount. Zero becomes the empty
         * string so an unpaid bon shows its placeholder rather than a typed-looking "0.00".
         *
         * Shared with `PurchaseFormSessionViewModel` so the form and the fingerprint cannot
         * disagree about what an untouched edit looks like.
         */
        fun montantPayeText(montantPaye: Double): String =
            if (montantPaye == 0.0) "" else String.format(Locale.ROOT, "%.2f", montantPaye)
    }
}
