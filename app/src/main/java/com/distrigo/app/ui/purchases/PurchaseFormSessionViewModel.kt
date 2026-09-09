package com.distrigo.app.ui.purchases

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.DraftLine
import com.distrigo.app.data.model.DraftSnapshot
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.PurchaseDraft
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.PurchaseDraftRepository
import com.distrigo.app.data.repository.PurchaseFingerprint
import com.distrigo.app.ui.common.DraftAutosave
import com.distrigo.app.ui.common.DraftAutosaveHost
import com.distrigo.app.ui.common.SessionPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns one pass through the Achats form: the form's own state, whether this pass is new / resumed
 * / an edit, and the autosave that keeps a Brouillon behind it.
 *
 * ### Why this exists rather than living on [PurchaseViewModel]
 *
 * It is obtained with `hiltViewModel(navController.getBackStackEntry(graphRoute))`, so it is scoped
 * to the purchase form graph's back stack entry, and its [SavedStateHandle] is that entry's. That
 * scope is the whole design:
 *
 *  - **Process death** — Navigation saves and restores the entry, so the handle comes back saying
 *    a session was already started. The form is restored from its draft, silently, with no dialog.
 *  - **The user leaves the graph** — the entry is popped and the handle is destroyed with it. The
 *    next entry is a fresh session, so the caller gets to ask "Reprendre ou recommencer ?".
 *
 * The flag and the state it describes now live in the same object, destroyed by the same event.
 * That is what retires the `rememberSaveable("initialized")` bug rather than patching it:
 * `rememberSaveable` survived process death while the ViewModel it described did not, so after a
 * kill the flag claimed setup was done against an empty ViewModel — and in edit mode the graph's
 * "wait for the order" gate then span forever. A flag that cannot outlive its state cannot lie
 * about it.
 *
 * `PurchaseViewModel` keeps orders, selectedOrder, the create/update/receive commands and the list
 * filters, and keeps its existing scoping in both hosts.
 */
