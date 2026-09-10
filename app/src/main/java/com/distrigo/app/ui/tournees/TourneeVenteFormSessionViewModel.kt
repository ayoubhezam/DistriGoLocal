package com.distrigo.app.ui.tournees

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.TourneeVenteDraft
import com.distrigo.app.data.model.TourneeVenteDraftLine
import com.distrigo.app.data.model.TourneeVenteDraftSnapshot
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.TourneeVenteDraftRepository
import com.distrigo.app.ui.common.DraftAutosave
import com.distrigo.app.ui.common.DraftAutosaveHost
import com.distrigo.app.ui.common.SessionPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns one pass through the Tournée Vente form: the form's own state, whether this pass is new or
 * resumed, and the autosave that keeps a Brouillon behind it.
 *
 * Scoped to the form graph's back stack entry, like its two siblings, and for the same two reasons:
 * a process-death return finds the handle saying a session was already started and restores from
 * the draft silently, while leaving the graph destroys the handle so the next entry is free to ask
 * "Reprendre ou recommencer ?".
 *
 * ### Thinner than the other two, deliberately
 *
 * The tournée form is create-only — its graph takes no `venteId` and it calls `createVente`. There
 * is no committed record to be an unsaved edit of, so there is no prefill-from-source, no base
 * fingerprint, no conflict check and no OBSOLÈTE state. [isEdit] is constantly false and
 * [baseFingerprint] constantly null, which is what makes the shared [DraftAutosave] fall through
 * to its new-record path: emptiness decides whether a write happens.
 *
 * ### Two things it has that the others do not
 *
 * A van sells from what is on the van. A draft resumed an hour later can name a product that has
 * since been deleted, exactly as the other two can — and it can also ask for more of a product than
 * is left in the camion, because the goods it wanted were sold to somebody else in the meantime.
 * Both are resolved at [hydrate] and published as blocks, so the form can mark the offending line
 * and refuse to commit rather than failing inside the save transaction.
 *
 * [TourneeViewModel] keeps the tournées, their clients, and the commands that act on them.
 */
