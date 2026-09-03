package com.distrigo.app.ui.ventes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.Vente
import com.distrigo.app.data.model.VenteDraft
import com.distrigo.app.data.model.VenteDraftLine
import com.distrigo.app.data.model.VenteDraftSnapshot
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.VenteDraftRepository
import com.distrigo.app.data.repository.VenteFingerprint
import com.distrigo.app.ui.common.DraftAutosave
import com.distrigo.app.ui.common.DraftAutosaveHost
import com.distrigo.app.ui.common.SessionPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns one pass through the Dépôt Vente form: the form's own state, whether this pass is new /
 * resumed / an edit, and the autosave that keeps a Brouillon behind it.
 *
 * ### Why this exists rather than living on [VenteViewModel]
 *
 * It is obtained with `hiltViewModel(navController.getBackStackEntry(graphRoute))`, so it is scoped
 * to the vente form graph's back stack entry, and its [SavedStateHandle] is that entry's. That
 * scope is the whole design, exactly as it is for Achats:
 *
 *  - **Process death** — Navigation saves and restores the entry, so the handle comes back saying
 *    a session was already started. The form is restored from its draft, silently, with no dialog.
 *  - **The user leaves the graph** — the entry is popped and the handle is destroyed with it. The
 *    next entry is a fresh session, so the caller gets to ask "Reprendre ou recommencer ?".
 *
 * This is what retires the `rememberSaveable("initialized")` flag the graph used to carry in two
 * places (once on the client step, once again on the products step for the `skipClientStep` entry).
 * That flag survived process death while the `VenteViewModel` it described did not, so after a kill
 * it claimed setup was done against a form that had been reset. A flag that cannot outlive its
 * state cannot lie about it — and there is now one copy of the entry logic instead of two.
 *
 * [VenteViewModel] keeps ventes, selectedVente, the create/update/deliver/delete commands and the
 * list filters, and keeps its existing tab-wide scoping in all three hosts.
 */