@HiltViewModel
class PurchaseFormSessionViewModel @Inject constructor(
    private val draftRepository  : PurchaseDraftRepository,
    private val productRepository: ProductRepository,
    private val savedState       : SavedStateHandle
) : ViewModel(), DraftAutosaveHost<DraftSnapshot> {

    // ── Form state (moved off PurchaseViewModel) ─────────────────────────────

    private val _formSupplier = MutableStateFlow<Supplier?>(null)
    val formSupplier: StateFlow<Supplier?> = _formSupplier

    private val _formCartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val formCartItems: StateFlow<List<CartItem>> = _formCartItems

    private val _formNote = MutableStateFlow("")
    val formNote: StateFlow<String> = _formNote

    private val _formMontantPaye = MutableStateFlow("")
    val formMontantPaye: StateFlow<String> = _formMontantPaye

    fun setFormSupplier(supplier: Supplier?) { _formSupplier.value = supplier }
    fun setFormCartItems(items: List<CartItem>) {
        _formCartItems.value = items
        pruneMissingProducts(items.map { it.product.id })
    }
    fun setFormNote(note: String) { _formNote.value = note }
    fun setFormMontantPaye(value: String) { _formMontantPaye.value = value }

    // ── Session state ────────────────────────────────────────────────────────

    private val _phase = MutableStateFlow(SessionPhase.UNDECIDED)
    val phase: StateFlow<SessionPhase> = _phase

    /**
     * Products referenced by a restored draft that no longer exist in the catalogue. Their cart
     * lines still render — from the name stored on the draft — but validation is blocked until
     * they are removed, rather than dropping them silently and changing a total the user remembers.
     *
     * Blocking here also turns what would otherwise be an opaque failure into a fixable message:
     * `ProductRepository.updatePurchaseOrder` throws "Produit introuvable" at save time.
     */
    private val _missingProductIds = MutableStateFlow<Set<Int>>(emptySet())
    val missingProductIds: StateFlow<Set<Int>> = _missingProductIds

    /**
     * Drops ids whose cart line has gone.
     *
     * Only [hydrate] can *discover* that a product is missing — that needs a live catalogue lookup
     * — so this never adds to the set. It only lets the block lift when the user does the very
     * thing the cart line asks of them.
     *
     * Without it the block outlived its cause: "retirez cette ligne pour continuer" removed the
     * line, the id stayed behind, and the form stayed un-saveable until the user backed out and
     * resumed the draft — a way out that nothing on screen suggested.
     */
    private fun pruneMissingProducts(presentIds: Collection<Int>) {
        val missing = _missingProductIds.value
        if (missing.isEmpty()) return
        _missingProductIds.value = missing intersect presentIds.toSet()
    }

    /**
     * Synchronous guard against double entry. During a navigation transition the outgoing and
     * incoming destinations are both composed, so [beginOrResumeSession] can be called twice
     * before the coroutine it launches has moved [_phase] off UNDECIDED.
     */
    private var entering = false

    /** The draft row this session owns, once it has one. Null until the form is first dirtied. */
    val draftId: Int? get() = savedState[KEY_DRAFT_ID]

    private val sourceOrderId : Int? get() = savedState[KEY_SOURCE_ORDER]

    // ── Autosave ─────────────────────────────────────────────────────────────

    private val autosave = DraftAutosave(
        host    = this,
        scope   = viewModelScope,
        signals = listOf(_formSupplier, _formCartItems, _formNote, _formMontantPaye)
    )

    /** Writes the current form now rather than waiting out the debounce — see [DraftAutosave.flush]. */
    fun flushDraft() = autosave.flush()

    // ── DraftAutosaveHost ────────────────────────────────────────────────────

    override val baseFingerprint: String? get() = savedState[KEY_BASE_FP]

    override var draftRowId: Int?
        get()      = savedState[KEY_DRAFT_ID]
        set(value) { savedState[KEY_DRAFT_ID] = value }

    override fun isEdit(snapshot: DraftSnapshot)   = snapshot.sourceOrderId != null
    override fun isEmpty(snapshot: DraftSnapshot)  = snapshot.isEmpty
    override fun fingerprintOf(snapshot: DraftSnapshot) = PurchaseFingerprint.of(snapshot)

    override suspend fun upsertDraft(existing: Int?, snapshot: DraftSnapshot): Int =
        draftRepository.upsert(existing, snapshot)

    override suspend fun deleteDraft(id: Int) = draftRepository.delete(id)

    override fun snapshot() = DraftSnapshot(
        supplierId      = _formSupplier.value?.id,
        supplierName    = _formSupplier.value?.name,
        lines           = _formCartItems.value.map { it.toDraftLine() },
        note            = _formNote.value,
        montantPaye     = _formMontantPaye.value,
        lastStep        = savedState[KEY_LAST_STEP] ?: "",
        sourceOrderId   = sourceOrderId,
        baseFingerprint = baseFingerprint
    )

    fun setLastStep(route: String) { savedState[KEY_LAST_STEP] = route }

    // ── Session entry ────────────────────────────────────────────────────────

    /**
     * The single entry point the graph calls on arrival. Idempotent across recomposition, and
     * across process death — which is the point.
     *
     * Note what is *not* here: any decision about whether to prompt. That happens upstream, at the
     * FAB or the draft card, before this graph is ever entered. A process-death return re-enters
     * the graph without passing through those, so it cannot prompt — not by choosing not to, but
     * because there is no code here that could.
     *
     * @param orderId    the bon being edited, or null for a new purchase
     * @param supplierId a supplier chosen before the form opened ("Nouvel achat" from a supplier
     *                   page), or null. Ignored in edit mode, where the bon names its own supplier.
     * @param draftId    a Brouillon to resume, or null to start clean
     */
    fun beginOrResumeSession(orderId: Int?, supplierId: Int?, draftId: Int?) {
        if (entering || _phase.value != SessionPhase.UNDECIDED) return
        entering = true

        if (savedState.get<Boolean>(KEY_STARTED) == true) {
            restoreAfterProcessDeath(orderId, supplierId)
            return
        }
        savedState[KEY_STARTED] = true

        viewModelScope.launch {
            when {
                draftId != null -> {
                    val draft = draftRepository.getDraft(draftId)
                    if (draft != null) {
                        savedState[KEY_DRAFT_ID]     = draft.id
                        savedState[KEY_SOURCE_ORDER] = draft.sourceOrderId
                        savedState[KEY_BASE_FP]      = draft.baseFingerprint
                        hydrate(draft)
                    }
                    arm()
                }

                orderId != null -> {
                    savedState[KEY_SOURCE_ORDER] = orderId
                    prefillEditFromOrder(orderId)
                }

                else -> {
                    resetForm()
                    // Before arming, deliberately. Arriving from a supplier page is navigation, not
                    // data entry: on its own it must not spawn a Brouillon, or every stray tap on
                    // "Nouvel achat" leaves one behind. Nothing is lost — reopening gives the
                    // identical starting state. The first thing the user actually enters arms the
                    // draft. Dépôt Vente's preselected client works the same way.
                    if (supplierId != null) preselectSupplier(supplierId)
                    arm()
                }
            }
        }
    }

    /**
     * Loads a committed bon into the form, then records the fingerprint of what it loaded and arms
     * autosave — strictly in that order, because an empty form measured against a non-empty base
     * reads as a wholesale edit and would be persisted as one.
     *
     * This is done here rather than from the graph on purpose. Process death restores the back
     * stack to whichever step the user was on, so the first destination may never compose; a
     * prefill that lived in one destination would leave the others looking at an empty form. The
     * session is the only thing guaranteed to be present on every path into the graph.
     *
     * The base fingerprint is captured once per session and never recaptured — it is what the
     * session started from, and it must not drift underneath the comparison.
     */
    private suspend fun prefillEditFromOrder(orderId: Int) {
        _phase.value = SessionPhase.PREFILLING_EDIT
        val order = runCatching { productRepository.getPurchaseOrder(orderId) }.getOrNull()
        if (order == null) { arm(); return }

        _formNote.value        = order.note.orEmpty()
        _formMontantPaye.value = PurchaseDraftRepository.montantPayeText(order.montant_paye ?: 0.0)
        _formSupplier.value    = productRepository.getSuppliers().find { it.id == order.supplier_id }

        // Every field is carried across as recorded. Anything defaulted here is written straight
        // back over the bon on the next save, so "1 colis × 25 pièces" must not become "25 × 1"
        // and an expiry date must not quietly become none.
        //
        // PurchaseDraftRepository.prefilledLinesFor / .montantPayeText build the other side of
        // every fingerprint comparison from the same fields. The two move together: if this
        // prefill ever stops being a verbatim copy, that mirror has to stop too, or an untouched
        // edit measures as modified and spawns a phantom draft on sight.
        val products = productRepository.getProducts().associateBy { it.id }
        _formCartItems.value = order.items.orEmpty().map { item ->
            CartItem(
                product       = products[item.product_id] ?: placeholderProduct(
                    DraftLine(item.product_id, item.product_name, item.quantity, item.unit_cost,
                              item.nb_colis, item.unite_par_colis, item.has_expiry, item.expiry_date)
                ),
                quantity      = item.quantity,
                unitCost      = item.unit_cost,
                nbColis       = item.nb_colis,
                uniteParColis = item.unite_par_colis,
                hasExpiry     = item.has_expiry,
                expiryDate    = item.expiry_date
            )
        }

        if (baseFingerprint == null) {
            savedState[KEY_BASE_FP] = draftRepository.baseFingerprintFor(orderId)
        }
        arm()
    }

    /**
     * The silent path. The back stack — and this handle — came back, so a session was already in
     * progress: restore it from its draft and carry on as if nothing happened. No dialog is
     * reachable from here.
     *
     * The base cannot have moved while the process was dead: this app is the only writer to
     * `purchase_orders`, and it was not running.
     */
    private fun restoreAfterProcessDeath(orderId: Int?, supplierId: Int?) {
        viewModelScope.launch {
            val id = draftId
            if (id != null) {
                val draft = draftRepository.getDraft(id)
                if (draft != null) {
                    hydrate(draft)
                    arm()
                    return@launch
                }
                savedState[KEY_DRAFT_ID] = null
            }
            // Nothing was ever dirtied, so there is no draft to restore. An edit reloads from its
            // bon (the base is already recorded and will not be recaptured); a new purchase simply
            // starts clean again, keeping any supplier it was opened with.
            when {
                orderId != null    -> prefillEditFromOrder(orderId)
                supplierId != null -> { preselectSupplier(supplierId); arm() }
                else               -> arm()
            }
        }
    }

    private suspend fun preselectSupplier(supplierId: Int) {
        _formSupplier.value = productRepository.getSuppliers().find { it.id == supplierId }
    }

    // ── Hydration ────────────────────────────────────────────────────────────

    /**
     * Rebuilds cart items from stored lines, taking the [Product] live from the catalogue and the
     * six user-entered values from the draft. Anything the user typed — the negotiated unit cost
     * above all — comes back exactly as they left it; anything belonging to the catalogue comes
     * back current.
     */
    private suspend fun hydrate(draft: PurchaseDraft) {
        val products = productRepository.getProducts().associateBy { it.id }
        val missing  = mutableSetOf<Int>()

        // Resolved here, not by the first destination: a process-death restore comes back on
        // whichever step the user was on, and a Cart or Validation step with a null supplier would
        // refuse to save. Ids in, live objects out — the same rule the cart lines follow.
        _formSupplier.value = draft.supplierId?.let { id ->
            productRepository.getSuppliers().find { it.id == id }
        }

        _formCartItems.value = draft.lines.map { line ->
            val product = products[line.product_id] ?: run {
                missing += line.product_id
                placeholderProduct(line)
            }
            CartItem(
                product       = product,
                quantity      = line.quantity,
                unitCost      = line.unit_cost,
                nbColis       = line.nb_colis,
                uniteParColis = line.unite_par_colis,
                hasExpiry     = line.has_expiry,
                expiryDate    = line.expiry_date
            )
        }
        _missingProductIds.value = missing
        _formNote.value        = draft.note
        _formMontantPaye.value = draft.montantPaye
    }

    /** Keeps a deleted product's line visible and named instead of dropping it. */
    private fun placeholderProduct(line: DraftLine) = Product(
        id             = line.product_id,
        name           = line.product_name,
        barcode        = null,
        selling_price  = 0.0,
        purchase_price = line.unit_cost,
        stock          = 0.0,
        min_stock      = 0,
        unit_type      = "unité",
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

    private fun CartItem.toDraftLine() = DraftLine(
        product_id      = product.id,
        product_name    = product.name,
        quantity        = quantity,
        unit_cost       = unitCost,
        nb_colis        = nbColis,
        unite_par_colis = uniteParColis,
        has_expiry      = hasExpiry,
        expiry_date     = expiryDate
    )

    private fun resetForm() {
        _formSupplier.value      = null
        _formCartItems.value     = emptyList()
        _formNote.value          = ""
        _formMontantPaye.value   = ""
        _missingProductIds.value = emptySet()
    }

    private fun arm() {
        autosave.arm()
        _phase.value = SessionPhase.READY
    }

    /**
     * Called after the bon commits. The draft row is already gone — deleted inside the same
     * transaction — so this only clears the session's memory of it, keeping the handle from
     * pointing at a row that no longer exists.
     */
    fun onCommitted() {
        autosave.disarm()
        savedState[KEY_DRAFT_ID] = null
    }

    private companion object {
        const val KEY_STARTED          = "draft_session_started"
        const val KEY_DRAFT_ID         = "draft_id"
        const val KEY_SOURCE_ORDER     = "draft_source_order_id"
        const val KEY_BASE_FP          = "draft_base_fingerprint"
        const val KEY_LAST_STEP        = "draft_last_step"
    }
}
