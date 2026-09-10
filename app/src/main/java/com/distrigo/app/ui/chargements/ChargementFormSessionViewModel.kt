package com.distrigo.app.ui.chargements

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.ChargementDraft
import com.distrigo.app.data.model.ChargementDraftLine
import com.distrigo.app.data.model.ChargementDraftSnapshot
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.repository.ChargementDraftRepository
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.ui.common.DraftAutosave
import com.distrigo.app.ui.common.DraftAutosaveHost
import com.distrigo.app.ui.common.SessionPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns one pass through the multi-product "Nouveau chargement" form, and the Brouillon behind it.
 *
 * Scoped to the form graph's back stack entry like its three siblings: a process-death return finds
 * the handle saying a session was already started and restores from the draft silently, while
 * leaving the graph destroys the handle so the next entry is free to ask "Reprendre ou
 * recommencer ?".
 *
 * ### Create-only, like Tournée Vente
 *
 * `ProductRepository` has no `updateChargement`, and `correctionChargementId` — the one parameter
 * that ever hinted at editing an existing movement — is passed by nobody. So there is no committed
 * record a draft can be an unsaved edit of: no base fingerprint, no conflict check, no OBSOLÈTE
 * state. [isEdit] is constantly false, which makes the shared [DraftAutosave] take its new-record
 * path, where emptiness alone decides whether a write happens.
 *
 * ### What a line means
 *
 * A line stores `target_camion` — "15 in the truck" — not the delta. The delta is computed against
 * the truck as it stands *at save time*. A draft therefore says what you wanted the truck to hold,
 * and if the truck reached that figure by itself while the draft sat unsaved, the save writes
 * nothing and the button says so by being disabled.
 *
 * [ChargementViewModel] keeps the chargement list, the sessions and the commands.
 */