@HiltViewModel
class TourneeVenteFormSessionViewModel @Inject constructor(
    private val draftRepository  : TourneeVenteDraftRepository,
    private val productRepository: ProductRepository,
    private val savedState       : SavedStateHandle
) : ViewModel(), DraftAutosaveHost<TourneeVenteDraftSnapshot> {

    // ── Form state ───────────────────────────────────────────────────────────

    private val _formClient = MutableStateFlow<Client?>(null)
    val formClient: StateFlow<Client?> = _formClient

    private val _formCartItems = MutableStateFlow<List<TourneeVenteCartItem>>(emptyList())
    val formCartItems: StateFlow<List<TourneeVenteCartItem>> = _formCartItems

    private val _formNote = MutableStateFlow("")
    val formNote: StateFlow<String> = _formNote

    private val _formMontantPaye = MutableStateFlow("")
    val formMontantPaye: StateFlow<String> = _formMontantPaye

    fun setFormClient(client: Client?) { _formClient.value = client }

    fun setFormCartItems(items: List<TourneeVenteCartItem>) {
        _formCartItems.value = items
        pruneBlocks(items.map { it.product.id })
    }

    fun setFormNote(note: String) { _formNote.value = note }
    fun setFormMontantPaye(value: String) { _formMontantPaye.value = value }

    // ── Session state ────────────────────────────────────────────────────────

    private val _phase = MutableStateFlow(SessionPhase.UNDECIDED)
    val phase: StateFlow<SessionPhase> = _phase

    /**
     * Products a restored draft names that are no longer in the catalogue. Their cart lines still
     * render, from the name the draft stored, but the sale cannot be confirmed until they are
     * removed — dropping them silently would change a total the user remembers.
     */
    private val _missingProductIds = MutableStateFlow<Set<Int>>(emptySet())
    val missingProductIds: StateFlow<Set<Int>> = _missingProductIds

    /**
     * Products the draft asks for more of than the camion still holds.
     *
     * The tournée form enforces a hard ceiling live — its stepper will not go past what is on the
     * van — but a draft is written before that ceiling moves and read after. Selling the stock to
     * somebody else in between is not an error, it is Tuesday, so the resumed form says so on the
     * line and blocks the save rather than letting `createVente`'s camion pre-check throw at the
     * end of the flow.
     */
    private val _overStockProductIds = MutableStateFlow<Set<Int>>(emptySet())
    val overStockProductIds: StateFlow<Set<Int>> = _overStockProductIds

    /**
     * Drops ids whose cart line has gone, for both blocks.
     *
     * Only [hydrate] can *discover* either problem — each needs a live catalogue lookup — so this
     * never adds to a set. It only lets a block lift when the user does the thing the cart line
     * asks of them, which is the whole point: without it, removing the line left the id behind and
     * the form stayed un-saveable with no way out that anything on screen suggested.
     *
     * An over-stock line can also be fixed by lowering its quantity rather than removing it, and
     * that is handled where the quantity changes — see [reviseOverStock].
     */
    private fun pruneBlocks(presentIds: Collection<Int>) {
        val present = presentIds.toSet()
        if (_missingProductIds.value.isNotEmpty()) {
            _missingProductIds.value = _missingProductIds.value intersect present
        }
        if (_overStockProductIds.value.isNotEmpty()) {
            _overStockProductIds.value = _overStockProductIds.value intersect present
        }
    }

    /**
     * Re-checks the over-stock block against what the cart now asks for.
     *
     * Called by the cart step when a quantity changes: unlike a deleted product, this block has a
     * second, better cure than removing the line — asking for less. `product.camion_stock` is the
     * live figure the products step keeps re-synced, so this compares against the truck as it is
     * now rather than as it was at hydrate.
     */
    fun reviseOverStock() {
        if (_overStockProductIds.value.isEmpty()) return
        _overStockProductIds.value = _formCartItems.value
            .filter { it.quantity > it.product.camion_stock }
            .map { it.product.id }
            .toSet()
    }

    /**
     * Synchronous guard against double entry. During a navigation transition the outgoing and
     * incoming destinations are both composed, so [beginOrResumeSession] can be called twice before
     * the coroutine it launches has moved [_phase] off UNDECIDED.
     */
    private var entering = false

    /** The draft row this session owns, once it has one. Null until the form is first dirtied. */
    val draftId: Int? get() = savedState[KEY_DRAFT_ID]

    private val tourneeId: Int get() = savedState[KEY_TOURNEE_ID] ?: 0

    // ── Autosave ─────────────────────────────────────────────────────────────

    private val autosave = DraftAutosave(
        host    = this,
        scope   = viewModelScope,
        signals = listOf(_formClient, _formCartItems, _formNote, _formMontantPaye)
    )

    /** Writes the current form now rather than waiting out the debounce. */
    fun flushDraft() = autosave.flush()

    // ── DraftAutosaveHost ────────────────────────────────────────────────────

    /** Always null: create-only, so there is never a base to have diverged from. */
    override val baseFingerprint: String? get() = null

    override var draftRowId: Int?
        get()      = savedState[KEY_DRAFT_ID]
        set(value) { savedState[KEY_DRAFT_ID] = value }

    /** Always false, for the same reason [baseFingerprint] is always null. */
    override fun isEdit(snapshot: TourneeVenteDraftSnapshot) = false

    override fun isEmpty(snapshot: TourneeVenteDraftSnapshot) = snapshot.isEmpty

    /**
     * Never consulted — [DraftAutosave] only fingerprints an edit session, and this one is never
     * an edit. Returning the empty string rather than hashing something is the honest answer: there
     * is no base to compare a hash against.
     */
    override fun fingerprintOf(snapshot: TourneeVenteDraftSnapshot) = ""

    override suspend fun upsertDraft(existing: Int?, snapshot: TourneeVenteDraftSnapshot): Int =
        draftRepository.upsert(existing, snapshot)

    override suspend fun deleteDraft(id: Int) = draftRepository.delete(id)

    override fun snapshot() = TourneeVenteDraftSnapshot(
        tourneeId   = tourneeId,
        clientId    = _formClient.value?.id,
        clientName  = _formClient.value?.name,
        lines       = _formCartItems.value.map { it.toDraftLine() },
        note        = _formNote.value,
        montantPaye = _formMontantPaye.value,
        lastStep    = savedState[KEY_LAST_STEP] ?: ""
    )

    fun setLastStep(route: String) { savedState[KEY_LAST_STEP] = route }

    // ── Session entry ────────────────────────────────────────────────────────

    /**
     * The single entry point the graph calls on arrival. Idempotent across recomposition, and
     * across process death — which is the point.
     *
     * As in the other two flows, nothing here decides whether to prompt. That happens upstream at
     * the FAB or the draft card, before this graph is entered; a process-death return re-enters
     * without passing through those, so it cannot prompt — not by choosing not to, but because
     * there is no code here that could.
     *
     * @param tourneeId the round this sale belongs to. Recorded on the session because every draft
     *                  write needs it and the graph's arguments are not reachable from here.
     * @param clientId  a client chosen before the form opened (tapping a client circle), or null.
     * @param draftId   a Brouillon to resume, or null to start clean.
     */
    fun beginOrResumeSession(tourneeId: Int, clientId: Int?, draftId: Int?) {
        if (entering || _phase.value != SessionPhase.UNDECIDED) return
        entering = true

        savedState[KEY_TOURNEE_ID] = tourneeId

        if (savedState.get<Boolean>(KEY_STARTED) == true) {
            restoreAfterProcessDeath(clientId)
            return
        }
        savedState[KEY_STARTED] = true

        viewModelScope.launch {
            when {
                draftId != null -> {
                    draftRepository.getDraft(draftId)?.let { draft ->
                        savedState[KEY_DRAFT_ID] = draft.id
                        hydrate(draft)
                    }
                    arm()
                }

                else -> {
                    resetForm()
                    if (clientId != null) preselectClient(clientId)
                    arm()
                }
            }
        }
    }

    /**
     * Comes back from a kill with the same draft row and the same content, and no dialog.
     *
     * The row id survives on the handle, so the draft is re-read rather than re-created. If it is
     * gone — committed on another path, or deleted from the Brouillons screen — the form falls back
     * to a clean start rather than resurrecting a row that no longer exists.
     */
    private fun restoreAfterProcessDeath(clientId: Int?) {
        viewModelScope.launch {
            val id    = savedState.get<Int>(KEY_DRAFT_ID)
            val draft = id?.let { draftRepository.getDraft(it) }
            if (draft != null) {
                hydrate(draft)
            } else {
                savedState[KEY_DRAFT_ID] = null
                resetForm()
                if (clientId != null) preselectClient(clientId)
            }
            arm()
        }
    }

    private suspend fun preselectClient(clientId: Int) {
        _formClient.value = productRepository.getClients().find { it.id == clientId }
    }

    // ── Hydration ────────────────────────────────────────────────────────────

    /**
     * Rebuilds cart lines from stored lines, taking the [Product] live from the catalogue and the
     * quantity and price from the draft. What the user negotiated comes back exactly as they left
     * it; what belongs to the catalogue — the name, the unit, and above all `camion_stock` — comes
     * back current.
     *
     * Both blocks are decided here, and only here, because both need the live catalogue.
     */
    private suspend fun hydrate(draft: TourneeVenteDraft) {
        val products  = productRepository.getProducts().associateBy { it.id }
        val missing   = mutableSetOf<Int>()
        val overStock = mutableSetOf<Int>()

        _formClient.value = draft.clientId?.let { id ->
            productRepository.getClients().find { it.id == id }
        }

        _formCartItems.value = draft.lines.map { line ->
            val product = products[line.product_id] ?: run {
                missing += line.product_id
                placeholderProduct(line)
            }
            if (line.product_id !in missing && line.quantity > product.camion_stock) {
                overStock += line.product_id
            }
            TourneeVenteCartItem(
                product   = product,
                quantity  = line.quantity,
                unitPrice = line.unit_price
            )
        }

        _missingProductIds.value   = missing
        _overStockProductIds.value = overStock
        _formNote.value            = draft.note
        _formMontantPaye.value     = draft.montantPaye
    }

    /** Keeps a deleted product's line visible and named instead of dropping it. */
    private fun placeholderProduct(line: TourneeVenteDraftLine) = Product(
        id             = line.product_id,
        name           = line.product_name,
        barcode        = null,
        selling_price  = line.unit_price,
        purchase_price = 0.0,
        stock          = 0.0,
        min_stock      = 0,
        unit_type      = line.unit_type,
        packages       = 0,
        pack_size      = 0,
        has_expiry     = 0,
        expiry_date    = null,
        image_uri      = null,
        category_name  = null,
        category_id    = null,
        supplier_name  = null,
        supplier_id    = null,
        camion_stock   = 0.0
    )

    private fun TourneeVenteCartItem.toDraftLine() = TourneeVenteDraftLine(
        product_id   = product.id,
        product_name = product.name,
        unit_type    = product.unit_type,
        quantity     = quantity,
        unit_price   = unitPrice
    )

    private fun resetForm() {
        _formClient.value          = null
        _formCartItems.value       = emptyList()
        _formNote.value            = ""
        _formMontantPaye.value     = ""
        _missingProductIds.value   = emptySet()
        _overStockProductIds.value = emptySet()
    }

    private fun arm() {
        autosave.arm()
        _phase.value = SessionPhase.READY
    }

    /**
     * Called after the sale commits. The draft row is already gone — deleted inside the same
     * transaction — so this only clears the session's memory of it, keeping the handle from
     * pointing at an id that no longer exists if the graph is re-entered.
     */
    fun onCommitted() {
        autosave.disarm()
        savedState[KEY_DRAFT_ID] = null
    }

    private companion object {
        const val KEY_STARTED    = "tv_session_started"
        const val KEY_DRAFT_ID   = "tv_session_draft_id"
        const val KEY_LAST_STEP  = "tv_session_last_step"
        const val KEY_TOURNEE_ID = "tv_session_tournee_id"
    }
}