@HiltViewModel
class VenteFormSessionViewModel @Inject constructor(
    private val draftRepository  : VenteDraftRepository,
    private val productRepository: ProductRepository,
    private val savedState       : SavedStateHandle
) : ViewModel(), DraftAutosaveHost<VenteDraftSnapshot> {

    // ── Form state (moved off VenteViewModel) ────────────────────────────────

    private val _formClient = MutableStateFlow<Client?>(null)
    val formClient: StateFlow<Client?> = _formClient

    private val _formCartItems = MutableStateFlow<List<VenteCartItem>>(emptyList())
    val formCartItems: StateFlow<List<VenteCartItem>> = _formCartItems

    private val _formNote = MutableStateFlow("")
    val formNote: StateFlow<String> = _formNote

    private val _formUserName = MutableStateFlow("")
    val formUserName: StateFlow<String> = _formUserName

    private val _formMontantPaye = MutableStateFlow("")
    val formMontantPaye: StateFlow<String> = _formMontantPaye

    fun setFormClient(client: Client?) { _formClient.value = client }
    fun setFormCartItems(items: List<VenteCartItem>) { _formCartItems.value = items }
    fun setFormNote(note: String) { _formNote.value = note }
    fun setFormUserName(name: String) { _formUserName.value = name }
    fun setFormMontantPaye(value: String) { _formMontantPaye.value = value }

    // ── Session state ────────────────────────────────────────────────────────

    private val _phase = MutableStateFlow(SessionPhase.UNDECIDED)
    val phase: StateFlow<SessionPhase> = _phase

    /**
     * Products referenced by a restored draft that no longer exist in the catalogue. Their cart
     * lines still render — from the name stored on the draft — but validation is blocked until they
     * are removed, rather than dropping them silently and changing a total the user remembers.
     *
     * Blocking here also turns what would otherwise be an opaque failure into a fixable message:
     * `ProductRepository.createVente`/`updateVente` throw "Produit introuvable" at save time.
     */
    private val _missingProductIds = MutableStateFlow<Set<Int>>(emptySet())
    val missingProductIds: StateFlow<Set<Int>> = _missingProductIds

    /**
     * The committed vente's `source` ("depot" / "camion") in edit mode, null for a new vente.
     *
     * Published because the products step re-syncs every cart line against the live catalogue as
     * stock moves elsewhere, and each re-sync has to re-apply the line's own reservation the same
     * way [cartItem] does — which depends on whether the sale came off the depot or the truck.
     */
    private val _editSource = MutableStateFlow<String?>(null)
    val editSource: StateFlow<String?> = _editSource

    /**
     * Synchronous guard against double entry. During a navigation transition the outgoing and
     * incoming destinations are both composed, so [beginOrResumeSession] can be called twice before
     * the coroutine it launches has moved [_phase] off UNDECIDED.
     */
    private var entering = false

    /** The draft row this session owns, once it has one. Null until the form is first dirtied. */
    val draftId: Int? get() = savedState[KEY_DRAFT_ID]

    private val sourceVenteId: Int? get() = savedState[KEY_SOURCE_VENTE]

    // ── Autosave ─────────────────────────────────────────────────────────────

    private val autosave = DraftAutosave(
        host    = this,
        scope   = viewModelScope,
        signals = listOf(_formClient, _formCartItems, _formNote, _formUserName, _formMontantPaye)
    )

    /** Writes the current form now rather than waiting out the debounce — see [DraftAutosave.flush]. */
    fun flushDraft() = autosave.flush()

    // ── DraftAutosaveHost ────────────────────────────────────────────────────

    override val baseFingerprint: String? get() = savedState[KEY_BASE_FP]

    override var draftRowId: Int?
        get()      = savedState[KEY_DRAFT_ID]
        set(value) { savedState[KEY_DRAFT_ID] = value }

    override fun isEdit(snapshot: VenteDraftSnapshot)  = snapshot.sourceVenteId != null
    override fun isEmpty(snapshot: VenteDraftSnapshot) = snapshot.isEmpty
    override fun fingerprintOf(snapshot: VenteDraftSnapshot) = VenteFingerprint.of(snapshot)

    override suspend fun upsertDraft(existing: Int?, snapshot: VenteDraftSnapshot): Int =
        draftRepository.upsert(existing, snapshot)

    override suspend fun deleteDraft(id: Int) = draftRepository.delete(id)

    override fun snapshot() = VenteDraftSnapshot(
        clientId        = _formClient.value?.id,
        clientName      = _formClient.value?.name,
        lines           = _formCartItems.value.map { it.toDraftLine() },
        note            = _formNote.value,
        montantPaye     = _formMontantPaye.value,
        userName        = _formUserName.value,
        lastStep        = savedState[KEY_LAST_STEP] ?: "",
        sourceVenteId   = sourceVenteId,
        baseFingerprint = baseFingerprint
    )

    fun setLastStep(route: String) { savedState[KEY_LAST_STEP] = route }

    // ── Session entry ────────────────────────────────────────────────────────

    /**
     * The single entry point the graph calls on arrival. Idempotent across recomposition, and
     * across process death — which is the point.
     *
     * Note what is *not* here: any decision about whether to prompt. That happens upstream, at the
     * FAB or the draft card, before this graph is ever entered. A process-death return re-enters the
     * graph without passing through those, so it cannot prompt — not by choosing not to, but because
     * there is no code here that could.
     *
     * @param venteId  the vente being edited, or null for a new vente
     * @param clientId a client chosen before the form opened ("Nouvelle facture" from a client
     *                 page), or null. Ignored in edit mode, where the vente names its own client.
     * @param draftId  a Brouillon to resume, or null to start clean
     */
    fun beginOrResumeSession(venteId: Int?, clientId: Int?, draftId: Int?) {
        if (entering || _phase.value != SessionPhase.UNDECIDED) return
        entering = true

        if (savedState.get<Boolean>(KEY_STARTED) == true) {
            restoreAfterProcessDeath(venteId, clientId)
            return
        }
        savedState[KEY_STARTED] = true

        viewModelScope.launch {
            when {
                draftId != null -> {
                    val draft = draftRepository.getDraft(draftId)
                    if (draft != null) {
                        savedState[KEY_DRAFT_ID]     = draft.id
                        savedState[KEY_SOURCE_VENTE] = draft.sourceVenteId
                        savedState[KEY_BASE_FP]      = draft.baseFingerprint
                        hydrate(draft)
                    }
                    arm()
                }

                venteId != null -> {
                    savedState[KEY_SOURCE_VENTE] = venteId
                    prefillEditFromVente(venteId)
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
     * Loads a committed vente into the form, then records the fingerprint of what it loaded and
     * arms autosave — strictly in that order, because an empty form measured against a non-empty
     * base reads as a wholesale edit and would be persisted as one.
     *
     * This is done here rather than from the graph on purpose. Process death restores the back
     * stack to whichever step the user was on, so the first destination may never compose; a
     * prefill that lived in one destination would leave the others looking at an empty form. The
     * session is the only thing guaranteed to be present on every path into the graph — and it is
     * what lets the client step and the products step stop carrying a copy of this each.
     *
     * The base fingerprint is captured once per session and never recaptured — it is what the
     * session started from, and it must not drift underneath the comparison.
     */
    private suspend fun prefillEditFromVente(venteId: Int) {
        _phase.value = SessionPhase.PREFILLING_EDIT
        val vente = runCatching { productRepository.getVente(venteId) }.getOrNull()
        if (vente == null) { arm(); return }

        _editSource.value      = vente.source
        _formNote.value        = vente.note.orEmpty()
        _formMontantPaye.value = VenteDraftRepository.montantPayeText(vente.montant_paye ?: 0.0)
        _formClient.value      = productRepository.getClients().find { it.id == vente.client_id }
        // Not restored, because a committed vente has nowhere to hold it: "Effectué par" is written
        // onto the stock movements, not onto the ventes row. VenteDraftRepository builds the other
        // side of every fingerprint comparison with the same empty value, so the two agree.
        _formUserName.value    = ""

        val products = productRepository.getProducts().associateBy { it.id }
        _formCartItems.value = vente.items.orEmpty().map { item ->
            cartItem(
                productId    = item.product_id,
                productName  = item.product_name,
                unitType     = item.unit_type,
                quantity     = item.quantity,
                unitPrice    = item.unit_price,
                reservedQty  = item.quantity,
                source       = vente.source,
                catalogue    = products,
                missing      = null
            )
        }

        if (baseFingerprint == null) {
            savedState[KEY_BASE_FP] = draftRepository.baseFingerprintFor(venteId)
        }
        arm()
    }

    /**
     * The silent path. The back stack — and this handle — came back, so a session was already in
     * progress: restore it from its draft and carry on as if nothing happened. No dialog is
     * reachable from here.
     *
     * The base cannot have moved while the process was dead: this app is the only writer to
     * `ventes`, and it was not running.
     */
    private fun restoreAfterProcessDeath(venteId: Int?, clientId: Int?) {
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
            // vente (the base is already recorded and will not be recaptured); a new vente simply
            // starts clean again, keeping any client it was opened with.
            when {
                venteId != null  -> prefillEditFromVente(venteId)
                clientId != null -> { preselectClient(clientId); arm() }
                else             -> arm()
            }
        }
    }

    private suspend fun preselectClient(clientId: Int) {
        _formClient.value = productRepository.getClients().find { it.id == clientId }
    }

    // ── Hydration ────────────────────────────────────────────────────────────

    /**
     * Rebuilds cart items from stored lines, taking the [Product] live from the catalogue and the
     * user-entered values from the draft. Anything the user typed — the negotiated unit price above
     * all — comes back exactly as they left it; anything belonging to the catalogue comes back
     * current.
     *
     * For an edit draft, each line's reservation is re-read from the **committed vente**, not from
     * the draft: the draft's quantity is what the user has changed it to, while the reservation is
     * this sale's own stock deduction that has to be re-added to the live snapshot for the form to
     * show what was actually available. Persisting it would have frozen a number that the vente may
     * have moved underneath in the meantime.
     */
    private suspend fun hydrate(draft: VenteDraft) {
        val products = productRepository.getProducts().associateBy { it.id }
        val missing  = mutableSetOf<Int>()

        // Resolved here, not by the first destination: a process-death restore comes back on
        // whichever step the user was on, and a Cart or Validation step with a null client would
        // refuse to save. Ids in, live objects out — the same rule the cart lines follow.
        _formClient.value = draft.clientId?.let { id ->
            productRepository.getClients().find { it.id == id }
        }

        val committed = draft.sourceVenteId
            ?.let { runCatching { productRepository.getVente(it) }.getOrNull() }
        val reservedByProduct = committed?.items.orEmpty().associate { it.product_id to it.quantity }
        val source = committed?.source ?: SOURCE_DEPOT
        _editSource.value = committed?.source

        _formCartItems.value = draft.lines.map { line ->
            cartItem(
                productId   = line.product_id,
                productName = line.product_name,
                unitType    = line.unit_type,
                quantity    = line.quantity,
                unitPrice   = line.unit_price,
                reservedQty = reservedByProduct[line.product_id],
                source      = source,
                catalogue   = products,
                missing     = missing
            )
        }
        _missingProductIds.value = missing
        _formNote.value        = draft.note
        _formMontantPaye.value = draft.montantPaye
        _formUserName.value    = draft.userName
    }

    /**
     * One cart line, with the edit-mode stock baseline already applied.
     *
     * `product.stock` already reflects this sale's own deduction, so the raw live snapshot
     * understates what was available when the sale was made. [reservedQty] is re-added before the
     * product is handed to the form — in local UI state only, never persisted — mirroring
     * `ProductRepository.updateVente`'s own reversal math. A camion sale moves `camion_stock` too.
     */
    private fun cartItem(
        productId  : Int,
        productName: String,
        unitType   : String,
        quantity   : Double,
        unitPrice  : Double,
        reservedQty: Double?,
        source     : String,
        catalogue  : Map<Int, Product>,
        missing    : MutableSet<Int>?
    ): VenteCartItem {
        val live = catalogue[productId] ?: run {
            missing?.add(productId)
            placeholderProduct(productId, productName, unitType, unitPrice)
        }
        val baseline = reservedQty?.let { reserved ->
            if (source == SOURCE_CAMION)
                live.copy(stock = live.stock + reserved, camion_stock = live.camion_stock + reserved)
            else
                live.copy(stock = live.stock + reserved)
        } ?: live

        return VenteCartItem(
            product             = baseline,
            quantity            = quantity,
            unitPrice           = unitPrice,
            originalReservedQty = reservedQty
        )
    }

    /** Keeps a deleted product's line visible and named instead of dropping it. */
    private fun placeholderProduct(
        productId  : Int,
        productName: String,
        unitType   : String,
        unitPrice  : Double
    ) = Product(
        id             = productId,
        name           = productName,
        barcode        = null,
        selling_price  = unitPrice,
        purchase_price = 0.0,
        stock          = 0.0,
        min_stock      = 0,
        unit_type      = unitType,
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

    private fun VenteCartItem.toDraftLine() = VenteDraftLine(
        product_id   = product.id,
        product_name = product.name,
        unit_type    = product.unit_type,
        quantity     = quantity,
        unit_price   = unitPrice
    )

    private fun resetForm() {
        _formClient.value        = null
        _formCartItems.value     = emptyList()
        _formNote.value          = ""
        _formUserName.value      = ""
        _formMontantPaye.value   = ""
        _missingProductIds.value = emptySet()
        _editSource.value        = null
    }

    private fun arm() {
        autosave.arm()
        _phase.value = SessionPhase.READY
    }

    /**
     * Called after the vente commits. The draft row is already gone — deleted by the graph in the
     * same success path — so this only clears the session's memory of it, keeping the handle from
     * pointing at a row that no longer exists.
     */
    fun onCommitted() {
        autosave.disarm()
        savedState[KEY_DRAFT_ID] = null
    }

    private companion object {
        const val SOURCE_DEPOT  = "depot"
        const val SOURCE_CAMION = "camion"

        const val KEY_STARTED      = "draft_session_started"
        const val KEY_DRAFT_ID     = "draft_id"
        const val KEY_SOURCE_VENTE = "draft_source_vente_id"
        const val KEY_BASE_FP      = "draft_base_fingerprint"
        const val KEY_LAST_STEP    = "draft_last_step"
    }
}