@HiltViewModel
class ChargementFormSessionViewModel @Inject constructor(
    private val draftRepository  : ChargementDraftRepository,
    private val productRepository: ProductRepository,
    private val savedState       : SavedStateHandle
) : ViewModel(), DraftAutosaveHost<ChargementDraftSnapshot> {

    // ── Form state ───────────────────────────────────────────────────────────

    private val _formCartItems = MutableStateFlow<List<ChargementCartItem>>(emptyList())
    val formCartItems: StateFlow<List<ChargementCartItem>> = _formCartItems

    private val _formNote = MutableStateFlow("")
    val formNote: StateFlow<String> = _formNote

    private val _formUserName = MutableStateFlow("")
    val formUserName: StateFlow<String> = _formUserName

    fun setFormCartItems(items: List<ChargementCartItem>) {
        _formCartItems.value = items
        pruneMissingProducts(items.map { it.product.id })
    }

    fun setFormNote(note: String) { _formNote.value = note }
    fun setFormUserName(name: String) { _formUserName.value = name }

    // ── Session state ────────────────────────────────────────────────────────

    private val _phase = MutableStateFlow(SessionPhase.UNDECIDED)
    val phase: StateFlow<SessionPhase> = _phase

    /**
     * Products a restored draft names that are no longer in the catalogue. Their lines still
     * render, from the name the draft stored, but the movement cannot be saved until they are
     * removed — dropping them silently would change a movement the user remembers composing.
     */
    private val _missingProductIds = MutableStateFlow<Set<Int>>(emptySet())
    val missingProductIds: StateFlow<Set<Int>> = _missingProductIds

    /**
     * Drops ids whose line has gone. Only [hydrate] can discover that a product is missing — that
     * needs a live catalogue lookup — so this never adds to the set. It only lets the block lift
     * when the user does the thing the line asks of them.
     */
    private fun pruneMissingProducts(presentIds: Collection<Int>) {
        val missing = _missingProductIds.value
        if (missing.isEmpty()) return
        _missingProductIds.value = missing intersect presentIds.toSet()
    }

    /**
     * Synchronous guard against double entry. During a navigation transition the outgoing and
     * incoming destinations are both composed, so [beginOrResumeSession] can be called twice before
     * the coroutine it launches has moved [_phase] off UNDECIDED.
     */
    private var entering = false

    /** The draft row this session owns, once it has one. Null until the form is first dirtied. */
    val draftId: Int? get() = savedState[KEY_DRAFT_ID]

    // ── Autosave ─────────────────────────────────────────────────────────────

    private val autosave = DraftAutosave(
        host    = this,
        scope   = viewModelScope,
        signals = listOf(_formCartItems, _formNote, _formUserName)
    )

    fun flushDraft() = autosave.flush()

    // ── DraftAutosaveHost ────────────────────────────────────────────────────

    /** Always null: create-only, so there is never a base to have diverged from. */
    override val baseFingerprint: String? get() = null

    override var draftRowId: Int?
        get()      = savedState[KEY_DRAFT_ID]
        set(value) { savedState[KEY_DRAFT_ID] = value }

    override fun isEdit(snapshot: ChargementDraftSnapshot) = false

    override fun isEmpty(snapshot: ChargementDraftSnapshot) = snapshot.isEmpty

    /** Never consulted — [DraftAutosave] only fingerprints an edit session, and this is never one. */
    override fun fingerprintOf(snapshot: ChargementDraftSnapshot) = ""

    override suspend fun upsertDraft(existing: Int?, snapshot: ChargementDraftSnapshot): Int =
        draftRepository.upsert(existing, snapshot)

    override suspend fun deleteDraft(id: Int) = draftRepository.delete(id)

    override fun snapshot() = ChargementDraftSnapshot(
        // Null: this session composes a listed Brouillon, never the Modifier card's private state.
        singleProductId = null,
        lines           = _formCartItems.value.map { it.toDraftLine() },
        note            = _formNote.value,
        userName        = _formUserName.value
    )

    // ── Session entry ────────────────────────────────────────────────────────

    /**
     * The single entry point the graph calls on arrival. Idempotent across recomposition and across
     * process death.
     *
     * As in the other three flows, nothing here decides whether to prompt. That happens upstream at
     * the FAB or the draft card, before this graph is entered.
     */
    fun beginOrResumeSession(draftId: Int?) {
        if (entering || _phase.value != SessionPhase.UNDECIDED) return
        entering = true

        if (savedState.get<Boolean>(KEY_STARTED) == true) {
            restoreAfterProcessDeath()
            return
        }
        savedState[KEY_STARTED] = true

        viewModelScope.launch {
            if (draftId != null) {
                draftRepository.getDraft(draftId)?.let { draft ->
                    savedState[KEY_DRAFT_ID] = draft.id
                    hydrate(draft)
                }
            } else {
                resetForm()
            }
            arm()
        }
    }

    /**
     * Comes back from a kill with the same draft row and the same content, and no dialog.
     *
     * If the row is gone — committed on another path, or deleted from the Brouillons screen — the
     * form falls back to a clean start rather than resurrecting a row that no longer exists.
     */
    private fun restoreAfterProcessDeath() {
        viewModelScope.launch {
            val id    = savedState.get<Int>(KEY_DRAFT_ID)
            val draft = id?.let { draftRepository.getDraft(it) }
            if (draft != null) {
                hydrate(draft)
            } else {
                savedState[KEY_DRAFT_ID] = null
                resetForm()
            }
            arm()
        }
    }

    // ── Hydration ────────────────────────────────────────────────────────────

    /**
     * Rebuilds lines from stored lines, taking the [Product] live from the catalogue and the target
     * from the draft.
     *
     * The live product is what makes the target mean anything on resume: `camion_stock` comes back
     * current, so the card recomputes the delta against the truck as it is now rather than as it
     * was when the draft was written.
     */
    private suspend fun hydrate(draft: ChargementDraft) {
        val products = productRepository.getProducts().associateBy { it.id }
        val missing  = mutableSetOf<Int>()

        _formCartItems.value = draft.lines.map { line ->
            val product = products[line.product_id] ?: run {
                missing += line.product_id
                placeholderProduct(line)
            }
            ChargementCartItem(product = product, targetCamion = line.target_camion)
        }

        _missingProductIds.value = missing
        _formNote.value          = draft.note
        _formUserName.value      = draft.userName
    }

    /** Keeps a deleted product's line visible and named instead of dropping it. */
    private fun placeholderProduct(line: ChargementDraftLine) = Product(
        id             = line.product_id,
        name           = line.product_name,
        barcode        = null,
        selling_price  = 0.0,
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

    private fun ChargementCartItem.toDraftLine() = ChargementDraftLine(
        product_id    = product.id,
        product_name  = product.name,
        unit_type     = product.unit_type,
        target_camion = targetCamion
    )

    private fun resetForm() {
        _formCartItems.value     = emptyList()
        _formNote.value          = ""
        _formUserName.value      = ""
        _missingProductIds.value = emptySet()
    }

    private fun arm() {
        autosave.arm()
        _phase.value = SessionPhase.READY
    }

    /**
     * Called after the movement commits. The draft row is already gone — deleted inside the same
     * transaction — so this only clears the session's memory of it.
     */
    fun onCommitted() {
        autosave.disarm()
        savedState[KEY_DRAFT_ID] = null
    }

    private companion object {
        const val KEY_STARTED  = "ch_session_started"
        const val KEY_DRAFT_ID = "ch_session_draft_id"
    }
}
